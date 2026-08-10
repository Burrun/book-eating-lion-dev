package com.bookeatinglion.ai.lion.repository;

import com.bookeatinglion.ai.lion.domain.Lion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LionRepository extends JpaRepository<Lion, Long> {

    Optional<Lion> findByMemberId(Long memberId);
}
