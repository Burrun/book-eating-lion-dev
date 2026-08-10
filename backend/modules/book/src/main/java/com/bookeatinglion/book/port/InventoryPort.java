package com.bookeatinglion.book.port;

import java.util.List;
import java.util.Map;

/**
 * 재고 조회 포트. 재고 소유권이 order-service 로 넘어가면서 생긴 유일한
 * "catalog → order" 읽기 경로다(계획서 판단 ③).
 *
 * 구현체는 modules 안에 두지 않는다. Feign 은 apps/catalog-api/client 에 있고,
 * modules/book 은 이 인터페이스만 안다 — modules 끼리의 의존을 금지하는
 * §7.2 의존 규칙을 지키기 위해서다.
 *
 * 반드시 벌크다. 도서 20건을 렌더링할 때 호출이 20번 나가면 N+1 이 된다.
 */
public interface InventoryPort {

    /**
     * @return bookId → 재고 수량. order-service 가 응답하지 않으면 빈 맵이 온다(fallback).
     *         호출자는 값이 없는 경우를 "재고 정보 없음"으로 degrade 처리해야 하며,
     *         도서 정보 자체는 정상 노출한다.
     */
    Map<Long, Integer> stockByBookIds(List<Long> bookIds);
}
