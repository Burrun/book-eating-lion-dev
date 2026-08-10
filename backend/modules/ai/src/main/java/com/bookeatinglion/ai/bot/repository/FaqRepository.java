package com.bookeatinglion.ai.bot.repository;

import com.bookeatinglion.ai.bot.domain.Faq;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    List<Faq> findAllByOrderBySortOrderAsc();
}
