package com.bookeatinglion.order.payment.dto;

import com.bookeatinglion.order.payment.domain.Payment;
import com.bookeatinglion.order.payment.domain.PaymentMethod;
import com.bookeatinglion.order.payment.domain.PaymentStatus;

public record PaymentView(
        Long paymentId,
        PaymentMethod paymentMethod,
        int amount,
        PaymentStatus paymentStatus,
        String approvalNumber,
        String pgTid) {

    public static PaymentView from(Payment payment) {
        return new PaymentView(
                payment.getId(),
                payment.getPaymentMethod(),
                payment.getAmount(),
                payment.getPaymentStatus(),
                payment.getApprovalNumber(),
                payment.getPgTid());
    }
}
