package com.bookeatinglion.ai.bot.repository;

import com.bookeatinglion.ai.bot.domain.Inquiry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    List<Inquiry> findByMemberIdOrderByCreatedAtDesc(Long memberId);
}
