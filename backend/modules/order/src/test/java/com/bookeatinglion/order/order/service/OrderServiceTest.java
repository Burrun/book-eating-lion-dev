package com.bookeatinglion.order.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.bookeatinglion.order.order.dto.CreateOrderRequest;
import com.bookeatinglion.order.order.dto.OrderItemRequest;
import com.bookeatinglion.order.order.dto.OrderResponse;
import com.bookeatinglion.order.order.dto.Recipient;
import com.bookeatinglion.order.order.exception.BookPriceUnavailableException;
import com.bookeatinglion.order.order.exception.InvalidCouponException;
import com.bookeatinglion.order.order.exception.InvalidOrderRequestException;
import com.bookeatinglion.order.order.exception.OrderCannotBeCancelledException;
import com.bookeatinglion.order.order.exception.OrderCouponNotFoundException;
import com.bookeatinglion.order.order.exception.OrderNotFoundException;
import com.bookeatinglion.order.order.exception.OutOfStockException;
import com.bookeatinglion.order.order.exception.UnauthorizedCouponAccessException;
import com.bookeatinglion.order.order.exception.UnauthorizedOrderAccessException;
import com.bookeatinglion.order.order.repository.OrderItemRepository;
import com.bookeatinglion.order.order.repository.OrderRepository;
import com.bookeatinglion.order.payment.domain.Payment;
import com.bookeatinglion.order.payment.domain.PaymentMethod;
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
    private CatalogClient catalogClient;

    @Mock
    private CardClient cardClient;

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
        paymentService = new PaymentService(paymentRepository, cardClient);
        orderService = new OrderService(
                orderRepository,
                orderItemRepository,
                inventoryRepository,
                memberCouponRepository,
                paymentRepository,
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

    @Test
    void 카카오페이_주문을_생성한다() {
        setUp();
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 10)));
        when(catalogClient.getBook(100L)).thenReturn(book(100L, "책1", 10000));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(100L, 2)), null, recipient(), PaymentMethod.KAKAOPAY, null);

        OrderResponse response = orderService.createOrder(1L, request);

        assertThat(response.orderStatus().name()).isEqualTo("PAID");
        assertThat(response.totalAmount()).isEqualTo(20000);
        assertThat(response.payment().paymentMethod()).isEqualTo(PaymentMethod.KAKAOPAY);
        assertThat(response.payment().pgTid()).isNotNull();
    }

    @Test
    void 가상카드_결제가_승인되면_주문이_PAID가_된다() {
        setUp();
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 10)));
        when(catalogClient.getBook(100L)).thenReturn(book(100L, "책1", 10000));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cardClient.deduct(anyLong(), any())).thenReturn(new CardClient.CardOperationResult(true, null));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(100L, 1)), null, recipient(), PaymentMethod.CARD, 55L);

        OrderResponse response = orderService.createOrder(1L, request);

        assertThat(response.orderStatus().name()).isEqualTo("PAID");
        assertThat(response.payment().paymentMethod()).isEqualTo(PaymentMethod.CARD);
    }

    @Test
    void CARD_결제인데_cardId가_없으면_예외를_던진다() {
        setUp();
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(100L, 1)), null, recipient(), PaymentMethod.CARD, null);

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
                List.of(new OrderItemRequest(100L, 1)), null, recipient(), PaymentMethod.CARD, 55L);

        assertThatThrownBy(() -> orderService.createOrder(1L, request)).isInstanceOf(PaymentDeclinedException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void 같은_책을_중복으로_담아도_합산해_재고를_검증한다() {
        setUp();
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 4)));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(100L, 2), new OrderItemRequest(100L, 3)),
                null,
                recipient(),
                PaymentMethod.KAKAOPAY,
                null);

        assertThatThrownBy(() -> orderService.createOrder(1L, request)).isInstanceOf(OutOfStockException.class);
    }

    @Test
    void 재고보다_많이_주문하면_예외를_던진다() {
        setUp();
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 1)));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(100L, 2)), null, recipient(), PaymentMethod.KAKAOPAY, null);

        assertThatThrownBy(() -> orderService.createOrder(1L, request)).isInstanceOf(OutOfStockException.class);
    }

    @Test
    void catalog_응답이_degrade되면_가격을_신뢰하지_않고_예외를_던진다() {
        setUp();
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 10)));
        when(catalogClient.getBook(100L))
                .thenReturn(new BookDetailEnvelope(false, new BookView(100L, "정보 조회 불가", 0, null)));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(100L, 1)), null, recipient(), PaymentMethod.KAKAOPAY, null);

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
                List.of(new OrderItemRequest(100L, 1)), 9L, recipient(), PaymentMethod.KAKAOPAY, null);

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
                List.of(new OrderItemRequest(100L, 1)), 9L, recipient(), PaymentMethod.KAKAOPAY, null);

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
                List.of(new OrderItemRequest(100L, 1)), 9L, recipient(), PaymentMethod.KAKAOPAY, null);

        assertThatThrownBy(() -> orderService.createOrder(1L, request)).isInstanceOf(InvalidCouponException.class);
    }

    @Test
    void 쿠폰을_적용하면_할인금액만큼_총액이_줄어든다() {
        setUp();
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 10)));
        when(catalogClient.getBook(100L)).thenReturn(book(100L, "책1", 10000));
        Coupon coupon = new Coupon("C1", "쿠폰", 3000, 5000, LocalDateTime.now().plusDays(1));
        MemberCoupon memberCoupon = new MemberCoupon(1L, coupon);
        ReflectionTestUtils.setField(memberCoupon, "id", 9L);
        when(memberCouponRepository.findById(9L)).thenReturn(Optional.of(memberCoupon));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(100L, 1)), 9L, recipient(), PaymentMethod.KAKAOPAY, null);

        OrderResponse response = orderService.createOrder(1L, request);

        assertThat(response.totalAmount()).isEqualTo(7000);
        assertThat(memberCoupon.isUsed()).isTrue();
        assertThat(memberCoupon.getUsedOrderId()).isEqualTo(1L);
    }

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

    @Test
    void PAID_주문을_취소하면_재고와_결제가_복구된다() {
        setUp();
        Order paidOrder = order(1L, 1L, 10000);
        ReflectionTestUtils.setField(paidOrder, "orderStatus", com.bookeatinglion.order.order.domain.OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));

        Order dummyOrderRef = order(1L, 1L, 10000);
        Payment payment = new Payment(dummyOrderRef, null, PaymentMethod.KAKAOPAY, 10000, null, "KAKAO-1", "idem-1");
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));

        com.bookeatinglion.order.order.domain.OrderItem item =
                new com.bookeatinglion.order.order.domain.OrderItem(paidOrder, 100L, "책1", 2, 5000);
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(item));
        when(inventoryRepository.findByBookIdIn(List.of(100L))).thenReturn(List.of(inventory(100L, 3)));
        when(memberCouponRepository.findByUsedOrderId(1L)).thenReturn(Optional.empty());

        OrderResponse response = orderService.cancelOrder(1L, 1L);

        assertThat(response.orderStatus().name()).isEqualTo("CANCELLED");
        assertThat(payment.getPaymentStatus().name()).isEqualTo("CANCELLED");
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
        ReflectionTestUtils.setField(paidOrder, "orderStatus", com.bookeatinglion.order.order.domain.OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));

        Payment payment = new Payment(paidOrder, 55L, PaymentMethod.CARD, 10000, "AP-1", null, "idem-1");
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));
        when(cardClient.restore(anyLong(), any()))
                .thenReturn(new CardClient.CardOperationResult(false, "member-service 응답 없음"));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, 1L)).isInstanceOf(CardRestoreFailedException.class);
    }
}
