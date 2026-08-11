package com.bookeatinglion.order.payment.exception;

import com.bookeatinglion.order.order.exception.OrderDomainException;
import com.bookeatinglion.order.order.exception.OrderErrorCode;

public class PaymentDeclinedException extends OrderDomainException {

    public PaymentDeclinedException(String reason) {
        super(OrderErrorCode.PAYMENT_DECLINED, "결제가 거절되었습니다: " + reason);
    }
}
