package com.bookeatinglion.book.port;

/**
 * 구독 상태 조회 포트. 구독 회원은 개별 구매 여부와 무관하게 eBook 전체를 열람할 수 있다
 * (EbookService.getAccess/getMyEbooks가 이 규칙을 적용한다).
 *
 * 구현체는 modules 안에 두지 않는다 — Feign은 apps/catalog-api/client에 있고, modules/book은
 * 이 인터페이스만 안다(InventoryPort/MemberNotificationProfilePort와 같은 이유, §7.2).
 */
public interface MemberSubscriptionPort {

    boolean isSubscribed(String memberId);
}
