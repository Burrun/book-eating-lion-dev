package com.bookeatinglion.common.event;

import java.util.Map;

/**
 * order-service → catalog-service. 구매 확정 시 리뷰 작성 권한을 미리 발급한다.
 *
 * 이 이벤트가 존재하는 이유는 리뷰 작성이 order-service 가용성에 종속되지 않게
 * 하기 위해서다(Phase 2 부록). 동기 호출로 바꾸면 "장애가 결제로 전이되지 않는다"는
 * 프로젝트 명분과 정확히 반대가 된다.
 *
 * 담긴 값은 전부 구매 확정 시점의 스냅샷이다. 원본이 바뀌어도 갱신하지 않는다.
 *
 * common 에 두는 이유: 발행측(order)과 소비측(catalog)이 같은 스키마를 봐야 하는데,
 * modules 끼리는 의존할 수 없기 때문이다(§7.2). 도메인 코드가 아니라 계약이다.
 */
public record ReviewPermissionGranted(
        String memberId, Long orderItemId, Long bookId, String nickname, String grantedAt) {

    public static final String STREAM_KEY = "events:review-permission-granted";

    public Map<String, String> toMap() {
        return Map.of(
                "memberId", String.valueOf(memberId),
                "orderItemId", String.valueOf(orderItemId),
                "bookId", String.valueOf(bookId),
                "nickname", nickname == null ? "" : nickname,
                "grantedAt", grantedAt);
    }

    public static ReviewPermissionGranted fromMap(Map<String, String> map) {
        String nickname = str(map, "nickname");
        return new ReviewPermissionGranted(
                str(map, "memberId"),
                Long.valueOf(str(map, "orderItemId")),
                Long.valueOf(str(map, "bookId")),
                nickname.isEmpty() ? null : nickname,
                str(map, "grantedAt"));
    }

    private static String str(Map<String, String> map, String key) {
        String value = map.get(key);
        return value == null ? "" : value;
    }
}
