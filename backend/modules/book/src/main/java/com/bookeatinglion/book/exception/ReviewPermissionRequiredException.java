package com.bookeatinglion.book.exception;

/**
 * 구매 확정 이력(=사전 발급된 review_permissions)이 없어 리뷰를 쓸 수 없는 경우.
 *
 * 참고 — 이벤트가 유실되면 실제로 구매한 사용자도 여기로 떨어진다.
 * 계획서 Phase 2 부록은 이때만 order-service 를 동기 확인하는 fallback 을
 * 예외 경로로 두라고 권한다. 현재는 미구현이며, 한계로 명시해 둔다.
 */
public class ReviewPermissionRequiredException extends RuntimeException {

    private final BookErrorCode errorCode = BookErrorCode.REVIEW_PERMISSION_REQUIRED;

    public ReviewPermissionRequiredException(Long memberId, Long bookId) {
        super("구매 확정 이력이 없어 리뷰를 작성할 수 없습니다: memberId=" + memberId + ", bookId=" + bookId);
    }

    public BookErrorCode getErrorCode() {
        return errorCode;
    }
}
