package com.bookeatinglion.order.order.service;

import com.bookeatinglion.order.client.CatalogClient;
import com.bookeatinglion.order.client.CatalogClient.BookDetailEnvelope;
import com.bookeatinglion.order.coupon.domain.MemberCoupon;
import com.bookeatinglion.order.coupon.repository.MemberCouponRepository;
import com.bookeatinglion.order.inventory.domain.Inventory;
import com.bookeatinglion.order.inventory.repository.InventoryRepository;
import com.bookeatinglion.order.lock.InventoryLockExecutor;
import com.bookeatinglion.order.order.domain.Order;
import com.bookeatinglion.order.order.domain.OrderItem;
import com.bookeatinglion.order.order.dto.CreateOrderRequest;
import com.bookeatinglion.order.order.dto.OrderItemRequest;
import com.bookeatinglion.order.order.dto.OrderResponse;
import com.bookeatinglion.order.order.dto.Recipient;
import com.bookeatinglion.order.order.exception.BookPriceUnavailableException;
import com.bookeatinglion.order.order.exception.InvalidCouponException;
import com.bookeatinglion.order.order.exception.InvalidOrderRequestException;
import com.bookeatinglion.order.order.exception.OrderCouponNotFoundException;
import com.bookeatinglion.order.order.exception.OrderNotFoundException;
import com.bookeatinglion.order.order.exception.OutOfStockException;
import com.bookeatinglion.order.order.exception.UnauthorizedCouponAccessException;
import com.bookeatinglion.order.order.exception.UnauthorizedOrderAccessException;
import com.bookeatinglion.order.order.repository.OrderItemRepository;
import com.bookeatinglion.order.order.repository.OrderRepository;
import com.bookeatinglion.order.payment.domain.Payment;
import com.bookeatinglion.order.payment.domain.PaymentMethod;
import com.bookeatinglion.order.payment.repository.PaymentRepository;
import com.bookeatinglion.order.payment.service.PaymentService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 차감/복구는 항상 InventoryLockExecutor 를 거친다 — bookId 오름차순 락으로 데드락을 막고,
 * 같은 책을 동시에 사는 두 주문 또는 구매와 취소가 겹치는 경우를 순차화한다.
 *
 * 요청의 items 는 같은 bookId 가 중복될 수 있다고 가정하고 먼저 수량을 합산한다 — 합산하지 않으면
 * "책 A 재고 4, 주문에 A 2개+A 3개" 같은 요청에서 항목별 재고 체크가 각각 통과해버려 실제로는
 * 5개를 차감하는 오버셀링 버그가 난다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryRepository inventoryRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final PaymentRepository paymentRepository;
    private final CatalogClient catalogClient;
    private final InventoryLockExecutor inventoryLockExecutor;
    private final PaymentService paymentService;

    @Transactional
    public OrderResponse createOrder(Long memberId, CreateOrderRequest request) {
        if (request.paymentMethod() == PaymentMethod.CARD && request.cardId() == null) {
            throw new InvalidOrderRequestException("paymentMethod=CARD 이면 cardId 가 필수입니다.");
        }

        Map<Long, Integer> quantityByBookId = new LinkedHashMap<>();
        for (OrderItemRequest item : request.items()) {
            quantityByBookId.merge(item.bookId(), item.quantity(), Integer::sum);
        }
        List<Long> bookIds = List.copyOf(quantityByBookId.keySet());

        return inventoryLockExecutor.executeWithLock(bookIds, () -> {
            Map<Long, Inventory> inventories = inventoryRepository.findByBookIdIn(bookIds).stream()
                    .collect(Collectors.toMap(Inventory::getBookId, Function.identity()));

            for (Map.Entry<Long, Integer> entry : quantityByBookId.entrySet()) {
                Inventory inventory = inventories.get(entry.getKey());
                if (inventory == null || inventory.getStock() < entry.getValue()) {
                    throw new OutOfStockException(entry.getKey());
                }
            }

            List<OrderItemSnapshot> snapshots = new ArrayList<>();
            int subtotal = 0;
            for (Map.Entry<Long, Integer> entry : quantityByBookId.entrySet()) {
                Long bookId = entry.getKey();
                int quantity = entry.getValue();

                BookDetailEnvelope envelope = catalogClient.getBook(bookId);
                if (!envelope.success()) {
                    throw new BookPriceUnavailableException(bookId);
                }

                var book = envelope.data();
                snapshots.add(new OrderItemSnapshot(bookId, book.title(), quantity, book.price()));
                subtotal += book.price() * quantity;
            }

            MemberCoupon memberCoupon = validateAndGetCoupon(memberId, request.memberCouponId(), subtotal);
            int discount = memberCoupon == null ? 0 : memberCoupon.getCoupon().getDiscountAmount();
            int totalAmount = Math.max(0, subtotal - discount);

            Recipient recipient = request.recipient();
            Order order = orderRepository.save(new Order(
                    memberId,
                    recipient.name(),
                    recipient.phone(),
                    recipient.postalCode(),
                    recipient.address(),
                    totalAmount));

            List<OrderItem> items = snapshots.stream()
                    .map(s -> new OrderItem(order, s.bookId(), s.title(), s.quantity(), s.price()))
                    .toList();
            orderItemRepository.saveAll(items);

            Payment payment = paymentService.approve(order, request.paymentMethod(), request.cardId(), totalAmount);
            order.markPaid();

            if (memberCoupon != null) {
                memberCoupon.use(LocalDateTime.now(), order.getId());
            }

            for (Map.Entry<Long, Integer> entry : quantityByBookId.entrySet()) {
                inventories.get(entry.getKey()).deduct(entry.getValue());
            }

            return OrderResponse.of(order, items, payment);
        });
    }

    public OrderResponse getOrder(Long memberId, Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.isOwnedBy(memberId)) {
            throw new UnauthorizedOrderAccessException(orderId);
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        return OrderResponse.of(order, items, payment);
    }

    @Transactional
    public OrderResponse cancelOrder(Long memberId, Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.isOwnedBy(memberId)) {
            throw new UnauthorizedOrderAccessException(orderId);
        }
        // PAID 가 아니면 여기서 OrderCannotBeCancelledException 을 던진다.
        order.cancel();

        // PAID 였다면 결제가 반드시 존재한다(불변식) — 없으면 데이터 정합성 문제이지 사용자 오류가 아니다.
        Payment payment = paymentRepository
                .findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("PAID 주문에 결제 정보가 없습니다: " + orderId));
        // 실패하면 CardRestoreFailedException 이 여기까지의 변경(order.cancel())을 포함해 전부 롤백시킨다.
        paymentService.cancel(payment);

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        List<Long> bookIds = items.stream().map(OrderItem::getBookId).distinct().toList();

        inventoryLockExecutor.executeWithLock(bookIds, () -> {
            Map<Long, Inventory> inventories = inventoryRepository.findByBookIdIn(bookIds).stream()
                    .collect(Collectors.toMap(Inventory::getBookId, Function.identity()));
            for (OrderItem item : items) {
                inventories.get(item.getBookId()).restock(item.getQuantity());
            }
            return null;
        });

        memberCouponRepository.findByUsedOrderId(orderId).ifPresent(MemberCoupon::cancelUse);

        return OrderResponse.of(order, items, payment);
    }

    private MemberCoupon validateAndGetCoupon(Long memberId, Long memberCouponId, int subtotal) {
        if (memberCouponId == null) {
            return null;
        }

        MemberCoupon memberCoupon = memberCouponRepository
                .findById(memberCouponId)
                .orElseThrow(() -> new OrderCouponNotFoundException(memberCouponId));

        if (!memberCoupon.isOwnedBy(memberId)) {
            throw new UnauthorizedCouponAccessException(memberCouponId);
        }
        if (memberCoupon.isUsed()) {
            throw new InvalidCouponException("이미 사용한 쿠폰입니다: " + memberCouponId);
        }
        if (memberCoupon.getCoupon().isExpired(LocalDateTime.now())) {
            throw new InvalidCouponException("만료된 쿠폰입니다: " + memberCouponId);
        }
        if (subtotal < memberCoupon.getCoupon().getMinimumOrderAmount()) {
            throw new InvalidCouponException("최소 주문 금액 미달입니다: " + memberCouponId);
        }
        return memberCoupon;
    }

    private record OrderItemSnapshot(Long bookId, String title, int quantity, int price) {}
}
