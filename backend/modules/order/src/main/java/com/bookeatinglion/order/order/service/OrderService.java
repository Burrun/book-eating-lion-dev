package com.bookeatinglion.order.order.service;

import com.bookeatinglion.common.event.ReviewPermissionGranted;
import com.bookeatinglion.order.cart.repository.CartItemRepository;
import com.bookeatinglion.order.client.CatalogClient;
import com.bookeatinglion.order.client.CatalogClient.BookDetailEnvelope;
import com.bookeatinglion.order.client.MemberSubscriptionClient;
import com.bookeatinglion.order.coupon.domain.MemberCoupon;
import com.bookeatinglion.order.coupon.repository.MemberCouponRepository;
import com.bookeatinglion.order.delivery.domain.Delivery;
import com.bookeatinglion.order.delivery.domain.DeliveryStatus;
import com.bookeatinglion.order.delivery.repository.DeliveryRepository;
import com.bookeatinglion.order.event.BookPurchasePublisher;
import com.bookeatinglion.order.event.ReviewPermissionPublisher;
import com.bookeatinglion.order.inventory.domain.Inventory;
import com.bookeatinglion.order.inventory.repository.InventoryRepository;
import com.bookeatinglion.order.lock.InventoryLockExecutor;
import com.bookeatinglion.order.order.domain.Order;
import com.bookeatinglion.order.order.domain.OrderItem;
import com.bookeatinglion.order.order.domain.OrderStatus;
import com.bookeatinglion.order.order.dto.AdminOrderSummaryResponse;
import com.bookeatinglion.order.order.dto.CreateOrderRequest;
import com.bookeatinglion.order.order.dto.OrderItemRequest;
import com.bookeatinglion.order.order.dto.OrderResponse;
import com.bookeatinglion.order.order.dto.OrderSummaryResponse;
import com.bookeatinglion.order.order.dto.Recipient;
import com.bookeatinglion.order.order.exception.AlreadySubscribedException;
import com.bookeatinglion.order.order.exception.BookPriceUnavailableException;
import com.bookeatinglion.order.order.exception.InvalidCouponException;
import com.bookeatinglion.order.order.exception.InvalidOrderRequestException;
import com.bookeatinglion.order.order.exception.InventoryNotFoundException;
import com.bookeatinglion.order.order.exception.OrderCouponNotFoundException;
import com.bookeatinglion.order.order.exception.OrderNotFoundException;
import com.bookeatinglion.order.order.exception.OutOfStockException;
import com.bookeatinglion.order.order.exception.PaymentAlreadyProcessedException;
import com.bookeatinglion.order.order.exception.SubscriptionCheckFailedException;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    /**
     * 구독권 상품의 bookId. 이 책이 든 주문이 결제 확정되면 member-service 에 구독을 만든다.
     *
     * <p>구독을 별도 상품 타입으로 두지 않고 카탈로그의 도서 하나로 표현한다 — 가격·재고·주문
     * 항목 스냅샷이 전부 기존 경로를 그대로 타고, order 도메인에 새 타입을 만들 필요가 없다.
     *
     * <p>기본값 9001 은 db/postgres/90-demo-data.sql 이 명시적으로 넣는 값이다 — 자동 채번
     * 대역 밖이라 dev/prod 어느 환경이든 같다. 시드가 안 돈 환경이면 그 도서가 없어 주문
     * 자체가 성립하지 않으므로, 값이 맞아도 기능만 안 탈 뿐 오작동하지 않는다.
     */
    @Value("${app.subscription.book-id:9001}")
    private long subscriptionBookId;

    /** 구독권 결제 시 만들 플랜. member 의 PlanType enum 이름이어야 한다. */
    @Value("${app.subscription.plan-type:MONTHLY}")
    private String subscriptionPlanType;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryRepository inventoryRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final PaymentRepository paymentRepository;
    private final CartItemRepository cartItemRepository;
    private final CatalogClient catalogClient;
    private final InventoryLockExecutor inventoryLockExecutor;
    private final PaymentService paymentService;
    private final DeliveryRepository deliveryRepository;
    private final ReviewPermissionPublisher reviewPermissionPublisher;
    private final BookPurchasePublisher bookPurchasePublisher;
    private final MemberSubscriptionClient memberSubscriptionClient;

    @Transactional
    public OrderResponse createOrder(String memberId, String nickname, CreateOrderRequest request) {
        if (request.paymentMethod() == PaymentMethod.VIRTUAL_CARD && request.cardId() == null) {
            throw new InvalidOrderRequestException("paymentMethod=CARD 이면 cardId 가 필수입니다.");
        }

        Map<Long, Integer> quantityByBookId = new LinkedHashMap<>();
        for (OrderItemRequest item : request.items()) {
            quantityByBookId.merge(item.bookId(), item.quantity(), Integer::sum);
        }
        List<Long> bookIds = List.copyOf(quantityByBookId.keySet());

        if (containsSubscription(quantityByBookId)) {
            rejectIfAlreadySubscribed(memberId);
        }

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
                    recipient.addressDetail(),
                    recipient.deliveryRequest(),
                    totalAmount));

            List<OrderItem> items = snapshots.stream()
                    .map(s -> new OrderItem(order, s.bookId(), s.title(), s.quantity(), s.price()))
                    .toList();
            orderItemRepository.saveAll(items);

            if (request.paymentMethod() == PaymentMethod.VIRTUAL_CARD) {
                Payment payment = paymentService.approveCard(order, request.cardId(), totalAmount);
                order.markPaid();
                publishPurchaseConfirmed(memberId, nickname, items);
                activateSubscriptionIfOrdered(memberId, items, order.getId());
                if (memberCoupon != null) {
                    memberCoupon.use(LocalDateTime.now(), order.getId());
                }
                deductStock(inventories, quantityByBookId);
                cartItemRepository.deleteByMemberIdAndBookIdIn(memberId, bookIds);
                createDelivery(order.getId(), items);
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
    public OrderResponse approveKakaoPay(String memberId, String nickname, Long orderId, String pgToken) {
        // 행을 잠그고 읽는다 — 같은 주문에 승인 요청이 동시에 두 번 들어와도 뒤엣놈은 여기서
        // 앞엣놈의 커밋을 기다렸다가 아래 상태 검사에서 카카오 승인 전에 거절된다.
        Order order = orderRepository.findWithLockById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
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

        // 카카오 ready 와 approve 사이에는 시간이 있다. 그 사이에 다른 경로로 구독이 생겼을 수
        // 있으므로 재고와 마찬가지로 여기서 다시 본다 — 승인 API 를 부르기 전이라 환불이 없다.
        if (containsSubscription(items)) {
            rejectIfAlreadySubscribed(memberId);
        }

        return inventoryLockExecutor.executeWithLock(bookIds, () -> {
            Map<Long, Inventory> inventories = loadInventories(bookIds);
            checkStock(inventories, quantityByBookId);

            MemberCoupon memberCoupon = null;
            if (order.getPendingMemberCouponId() != null) {
                // FOR UPDATE — 같은 쿠폰이 걸린 다른 주문의 승인과 겹치면 그쪽 커밋을 기다렸다가
                // used 를 다시 본다. 카카오 승인 전이라 여기서 거절하면 환불이 필요 없다.
                memberCoupon = memberCouponRepository
                        .findWithLockById(order.getPendingMemberCouponId())
                        .orElseThrow(() -> new OrderCouponNotFoundException(order.getPendingMemberCouponId()));
                if (memberCoupon.isUsed()) {
                    throw new InvalidCouponException("이미 사용한 쿠폰입니다: " + order.getPendingMemberCouponId());
                }
            }

            paymentService.approveKakao(payment, orderId, memberId, pgToken);
            order.markPaid();
            publishPurchaseConfirmed(memberId, nickname, items);
            activateSubscriptionIfOrdered(memberId, items, orderId);

            if (memberCoupon != null) {
                memberCoupon.use(LocalDateTime.now(), orderId);
                order.clearPendingCoupon();
            }

            deductStock(inventories, quantityByBookId);
            cartItemRepository.deleteByMemberIdAndBookIdIn(memberId, bookIds);
            createDelivery(orderId, items);

            return OrderResponse.of(order, items, payment);
        });
    }

    /**
     * 구독권이 포함된 주문이면 결제 확정 후 member-service 에 구독 생성을 요청한다.
     *
     * <p>🔴 <b>커밋 이후에 부른다.</b> 트랜잭션 안에서 부르면 member-service 가 흔들릴 때 주문까지
     * 롤백되는데, 그 시점엔 카카오/카드 승인이 이미 끝나 있다 — 돈은 나갔는데 주문은 미결제로
     * 남는 최악의 상태가 DB 에 굳는다. 주문을 먼저 확정하고 구독 생성을 뒤로 미루면, 실패해도
     * PAID 주문과 아래 ERROR 로그가 남아 사람이 복구할 수 있다.
     * ({@link #publishPurchaseConfirmed} 의 SQS 발행이 afterCommit 인 것과 같은 이유다)
     *
     * <p>🔴 <b>예외를 밖으로 내보내지 않는다.</b> 같은 이유다 — afterCommit 훅에서 던져봐야
     * 커밋은 이미 끝났고 사용자에게는 500 만 보인다. 결제는 성공했으므로 주문 응답은 성공이어야
     * 하고, 어긋난 구독은 로그로 드러내는 게 맞다.
     *
     * <p>member 쪽 엔드포인트가 멱등이라 이 호출은 몇 번을 다시 해도 안전하다.
     */
    /** 구독권이 담긴 주문인지. bookId 미설정(-1)이면 어떤 주문과도 안 맞아 기능이 꺼진다. */
    private boolean containsSubscription(List<OrderItem> items) {
        return items.stream().anyMatch(item -> item.getBookId() == subscriptionBookId);
    }

    private boolean containsSubscription(Map<Long, Integer> quantityByBookId) {
        return quantityByBookId.containsKey(subscriptionBookId);
    }

    /**
     * 이미 구독 중이면 구독권 결제를 <b>돈이 나가기 전에</b> 막는다.
     *
     * <p>재고 재검증과 같은 자리, 같은 이유다(클래스 주석 참고) — 카카오 승인 API 호출 전에
     * 걸러야 환불 로직이 필요 없다. 받고 나서 되돌리려면 환불 경로가 있어야 하는데 없다.
     *
     * <p>프론트가 이미 구독 중이면 CTA 를 막지만(MyPage/ProductList/ProductDetail) 그것만으로는
     * 부족하다 — 탭 두 개, 뒤로가기, 오래된 캐시, /checkout 직접 진입이 전부 그 아래로 들어온다.
     *
     * <p>🔴 조회 실패는 "구독 없음"으로 넘기지 않는다. 틀렸을 때의 결과가 비대칭이다 — 잘못
     * 막으면 사용자가 다시 시도하면 되지만, 잘못 통과시키면 돈이 나간 뒤에야 드러난다.
     */
    private void rejectIfAlreadySubscribed(String memberId) {
        boolean subscribed;
        try {
            subscribed =
                    memberSubscriptionClient.getSubscriptionStatus(memberId).subscribed();
        } catch (RuntimeException e) {
            throw new SubscriptionCheckFailedException(memberId, e);
        }
        if (subscribed) {
            throw new AlreadySubscribedException(memberId);
        }
    }

    private void activateSubscriptionIfOrdered(String memberId, List<OrderItem> items, Long orderId) {
        if (!containsSubscription(items)) {
            return;
        }

        Runnable activate = () -> {
            try {
                memberSubscriptionClient.activate(
                        memberId, new MemberSubscriptionClient.ActivateRequest(subscriptionPlanType));
                log.info("구독 활성화 완료. orderId={}, memberId={}", orderId, memberId);
            } catch (RuntimeException e) {
                log.error(
                        "구독 활성화 실패 - 결제는 이미 확정됐다. 수동 복구 필요." + " orderId={}, memberId={}, planType={}",
                        orderId,
                        memberId,
                        subscriptionPlanType,
                        e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    activate.run();
                }
            });
        } else {
            activate.run();
        }
    }

    /**
     * 결제가 최종 확정되는 시점(카드 1단계 완료 / 카카오 approve)에 재고차감과 같은 자리에서 배송을 만든다.
     * find-or-create — 재시도로 이 지점이 두 번 불리는 경우, 이미 있으면 새로 만들지 않는다. orderId 에는
     * DB unique 제약도 걸려 있어(Delivery 참고) 이 조회와 저장 사이의 아주 좁은 동시성 창을 뚫고 들어와도
     * 중복 행 자체는 만들어지지 않는다 — 다만 그 경우엔 이 메서드가 처리하지 않은 제약 위반 예외를 던진다.
     *
     * <p>구독권은 실물 배송이 없다 — PENDING 으로 만들면 관리자가 실물 책처럼
     * SHIPPED/IN_TRANSIT 단계를 밟아야 한다. 결제 확정 = 배송 완료로 바로 만든다.
     */
    private void createDelivery(Long orderId, List<OrderItem> items) {
        if (deliveryRepository.findByOrderId(orderId).isPresent()) {
            return;
        }
        DeliveryStatus initialStatus = containsSubscription(items) ? DeliveryStatus.DELIVERED : DeliveryStatus.PENDING;
        deliveryRepository.save(Delivery.builder()
                .orderId(orderId)
                .deliveryStatus(initialStatus)
                .build());
    }

    /**
     * 결제 확정(order.markPaid() 직후) 후속 이벤트 발행. 리뷰 권한(Redis Streams)과 구매 확정
     * (SQS) 둘 다 <b>afterCommit</b> 으로 미룬다 — 커밋 전에 나가면, 이후 단계
     * (쿠폰 사용확정 / createDelivery 의 UNIQUE 충돌 등)에서 롤백이 나도 catalog 는 이미 리뷰
     * 권한을, ai 는 이미 검색 권한을 적재해버려 되돌릴 수 없다. 리뷰 권한은 eBook 열람 권한까지
     * 겸하므로(EbookService.getAccess 가 review_permissions 존재만 본다) 롤백된 주문에 새어
     * 나가면 결제 안 된 책을 계속 읽게 된다.
     *
     * <p>afterCommit 은 catalog-service 응답을 기다리는 것과 무관하다 — 둘 다 비동기 채널로
     * 던지고 소비는 상대 서비스가 알아서 한다.
     *
     * <p>🔴 두 채널은 서로 독립이다. afterCommit 시점엔 주문이 이미 커밋됐으므로 한쪽 발행이
     * 실패해도 예외를 올려봐야 되돌릴 게 없고, 같은 Runnable 안에서 던지면 뒤엣놈(SQS)이
     * 아예 안 나간다. 그래서 각각 try/catch 로 감싸 로그만 남기고 서로를 막지 않는다.
     */
    private void publishPurchaseConfirmed(String memberId, String nickname, List<OrderItem> items) {
        String grantedAt = LocalDateTime.now().toString();
        Runnable publish = () -> {
            try {
                for (OrderItem item : items) {
                    reviewPermissionPublisher.publish(
                            new ReviewPermissionGranted(memberId, item.getId(), item.getBookId(), nickname, grantedAt));
                }
            } catch (RuntimeException e) {
                log.error("리뷰 권한 이벤트 발행 실패 — 결제는 이미 확정됨. 수동 확인 필요. memberId={}", memberId, e);
            }
            try {
                items.forEach(item -> bookPurchasePublisher.publish(memberId, item.getBookId()));
            } catch (RuntimeException e) {
                log.error("구매 확정 SQS 이벤트 발행 실패 — 결제는 이미 확정됨. 수동 확인 필요. memberId={}", memberId, e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    public Page<OrderSummaryResponse> getOrders(String memberId, Pageable pageable) {
        return orderRepository.findByMemberId(memberId, pageable).map(OrderSummaryResponse::from);
    }

    /** 관리자용 전체 주문 목록. statusFilter 가 null 이면 상태 무관 전체 조회. */
    public Page<AdminOrderSummaryResponse> getAdminOrders(OrderStatus statusFilter, Pageable pageable) {
        Page<Order> orders = statusFilter == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findByOrderStatus(statusFilter, pageable);

        List<Long> orderIds = orders.map(Order::getId).getContent();
        Map<Long, DeliveryStatus> deliveryStatusByOrderId = deliveryRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(Delivery::getOrderId, Delivery::getDeliveryStatus));

        return orders.map(order -> AdminOrderSummaryResponse.of(order, deliveryStatusByOrderId.get(order.getId())));
    }

    public OrderResponse getOrder(String memberId, Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.isOwnedBy(memberId)) {
            throw new UnauthorizedOrderAccessException(orderId);
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        return OrderResponse.of(order, items, payment);
    }

    @Transactional
    public OrderResponse cancelOrder(String memberId, Long orderId) {
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
    public OrderResponse requestReturn(String memberId, Long orderId, String reason) {
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
    public OrderResponse refundOrder(String memberId, Long orderId) {
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
            // 행이 없는 것과 재고가 모자란 것을 구분한다. 전자는 데이터 누락(카탈로그엔
            // 있는데 inventory 행이 안 만들어짐)이라 운영이 손댈 곳이 다르다.
            if (inventory == null) {
                throw new InventoryNotFoundException(entry.getKey());
            }
            if (inventory.getStock() < entry.getValue()) {
                throw new OutOfStockException(entry.getKey());
            }
        }
    }

    private void deductStock(Map<Long, Inventory> inventories, Map<Long, Integer> quantityByBookId) {
        for (Map.Entry<Long, Integer> entry : quantityByBookId.entrySet()) {
            inventories.get(entry.getKey()).deduct(entry.getValue());
        }
    }

    private MemberCoupon validateAndGetCoupon(String memberId, Long memberCouponId, int subtotal) {
        if (memberCouponId == null) {
            return null;
        }

        // FOR UPDATE — 쿠폰 적용은 결제 직전에 일어난다. 같은 쿠폰을 쓰는 두 주문이 동시에
        // 들어오면 뒤엣놈은 앞엣놈의 커밋을 기다렸다가 아래 isUsed() 에서 걸려 결제 전에
        // 거절된다. 이 락이 없으면 둘 다 통과해 할인이 두 번 적용되고, 취소 시
        // findByUsedOrderId 는 한 주문만 찾아 나머지 쿠폰은 복구되지 않는다.
        MemberCoupon memberCoupon = memberCouponRepository
                .findWithLockById(memberCouponId)
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
