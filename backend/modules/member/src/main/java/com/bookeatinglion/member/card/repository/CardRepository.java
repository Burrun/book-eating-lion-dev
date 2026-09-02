package com.bookeatinglion.member.card.repository;

import com.bookeatinglion.member.card.domain.Card;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, Long> {

    List<Card> findByMemberSub(String memberSub);
}
