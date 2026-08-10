package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.ReviewPermission;
import com.bookeatinglion.book.domain.ReviewPermissionId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewPermissionRepository extends JpaRepository<ReviewPermission, ReviewPermissionId> {

    /**
     * 아직 쓰지 않은 권한 1건. 리뷰 작성은 이 로컬 조회 하나로 끝난다 — 네트워크 홉 0.
     */
    Optional<ReviewPermission> findFirstByIdMemberIdAndBookIdAndUsedAtIsNull(Long memberId, Long bookId);
}
