package com.bookeatinglion.order.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.order.client.CardClient;
import com.bookeatinglion.order.client.CardClient.CardOperationResult;
import com.bookeatinglion.order.order.domain.Order;
import com.bookeatinglion.order.payment.client.KakaoPayClient;
import com.bookeatinglion.order.payment.client.KakaoPayClient.KakaoApproveResult;
import com.bookeatinglion.order.payment.client.KakaoPayClient.KakaoReadyResult;
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

        Payment payment = paymentService.approveCard(order(), 55L, 10000);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getApprovalNumber()).isNotNull();
        assertThat(payment.getPgTid()).isNull();
    }

    @Test
    void CARD_결제가_거절되면_예외를_던지고_저장하지_않는다() {
        when(cardClient.deduct(anyLong(), any())).thenReturn(new CardOperationResult(false, "한도 초과"));

        assertThatThrownBy(() -> paymentService.approveCard(order(), 55L, 10000))
                .isInstanceOf(PaymentDeclinedException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void 카카오페이_ready는_READY_상태의_결제와_리다이렉트URL을_돌려준다() {
        when(kakaoPayClient.ready(anyLong(), anyLong(), anyString(), anyInt()))
                .thenReturn(new KakaoReadyResult("T123", "https://mockup-pg-web.kakao.com/redirect"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentService.KakaoReadyOutcome outcome = paymentService.readyKakao(order(), 1L, 10000);

        assertThat(outcome.payment().getPaymentStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(outcome.payment().getPgTid()).isEqualTo("T123");
        assertThat(outcome.payment().getApprovalNumber()).isNull();
        assertThat(outcome.redirectUrl()).isEqualTo("https://mockup-pg-web.kakao.com/redirect");
    }

    @Test
    void 카카오페이_approve는_결제를_APPROVED로_전환한다() {
        Payment payment = Payment.ready(order(), 10000, "T123", "idem-1");
        when(kakaoPayClient.approve(1L, 1L, "T123", "pg-token")).thenReturn(new KakaoApproveResult("A987"));

        paymentService.approveKakao(payment, 1L, 1L, "pg-token");

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getApprovalNumber()).isEqualTo("A987");
    }

    @Test
    void CARD_취소는_한도를_복구하고_결제상태를_CANCELLED로_바꾼다() {
        Payment payment = Payment.approved(order(), 55L, PaymentMethod.CARD, 10000, "AP-1", null, "idem-1");
        when(cardClient.restore(anyLong(), any())).thenReturn(new CardOperationResult(true, null));

        paymentService.cancel(payment);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void CARD_한도복구가_실패하면_예외를_던지고_상태를_바꾸지_않는다() {
        Payment payment = Payment.approved(order(), 55L, PaymentMethod.CARD, 10000, "AP-1", null, "idem-1");
        when(cardClient.restore(anyLong(), any())).thenReturn(new CardOperationResult(false, "member-service 응답 없음"));

        assertThatThrownBy(() -> paymentService.cancel(payment)).isInstanceOf(CardRestoreFailedException.class);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void KAKAOPAY_취소는_KakaoPayClient_cancel을_호출한다() {
        Payment payment = Payment.approved(order(), null, PaymentMethod.KAKAOPAY, 10000, null, "T123", "idem-1");

        paymentService.cancel(payment);

        verify(kakaoPayClient).cancel("T123", 10000);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void CARD_환불은_한도를_복구하고_결제상태를_REFUNDED로_바꾼다() {
        Payment payment = Payment.approved(order(), 55L, PaymentMethod.CARD, 10000, "AP-1", null, "idem-1");
        when(cardClient.restore(anyLong(), any())).thenReturn(new CardOperationResult(true, null));

        paymentService.refund(payment);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void CARD_환불시_한도복구가_실패하면_예외를_던지고_상태를_바꾸지_않는다() {
        Payment payment = Payment.approved(order(), 55L, PaymentMethod.CARD, 10000, "AP-1", null, "idem-1");
        when(cardClient.restore(anyLong(), any())).thenReturn(new CardOperationResult(false, "member-service 응답 없음"));

        assertThatThrownBy(() -> paymentService.refund(payment)).isInstanceOf(CardRestoreFailedException.class);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void KAKAOPAY_환불은_KakaoPayClient_cancel을_호출하고_REFUNDED로_바꾼다() {
        Payment payment = Payment.approved(order(), null, PaymentMethod.KAKAOPAY, 10000, null, "T123", "idem-1");

        paymentService.refund(payment);

        verify(kakaoPayClient).cancel("T123", 10000);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }
}
