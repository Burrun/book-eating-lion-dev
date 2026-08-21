package com.bookeatinglion.book.repository;

import com.bookeatinglion.book.domain.BookMemo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookMemoRepository extends JpaRepository<BookMemo, Long> {

    Optional<BookMemo> findByMemberSubAndBook_BookId(String memberSub, Long bookId);

    /** 아직 안 먹인 내 메모만 — LionFeedingCard 드래그 목록. */
    List<BookMemo> findByMemberSubAndFedAtIsNullOrderByUpdatedAtDesc(String memberSub);

    /** 이미 사자에게 먹인 내 메모만 — "사자에게 물어보기" 패널의 "내가 먹인 요약 메모" 목록. */
    List<BookMemo> findByMemberSubAndFedAtIsNotNullOrderByFedAtDesc(String memberSub);
}
