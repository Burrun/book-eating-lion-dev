package com.bookeatinglion.order.lock;

import com.bookeatinglion.order.order.exception.OrderDomainException;
import com.bookeatinglion.order.order.exception.OrderErrorCode;
import java.util.List;

public class InventoryLockAcquisitionException extends OrderDomainException {

    public InventoryLockAcquisitionException(Long bookId) {
        super(OrderErrorCode.LOCK_ACQUISITION_FAILED, "재고 락 획득에 실패했습니다: " + bookId);
    }

    public InventoryLockAcquisitionException(List<Long> bookIds) {
        super(OrderErrorCode.LOCK_ACQUISITION_FAILED, "재고 락 획득이 중단됐습니다: " + bookIds);
    }
}
