package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.ReviewPermission;
import com.bookeatinglion.book.domain.ReviewPermissionId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewPermissionRepository extends JpaRepository<ReviewPermission, ReviewPermissionId> {

    /**
     * 아직 쓰지 않은 권한 1건. 리뷰 작성은 이 로컬 조회 하나로 끝난다 — 네트워크 홉 0.
     */
    Optional<ReviewPermission> findFirstByIdMemberIdAndBookIdAndUsedAtIsNull(String memberId, Long bookId);

    List<ReviewPermission> findByIdMemberId(String memberId);

    /** eBook 열람 시 "이 회원이 이 책을 구매 확정했는가"를 확인하는 용도. usedAt 여부는 무관하다(리뷰 소진과 무관). */
    boolean existsByIdMemberIdAndBookId(String memberId, Long bookId);
}
