package com.bookeatinglion.order.order.exception;

/**
 * inventory 에 해당 bookId 행 자체가 없을 때. 재고 0 과는 다른 사건이다 — 카탈로그엔
 * 있는데 재고가 등록되지 않은 상태이므로, 팔 물건이 떨어진 게 아니라 데이터가 빠진 것이다.
 * 둘을 같은 예외로 뭉치면 "재고가 부족합니다"만 보고 시드/등록 누락을 못 찾는다.
 */
public class InventoryNotFoundException extends OrderDomainException {

    public InventoryNotFoundException(Long bookId) {
        super(OrderErrorCode.INVENTORY_NOT_FOUND, "재고 정보가 등록되어 있지 않습니다: bookId=" + bookId);
    }
}
