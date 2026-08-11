package com.bookeatinglion.order.order.service;

import com.bookeatinglion.order.cart.repository.CartItemRepository;
import com.bookeatinglion.order.client.CatalogClient;
import com.bookeatinglion.order.client.CatalogClient.BookDetailEnvelope;
import com.bookeatinglion.order.coupon.domain.MemberCoupon;
import com.bookeatinglion.order.coupon.repository.MemberCouponRepository;
import com.bookeatinglion.order.inventory.domain.Inventory;
import com.bookeatinglion.order.inventory.repository.InventoryRepository;
import com.bookeatinglion.order.lock.InventoryLockExecutor;
import com.bookeatinglion.order.order.domain.Order;
import com.bookeatinglion.order.order.domain.OrderItem;
import com.bookeatinglion.order.order.domain.OrderStatus;
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
import com.bookeatinglion.order.order.exception.PaymentAlreadyProcessedException;
import com.bookeatinglion.order.order.exception.UnauthorizedCouponAccessException;
import com.bookeatinglion.order.order.exception.UnauthorizedOrderAccessException;
import com.bookeatinglion.order.order.repository.OrderItemRepository;
import com.bookeatinglion.order.order.repository.OrderRepository;
import com.bookeatinglion.order.payment.domain.Payment;
import com.bookeatinglion.order.payment.domain.PaymentMethod;
import com.bookeatinglion.order.payment.domain.PaymentStatus;
import com.bookeatinglion.order.payment.repository.PaymentRepository;
import com.bookeatinglion.order.payment.service.PaymentService;
import com.bookeatinglion.order.payment.service.PaymentService.KakaoReadyOutcome;
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
 * CARD 는 1단계다 — createOrder 안에서 결제·재고차감·쿠폰사용확정·장바구니비우기가 전부
 * 끝난다. KAKAOPAY 는 2단계다 — createOrder 는 카카오페이 ready 만 하고 PENDING_PAYMENT 로
 * 남기며, 재고는 아직 건드리지 않고 쿠폰도 아직 사용확정하지 않는다(Order.pendingMemberCouponId
 * 에 의도만 적어둔다). approveKakaoPay 가 실제 결제 확정 시점이다 — 재고를 카카오 승인 API
 * 호출 *전에* 재검증해서, 부족하면 카카오에 승인 요청 자체를 보내지 않는다(환불 로직 불필요).
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
    private final CartItemRepository cartItemRepository;
    private final CatalogClient catalogClient;
    private final InventoryLockExecutor inventoryLockExecutor;
    private final PaymentService paymentService;

    @Transactional
    public OrderResponse createOrder(Long memberId, CreateOrderRequest request) {
        if (request.paymentMethod() == PaymentMethod.VIRTUAL_CARD && request.cardId() == null) {
            throw new InvalidOrderRequestException("paymentMethod=CARD 이면 cardId 가 필수입니다.");
        }

        Map<Long, Integer> quantityByBookId = new LinkedHashMap<>();
        for (OrderItemRequest item : request.items()) {
            quantityByBookId.merge(item.bookId(), item.quantity(), Integer::sum);
        }
        List<Long> bookIds = List.copyOf(quantityByBookId.keySet());

        return inventoryLockExecutor.executeWithLock(bookIds, () -> {
            Map<Long, Inventory> inventories = loadInventories(bookIds);
            checkStock(inventories, quantityByBookId);

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

            if (request.paymentMethod() == PaymentMethod.VIRTUAL_CARD) {
                Payment payment = paymentService.approveCard(order, request.cardId(), totalAmount);
                order.markPaid();
                if (memberCoupon != null) {
                    memberCoupon.use(LocalDateTime.now(), order.getId());
                }
                deductStock(inventories, quantityByBookId);
                cartItemRepository.deleteByMemberIdAndBookIdIn(memberId, bookIds);
                return OrderResponse.of(order, items, payment);
            }

            // KAKAOPAY — 재고 차감/쿠폰 사용확정/장바구니 비우기는 approveKakaoPay 로 미룬다.
            if (memberCoupon != null) {
                order.reservePendingCoupon(memberCoupon.getId());
            }
            KakaoReadyOutcome outcome = paymentService.readyKakao(order, memberId, totalAmount);
            return OrderResponse.of(order, items, outcome.payment(), outcome.redirectUrl());
        });
    }

    /**
     * 카카오페이 2단계. 재고 재검증 → (통과 시에만) 카카오 승인 API 호출 → 재고 차감/쿠폰
     * 사용확정/장바구니 비우기 순서다. 재검증에서 막히면 카카오 승인 요청 자체가 안 나가므로
     * 사용자 결제는 실제로 이뤄지지 않는다.
     */
    @Transactional
    public OrderResponse approveKakaoPay(Long memberId, Long orderId, String pgToken) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.isOwnedBy(memberId)) {
            throw new UnauthorizedOrderAccessException(orderId);
        }
        if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new PaymentAlreadyProcessedException(orderId);
        }

        Payment payment = paymentRepository
                .findByOrderId(orderId)
                .filter(p -> p.getPaymentStatus() == PaymentStatus.READY)
                .orElseThrow(() -> new PaymentAlreadyProcessedException(orderId));

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Map<Long, Integer> quantityByBookId = new LinkedHashMap<>();
        for (OrderItem item : items) {
            quantityByBookId.merge(item.getBookId(), item.getQuantity(), Integer::sum);
        }
        List<Long> bookIds = List.copyOf(quantityByBookId.keySet());

        return inventoryLockExecutor.executeWithLock(bookIds, () -> {
            Map<Long, Inventory> inventories = loadInventories(bookIds);
            checkStock(inventories, quantityByBookId);

            MemberCoupon memberCoupon = null;
            if (order.getPendingMemberCouponId() != null) {
                memberCoupon = memberCouponRepository
                        .findById(order.getPendingMemberCouponId())
                        .orElseThrow(() -> new OrderCouponNotFoundException(order.getPendingMemberCouponId()));
                if (memberCoupon.isUsed()) {
                    throw new InvalidCouponException("이미 사용한 쿠폰입니다: " + order.getPendingMemberCouponId());
                }
            }

            paymentService.approveKakao(payment, orderId, memberId, pgToken);
            order.markPaid();

            if (memberCoupon != null) {
                memberCoupon.use(LocalDateTime.now(), orderId);
                order.clearPendingCoupon();
            }

            deductStock(inventories, quantityByBookId);
            cartItemRepository.deleteByMemberIdAndBookIdIn(memberId, bookIds);

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
        // 실패하면 CardRestoreFailedException/KakaoPayApiException 이 여기까지의 변경을 포함해 전부 롤백시킨다.
        paymentService.cancel(payment);

        List<OrderItem> items = restoreStockAndCoupon(orderId);

        return OrderResponse.of(order, items, payment);
    }

    /**
     * 배송 완료 후의 반품/교환 신청 접수다. cancel 과 달리 이 시점엔 재고/쿠폰/결제를 건드리지
     * 않는다 — 실제 환불은 반품 상품 회수 확인 후 refundOrder 로 별도 처리한다.
     */
    @Transactional
    public OrderResponse requestReturn(Long memberId, Long orderId, String reason) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.isOwnedBy(memberId)) {
            throw new UnauthorizedOrderAccessException(orderId);
        }
        // PAID 가 아니면 여기서 OrderCannotBeReturnedException 을 던진다.
        order.requestReturn(reason);

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        return OrderResponse.of(order, items, payment);
    }

    /**
     * 반품 신청된 주문의 환불 완료 처리다 — 결제 수단별 환불(카드 한도 복구/카카오페이 취소),
     * 재고 복구, 쿠폰 원복을 cancelOrder 와 동일한 방식으로 수행한다.
     */
    @Transactional
    public OrderResponse refundOrder(Long memberId, Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.isOwnedBy(memberId)) {
            throw new UnauthorizedOrderAccessException(orderId);
        }
        // RETURN_REQUESTED 가 아니면 여기서 OrderCannotBeRefundedException 을 던진다.
        order.completeRefund();

        Payment payment = paymentRepository
                .findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("반품 신청된 주문에 결제 정보가 없습니다: " + orderId));
        // 실패하면 CardRestoreFailedException/KakaoPayApiException 이 여기까지의 변경을 포함해 전부 롤백시킨다.
        paymentService.refund(payment);

        List<OrderItem> items = restoreStockAndCoupon(orderId);

        return OrderResponse.of(order, items, payment);
    }

    /** cancelOrder/refundOrder 가 공유하는 재고 복구 + 쿠폰 사용 원복. */
    private List<OrderItem> restoreStockAndCoupon(Long orderId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        List<Long> bookIds = items.stream().map(OrderItem::getBookId).distinct().toList();

        inventoryLockExecutor.executeWithLock(bookIds, () -> {
            Map<Long, Inventory> inventories = loadInventories(bookIds);
            for (OrderItem item : items) {
                inventories.get(item.getBookId()).restock(item.getQuantity());
            }
            return null;
        });

        memberCouponRepository.findByUsedOrderId(orderId).ifPresent(MemberCoupon::cancelUse);

        return items;
    }

    private Map<Long, Inventory> loadInventories(List<Long> bookIds) {
        return inventoryRepository.findByBookIdIn(bookIds).stream()
                .collect(Collectors.toMap(Inventory::getBookId, Function.identity()));
    }

    private void checkStock(Map<Long, Inventory> inventories, Map<Long, Integer> quantityByBookId) {
        for (Map.Entry<Long, Integer> entry : quantityByBookId.entrySet()) {
            Inventory inventory = inventories.get(entry.getKey());
            if (inventory == null || inventory.getStock() < entry.getValue()) {
                throw new OutOfStockException(entry.getKey());
            }
        }
    }

    private void deductStock(Map<Long, Inventory> inventories, Map<Long, Integer> quantityByBookId) {
        for (Map.Entry<Long, Integer> entry : quantityByBookId.entrySet()) {
            inventories.get(entry.getKey()).deduct(entry.getValue());
        }
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
