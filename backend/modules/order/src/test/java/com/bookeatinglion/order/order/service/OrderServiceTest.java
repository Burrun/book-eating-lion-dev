package com.bookeatinglion.order.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.order.cart.repository.CartItemRepository;
import com.bookeatinglion.order.client.CardClient;
import com.bookeatinglion.order.client.CatalogClient;
import com.bookeatinglion.order.client.CatalogClient.BookDetailEnvelope;
import com.bookeatinglion.order.client.CatalogClient.BookView;
import com.bookeatinglion.order.coupon.domain.Coupon;
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
import com.bookeatinglion.order.order.exception.OrderCannotBeCancelledException;
import com.bookeatinglion.order.order.exception.OrderCannotBeRefundedException;
import com.bookeatinglion.order.order.exception.OrderCannotBeReturnedException;
import com.bookeatinglion.order.order.exception.OrderCouponNotFoundException;
import com.bookeatinglion.order.order.exception.OrderNotFoundException;
import com.bookeatinglion.order.order.exception.OutOfStockException;
import com.bookeatinglion.order.order.exception.PaymentAlreadyProcessedException;
import com.bookeatinglion.order.order.exception.UnauthorizedCouponAccessException;
import com.bookeatinglion.order.order.exception.UnauthorizedOrderAccessException;
import com.bookeatinglion.order.order.repository.OrderItemRepository;
import com.bookeatinglion.order.order.repository.OrderRepository;
import com.bookeatinglion.order.payment.client.KakaoPayClient;
import com.bookeatinglion.order.payment.client.KakaoPayClient.KakaoApproveResult;
import com.bookeatinglion.order.payment.client.KakaoPayClient.KakaoReadyResult;
import com.bookeatinglion.order.payment.domain.Payment;
import com.bookeatinglion.order.payment.domain.PaymentMethod;
import com.bookeatinglion.order.payment.domain.PaymentStatus;
import com.bookeatinglion.order.payment.exception.CardRestoreFailedException;
import com.bookeatinglion.order.payment.exception.PaymentDeclinedException;
import com.bookeatinglion.order.payment.repository.PaymentRepository;
import com.bookeatinglion.order.payment.service.PaymentService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private MemberCouponRepository memberCouponRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private CardClient cardClient;

    @Mock
    private KakaoPayClient kakaoPayClient;

    private InventoryLockExecutor passThroughLockExecutor;

    private PaymentService paymentService;

    private OrderService orderService;

    private void setUp() {
        passThroughLockExecutor = new InventoryLockExecutor(null) {
            @Override
            public <T> T executeWithLock(List<Long> bookIds, Supplier<T> action) {
                return action.get();
            }
        };
        paymentService = new PaymentService(paymentRepository, cardClient, kakaoPayClient);
        orderService = new OrderService(
                orderRepository,
                orderItemRepository,
                inventoryRepository,
                memberCouponRepository,
                paymentRepository,
                cartItemRepository,
                catalogClient,
                passThroughLockExecutor,
                paymentService);
    }

    private Inventory inventory(Long bookId, int stock) {
        return new Inventory(bookId, stock);
    }

    private BookDetailEnvelope book(Long bookId, String title, int price) {
        return new BookDetailEnvelope(true, new BookView(bookId, title, price, null));
    }

    private Recipient recipient() {
        return new Recipient("홍길동", "010-0000-0000", "06236", "서울시 강남구");
    }

    private Order order(Long id, Long memberId, int totalAmount) {
        Order order = new Order(memberId, "홍길동", "010-0000-0000", "06236", "서울시 강남구", totalAmount);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    private void stubOrderAndPaymentSave() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ---------------------------------------------------------------- createOrder / CARD

    @Test
    void 가상카드_결제가_승인되면_주문이_PAID가_되고_장바구니가_비워진다() {
        setUp();
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 10)));
        when(catalogClient.getBook(100L)).thenReturn(book(100L, "책1", 10000));
        stubOrderAndPaymentSave();
        when(cardClient.deduct(anyLong(), any())).thenReturn(new CardClient.CardOperationResult(true, null));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(100L, 1)), null, recipient(), PaymentMethod.VIRTUAL_CARD, 55L);

        OrderResponse response = orderService.createOrder(1L, request);

        assertThat(response.orderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(response.nextRedirectUrl()).isNull();
        assertThat(response.payment().paymentMethod()).isEqualTo(PaymentMethod.VIRTUAL_CARD);
        verify(cartItemRepository).deleteByMemberIdAndBookIdIn(1L, List.of(100L));
    }

    @Test
    void CARD_결제인데_cardId가_없으면_예외를_던진다() {
        setUp();
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(100L, 1)), null, recipient(), PaymentMethod.VIRTUAL_CARD, null);

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(InvalidOrderRequestException.class);
    }

    @Test
    void 가상카드_한도_차감이_거절되면_결제거절_예외를_던진다() {
        setUp();
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 10)));
        when(catalogClient.getBook(100L)).thenReturn(book(100L, "책1", 10000));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
        when(cardClient.deduct(anyLong(), any())).thenReturn(new CardClient.CardOperationResult(false, "한도 초과"));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(100L, 1)), null, recipient(), PaymentMethod.VIRTUAL_CARD, 55L);

        assertThatThrownBy(() -> orderService.createOrder(1L, request)).isInstanceOf(PaymentDeclinedException.class);

        verify(paymentRepository, never()).save(any());
        verify(cartItemRepository, never()).deleteByMemberIdAndBookIdIn(any(), any());
    }

    @Test
    void 쿠폰을_적용한_CARD_주문은_즉시_사용확정된다() {
        setUp();
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 10)));
        when(catalogClient.getBook(100L)).thenReturn(book(100L, "책1", 10000));
        Coupon coupon = new Coupon("C1", "쿠폰", 3000, 5000, LocalDateTime.now().plusDays(1));
        MemberCoupon memberCoupon = new MemberCoupon(1L, coupon);
        ReflectionTestUtils.setField(memberCoupon, "id", 9L);
        when(memberCouponRepository.findById(9L)).thenReturn(Optional.of(memberCoupon));
        stubOrderAndPaymentSave();
        when(cardClient.deduct(anyLong(), any())).thenReturn(new CardClient.CardOperationResult(true, null));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(100L, 1)), 9L, recipient(), PaymentMethod.VIRTUAL_CARD, 55L);

        OrderResponse response = orderService.createOrder(1L, request);

        assertThat(response.totalAmount()).isEqualTo(7000);
        assertThat(memberCoupon.isUsed()).isTrue();
        assertThat(memberCoupon.getUsedOrderId()).isEqualTo(1L);
    }

    // ---------------------------------------------------------------- createOrder / KAKAOPAY (ready)

    @Test
    void 카카오페이_주문_생성은_PENDING_PAYMENT와_리다이렉트URL을_반환하고_재고를_건드리지_않는다() {
        setUp();
        Inventory inventory = inventory(100L, 10);
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory));
        when(catalogClient.getBook(100L)).thenReturn(book(100L, "책1", 10000));
        stubOrderAndPaymentSave();
        when(kakaoPayClient.ready(anyLong(), eq(1L), anyString(), eq(20000)))
                .thenReturn(new KakaoReadyResult("T1", "https://mockup-pg-web.kakao.com/redirect"));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(100L, 2)), null, recipient(), PaymentMethod.KAKAO_PAY, null);

        OrderResponse response = orderService.createOrder(1L, request);

        assertThat(response.orderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.nextRedirectUrl()).isEqualTo("https://mockup-pg-web.kakao.com/redirect");
        assertThat(response.payment().paymentStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(inventory.getStock()).isEqualTo(10); // 아직 차감되지 않았다
        verify(cartItemRepository, never()).deleteByMemberIdAndBookIdIn(any(), any());
        verify(kakaoPayClient, never()).approve(any(), any(), any(), any());
    }

    @Test
    void 카카오페이_주문_생성시_쿠폰은_예약만_되고_아직_사용확정되지_않는다() {
        setUp();
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 10)));
        when(catalogClient.getBook(100L)).thenReturn(book(100L, "책1", 10000));
        Coupon coupon = new Coupon("C1", "쿠폰", 3000, 5000, LocalDateTime.now().plusDays(1));
        MemberCoupon memberCoupon = new MemberCoupon(1L, coupon);
        ReflectionTestUtils.setField(memberCoupon, "id", 9L);
        when(memberCouponRepository.findById(9L)).thenReturn(Optional.of(memberCoupon));
        stubOrderAndPaymentSave();
        when(kakaoPayClient.ready(anyLong(), anyLong(), anyString(), eq(7000)))
                .thenReturn(new KakaoReadyResult("T1", "https://mockup-pg-web.kakao.com/redirect"));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(100L, 1)), 9L, recipient(), PaymentMethod.KAKAO_PAY, null);

        OrderResponse response = orderService.createOrder(1L, request);

        assertThat(response.totalAmount()).isEqualTo(7000);
        assertThat(memberCoupon.isUsed()).isFalse();
    }

    @Test
    void 같은_책을_중복으로_담아도_합산해_재고를_검증한다() {
        setUp();
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 4)));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(100L, 2), new OrderItemRequest(100L, 3)),
                null,
                recipient(),
                PaymentMethod.KAKAO_PAY,
                null);

        assertThatThrownBy(() -> orderService.createOrder(1L, request)).isInstanceOf(OutOfStockException.class);
    }

    @Test
    void 재고보다_많이_주문하면_예외를_던진다() {
        setUp();
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 1)));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(100L, 2)), null, recipient(), PaymentMethod.KAKAO_PAY, null);

        assertThatThrownBy(() -> orderService.createOrder(1L, request)).isInstanceOf(OutOfStockException.class);
    }

    @Test
    void catalog_응답이_degrade되면_가격을_신뢰하지_않고_예외를_던진다() {
        setUp();
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 10)));
        when(catalogClient.getBook(100L))
                .thenReturn(new BookDetailEnvelope(false, new BookView(100L, "정보 조회 불가", 0, null)));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(100L, 1)), null, recipient(), PaymentMethod.KAKAO_PAY, null);

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(BookPriceUnavailableException.class);
    }

    @Test
    void 존재하지_않는_보유쿠폰이면_예외를_던진다() {
        setUp();
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 10)));
        when(catalogClient.getBook(100L)).thenReturn(book(100L, "책1", 10000));
        when(memberCouponRepository.findById(9L)).thenReturn(Optional.empty());

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(100L, 1)), 9L, recipient(), PaymentMethod.KAKAO_PAY, null);

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(OrderCouponNotFoundException.class);
    }

    @Test
    void 타인의_쿠폰이면_예외를_던진다() {
        setUp();
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 10)));
        when(catalogClient.getBook(100L)).thenReturn(book(100L, "책1", 10000));
        Coupon coupon = new Coupon("C1", "쿠폰", 1000, 5000, LocalDateTime.now().plusDays(1));
        MemberCoupon memberCoupon = new MemberCoupon(2L, coupon);
        ReflectionTestUtils.setField(memberCoupon, "id", 9L);
        when(memberCouponRepository.findById(9L)).thenReturn(Optional.of(memberCoupon));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(100L, 1)), 9L, recipient(), PaymentMethod.KAKAO_PAY, null);

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(UnauthorizedCouponAccessException.class);
    }

    @Test
    void 최소주문금액_미달_쿠폰이면_예외를_던진다() {
        setUp();
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 10)));
        when(catalogClient.getBook(100L)).thenReturn(book(100L, "책1", 1000));
        Coupon coupon = new Coupon("C1", "쿠폰", 500, 50000, LocalDateTime.now().plusDays(1));
        MemberCoupon memberCoupon = new MemberCoupon(1L, coupon);
        ReflectionTestUtils.setField(memberCoupon, "id", 9L);
        when(memberCouponRepository.findById(9L)).thenReturn(Optional.of(memberCoupon));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(100L, 1)), 9L, recipient(), PaymentMethod.KAKAO_PAY, null);

        assertThatThrownBy(() -> orderService.createOrder(1L, request)).isInstanceOf(InvalidCouponException.class);
    }

    // ---------------------------------------------------------------- approveKakaoPay

    private Order pendingKakaoOrder(int totalAmount, Long pendingMemberCouponId) {
        Order order = order(1L, 1L, totalAmount);
        if (pendingMemberCouponId != null) {
            order.reservePendingCoupon(pendingMemberCouponId);
        }
        return order;
    }

    private Payment readyPayment(Order order, int amount) {
        Payment payment = Payment.ready(order, amount, "T1", "idem-1");
        ReflectionTestUtils.setField(payment, "id", 500L);
        return payment;
    }

    @Test
    void 카카오페이_승인은_재고차감_쿠폰사용확정_장바구니비우기까지_전부_끝낸다() {
        setUp();
        Order order = pendingKakaoOrder(7000, 9L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        Payment payment = readyPayment(order, 7000);
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));
        OrderItem item = new OrderItem(order, 100L, "책1", 1, 10000);
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(item));
        Inventory inventory = inventory(100L, 10);
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory));

        Coupon coupon = new Coupon("C1", "쿠폰", 3000, 5000, LocalDateTime.now().plusDays(1));
        MemberCoupon memberCoupon = new MemberCoupon(1L, coupon);
        ReflectionTestUtils.setField(memberCoupon, "id", 9L);
        when(memberCouponRepository.findById(9L)).thenReturn(Optional.of(memberCoupon));

        when(kakaoPayClient.approve(1L, 1L, "T1", "pg-token")).thenReturn(new KakaoApproveResult("A1"));

        OrderResponse response = orderService.approveKakaoPay(1L, 1L, "pg-token");

        assertThat(response.orderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(response.payment().paymentStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.payment().approvalNumber()).isEqualTo("A1");
        assertThat(inventory.getStock()).isEqualTo(9);
        assertThat(memberCoupon.isUsed()).isTrue();
        verify(cartItemRepository).deleteByMemberIdAndBookIdIn(1L, List.of(100L));
    }

    @Test
    void 승인_시점에_재고가_부족하면_카카오_승인_API를_호출하지_않는다() {
        setUp();
        Order order = pendingKakaoOrder(10000, null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        Payment payment = readyPayment(order, 10000);
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));
        OrderItem item = new OrderItem(order, 100L, "책1", 5, 2000);
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(item));
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 1)));

        assertThatThrownBy(() -> orderService.approveKakaoPay(1L, 1L, "pg-token"))
                .isInstanceOf(OutOfStockException.class);

        verify(kakaoPayClient, never()).approve(any(), any(), any(), any());
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    void PAID_주문에_다시_승인_요청하면_예외를_던진다() {
        setUp();
        Order order = order(1L, 1L, 10000);
        ReflectionTestUtils.setField(order, "orderStatus", OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.approveKakaoPay(1L, 1L, "pg-token"))
                .isInstanceOf(PaymentAlreadyProcessedException.class);
    }

    @Test
    void 결제정보가_없으면_승인_요청은_예외를_던진다() {
        setUp();
        Order order = pendingKakaoOrder(10000, null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.approveKakaoPay(1L, 1L, "pg-token"))
                .isInstanceOf(PaymentAlreadyProcessedException.class);
    }

    @Test
    void 타인의_주문에_승인_요청하면_예외를_던진다() {
        setUp();
        Order order = order(1L, 2L, 10000);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.approveKakaoPay(1L, 1L, "pg-token"))
                .isInstanceOf(UnauthorizedOrderAccessException.class);
    }

    @Test
    void 존재하지_않는_주문에_승인_요청하면_예외를_던진다() {
        setUp();
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.approveKakaoPay(1L, 999L, "pg-token"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ---------------------------------------------------------------- getOrder

    @Test
    void 본인_주문을_상세조회한다() {
        setUp();
        Order order = order(1L, 1L, 10000);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        OrderResponse response = orderService.getOrder(1L, 1L);

        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.payment()).isNull();
    }

    @Test
    void 존재하지_않는_주문_조회는_예외를_던진다() {
        setUp();
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(1L, 999L)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void 타인의_주문_조회는_예외를_던진다() {
        setUp();
        Order order = order(1L, 2L, 10000);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrder(1L, 1L)).isInstanceOf(UnauthorizedOrderAccessException.class);
    }

    // ---------------------------------------------------------------- cancelOrder

    @Test
    void PAID_주문을_취소하면_재고와_결제가_복구된다() {
        setUp();
        Order paidOrder = order(1L, 1L, 10000);
        ReflectionTestUtils.setField(paidOrder, "orderStatus", OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));

        Payment payment = Payment.approved(paidOrder, null, PaymentMethod.KAKAO_PAY, 10000, null, "KAKAO-1", "idem-1");
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));

        OrderItem item = new OrderItem(paidOrder, 100L, "책1", 2, 5000);
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(item));
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 3)));
        when(memberCouponRepository.findByUsedOrderId(1L)).thenReturn(Optional.empty());

        OrderResponse response = orderService.cancelOrder(1L, 1L);

        assertThat(response.orderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void PAID가_아닌_주문은_취소할_수_없다() {
        setUp();
        Order pendingOrder = order(1L, 1L, 10000);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, 1L)).isInstanceOf(OrderCannotBeCancelledException.class);
    }

    @Test
    void 카드_한도_복구가_실패하면_예외를_던진다() {
        setUp();
        Order paidOrder = order(1L, 1L, 10000);
        ReflectionTestUtils.setField(paidOrder, "orderStatus", OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));

        Payment payment = Payment.approved(paidOrder, 55L, PaymentMethod.VIRTUAL_CARD, 10000, "AP-1", null, "idem-1");
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));
        when(cardClient.restore(anyLong(), any()))
                .thenReturn(new CardClient.CardOperationResult(false, "member-service 응답 없음"));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, 1L)).isInstanceOf(CardRestoreFailedException.class);
    }

    @Test
    void 카카오페이_취소는_KakaoPayClient_cancel을_호출한다() {
        setUp();
        Order paidOrder = order(1L, 1L, 10000);
        ReflectionTestUtils.setField(paidOrder, "orderStatus", OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));

        Payment payment = Payment.approved(paidOrder, null, PaymentMethod.KAKAO_PAY, 10000, null, "T1", "idem-1");
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));

        OrderItem item = new OrderItem(paidOrder, 100L, "책1", 1, 10000);
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(item));
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 3)));
        when(memberCouponRepository.findByUsedOrderId(1L)).thenReturn(Optional.empty());

        orderService.cancelOrder(1L, 1L);

        verify(kakaoPayClient).cancel("T1", 10000);
    }

    // ---------------------------------------------------------------- requestReturn / refundOrder

    @Test
    void PAID_주문에_반품을_신청하면_RETURN_REQUESTED가_되고_사유가_저장된다() {
        setUp();
        Order paidOrder = order(1L, 1L, 10000);
        ReflectionTestUtils.setField(paidOrder, "orderStatus", OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        OrderResponse response = orderService.requestReturn(1L, 1L, "단순 변심");

        assertThat(response.orderStatus()).isEqualTo(OrderStatus.RETURN_REQUESTED);
        assertThat(response.returnReason()).isEqualTo("단순 변심");
    }

    @Test
    void PAID가_아닌_주문은_반품_신청할_수_없다() {
        setUp();
        Order pendingOrder = order(1L, 1L, 10000);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));

        assertThatThrownBy(() -> orderService.requestReturn(1L, 1L, "단순 변심"))
                .isInstanceOf(OrderCannotBeReturnedException.class);
    }

    @Test
    void 타인의_주문은_반품_신청할_수_없다() {
        setUp();
        Order order = order(1L, 2L, 10000);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.requestReturn(1L, 1L, "단순 변심"))
                .isInstanceOf(UnauthorizedOrderAccessException.class);
    }

    @Test
    void RETURN_REQUESTED_주문을_환불하면_재고와_결제가_복구된다() {
        setUp();
        Order returnRequestedOrder = order(1L, 1L, 10000);
        ReflectionTestUtils.setField(returnRequestedOrder, "orderStatus", OrderStatus.RETURN_REQUESTED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(returnRequestedOrder));

        Payment payment =
                Payment.approved(returnRequestedOrder, null, PaymentMethod.KAKAO_PAY, 10000, null, "KAKAO-1", "idem-1");
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));

        OrderItem item = new OrderItem(returnRequestedOrder, 100L, "책1", 2, 5000);
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(item));
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 3)));
        when(memberCouponRepository.findByUsedOrderId(1L)).thenReturn(Optional.empty());

        OrderResponse response = orderService.refundOrder(1L, 1L);

        assertThat(response.orderStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(kakaoPayClient).cancel("KAKAO-1", 10000);
    }

    @Test
    void RETURN_REQUESTED가_아닌_주문은_환불할_수_없다() {
        setUp();
        Order paidOrder = order(1L, 1L, 10000);
        ReflectionTestUtils.setField(paidOrder, "orderStatus", OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));

        assertThatThrownBy(() -> orderService.refundOrder(1L, 1L)).isInstanceOf(OrderCannotBeRefundedException.class);
    }

    @Test
    void 카드_환불시_한도_복구가_실패하면_예외를_던진다() {
        setUp();
        Order returnRequestedOrder = order(1L, 1L, 10000);
        ReflectionTestUtils.setField(returnRequestedOrder, "orderStatus", OrderStatus.RETURN_REQUESTED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(returnRequestedOrder));

        Payment payment =
                Payment.approved(returnRequestedOrder, 55L, PaymentMethod.VIRTUAL_CARD, 10000, "AP-1", null, "idem-1");
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));
        when(cardClient.restore(anyLong(), any()))
                .thenReturn(new CardClient.CardOperationResult(false, "member-service 응답 없음"));

        assertThatThrownBy(() -> orderService.refundOrder(1L, 1L)).isInstanceOf(CardRestoreFailedException.class);
    }
}
