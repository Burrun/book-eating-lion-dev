package com.bookeatinglion.order.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.order.client.CardClient;
import com.bookeatinglion.order.client.CardClient.CardOperationResult;
import com.bookeatinglion.order.order.domain.Order;
import com.bookeatinglion.order.payment.client.KakaoPayClient;
import com.bookeatinglion.order.payment.client.KakaoPayClient.KakaoPayApproval;
import com.bookeatinglion.order.payment.domain.Payment;
import com.bookeatinglion.order.payment.domain.PaymentMethod;
import com.bookeatinglion.order.payment.domain.PaymentStatus;
import com.bookeatinglion.order.payment.exception.CardRestoreFailedException;
import com.bookeatinglion.order.payment.exception.PaymentDeclinedException;
import com.bookeatinglion.order.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CardClient cardClient;

    @Mock
    private KakaoPayClient kakaoPayClient;

    @InjectMocks
    private PaymentService paymentService;

    private Order order() {
        Order order = new Order(1L, "홍길동", "010-0000-0000", "06236", "서울", 10000);
        ReflectionTestUtils.setField(order, "id", 1L);
        return order;
    }

    @Test
    void CARD_결제가_승인되면_결제내역이_저장된다() {
        when(cardClient.deduct(anyLong(), any())).thenReturn(new CardOperationResult(true, null));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = paymentService.approve(order(), PaymentMethod.CARD, 55L, 10000);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getApprovalNumber()).isNotNull();
        assertThat(payment.getPgTid()).isNull();
    }

    @Test
    void CARD_결제가_거절되면_예외를_던지고_저장하지_않는다() {
        when(cardClient.deduct(anyLong(), any())).thenReturn(new CardOperationResult(false, "한도 초과"));

        assertThatThrownBy(() -> paymentService.approve(order(), PaymentMethod.CARD, 55L, 10000))
                .isInstanceOf(PaymentDeclinedException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void KAKAOPAY_결제는_KakaoPayClient가_발급한_tid를_쓴다() {
        when(kakaoPayClient.approve(anyLong(), anyInt())).thenReturn(new KakaoPayApproval("TC0ONETIME-XYZ", "A1"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = paymentService.approve(order(), PaymentMethod.KAKAOPAY, null, 10000);

        assertThat(payment.getPgTid()).isEqualTo("TC0ONETIME-XYZ");
        assertThat(payment.getApprovalNumber()).isEqualTo("A1");
    }

    @Test
    void CARD_취소는_한도를_복구하고_결제상태를_CANCELLED로_바꾼다() {
        Payment payment = new Payment(order(), 55L, PaymentMethod.CARD, 10000, "AP-1", null, "idem-1");
        when(cardClient.restore(anyLong(), any())).thenReturn(new CardOperationResult(true, null));

        paymentService.cancel(payment);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void CARD_한도복구가_실패하면_예외를_던지고_상태를_바꾸지_않는다() {
        Payment payment = new Payment(order(), 55L, PaymentMethod.CARD, 10000, "AP-1", null, "idem-1");
        when(cardClient.restore(anyLong(), any())).thenReturn(new CardOperationResult(false, "member-service 응답 없음"));

        assertThatThrownBy(() -> paymentService.cancel(payment)).isInstanceOf(CardRestoreFailedException.class);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void KAKAOPAY_취소는_KakaoPayClient_cancel을_호출한다() {
        Payment payment = new Payment(order(), null, PaymentMethod.KAKAOPAY, 10000, null, "TC0ONETIME-XYZ", "idem-1");

        paymentService.cancel(payment);

        verify(kakaoPayClient).cancel("TC0ONETIME-XYZ", 10000);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }
}
