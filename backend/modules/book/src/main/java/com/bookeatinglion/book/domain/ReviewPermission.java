package com.bookeatinglion.book.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구매 확정 시점에 order-service 가 미리 넘겨준 리뷰 작성 권한.
 *
 * 이 테이블의 존재 이유는 하나다 — 리뷰 작성 시 order-service 에 묻지 않기 위해서.
 * 동기로 물으면 리뷰 작성이 결제 서비스 가용성에 종속되고, "장애가 결제로
 * 전이되지 않는다"는 프로젝트 명분과 정반대로 결제 장애가 리뷰로 전이된다.
 *
 * 담긴 값은 전부 구매 확정 시점의 스냅샷이라 원본이 바뀌어도 갱신하지 않는다.
 * 동기화가 불필요한 게 아니라, 동기화하면 틀린 동작이 된다(안내서[R1]의 스냅샷 예외).
 */
@Entity
@Table(name = "review_permissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewPermission {

    @EmbeddedId
    private ReviewPermissionId id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    /** 작성자 표시용 스냅샷. 사용자가 닉네임을 바꿔도 과거 리뷰는 당시 이름으로 남는다. */
    private String nickname;

    @Column(nullable = false)
    private LocalDateTime grantedAt;

    /** 1건당 1리뷰 강제. 소진되면 채워진다. */
    private LocalDateTime usedAt;

    public ReviewPermission(Long memberId, Long orderItemId, Long bookId, String nickname, LocalDateTime grantedAt) {
        this.id = new ReviewPermissionId(memberId, orderItemId);
        this.bookId = bookId;
        this.nickname = nickname;
        this.grantedAt = grantedAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed(LocalDateTime now) {
        this.usedAt = now;
    }

    public void restore() {
        this.usedAt = null;
    }
}
