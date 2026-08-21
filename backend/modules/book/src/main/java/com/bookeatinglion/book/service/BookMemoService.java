package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.BookMemo;
import com.bookeatinglion.book.dto.BookMemoResponse;
import com.bookeatinglion.book.dto.FedMemoResponse;
import com.bookeatinglion.book.dto.FeedableMemoResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.repository.BookMemoRepository;
import com.bookeatinglion.book.repository.BookRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 완독 요약 메모. 책 하나당 1개만 존재하는 upsert 모양이다(ReadingProgressService와 동일 패턴).
 *
 * <p>먹이기(EXP/인덱싱) 자체는 ai-service 소유다 — 여기서는 텍스트 저장과 "먹였다는 표시
 * (fedAt)"만 관리한다. fedAt은 프론트가 ai-service feed 호출에 성공한 직후 {@link #markFed}로
 * 별도로 알려준다(두 서비스 간 동기 호출 없이 프론트가 오케스트레이션한다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookMemoService {

    private final BookMemoRepository bookMemoRepository;
    private final BookRepository bookRepository;

    @Transactional
    public BookMemoResponse upsertMemo(Long bookId, String memberSub, String memoText) {
        Book book = bookRepository
                .findByBookIdAndIsDeletedFalse(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        BookMemo memo = bookMemoRepository
                .findByMemberSubAndBook_BookId(memberSub, bookId)
                .map(existing -> {
                    existing.updateText(memoText);
                    return existing;
                })
                .orElseGet(() -> bookMemoRepository.save(BookMemo.builder()
                        .memberSub(memberSub)
                        .book(book)
                        .memoText(memoText)
                        .build()));
        return BookMemoResponse.from(memo);
    }

    /** 기록이 없으면 null을 반환한다 — 아직 메모를 안 쓴 책은 정상 상태라 예외가 아니다. */
    public BookMemoResponse getMemo(Long bookId, String memberSub) {
        if (!bookRepository.existsByBookIdAndIsDeletedFalse(bookId)) {
            throw new BookNotFoundException(bookId);
        }
        return bookMemoRepository
                .findByMemberSubAndBook_BookId(memberSub, bookId)
                .map(BookMemoResponse::from)
                .orElse(null);
    }

    public List<FeedableMemoResponse> listFeedableMemos(String memberSub) {
        return bookMemoRepository.findByMemberSubAndFedAtIsNullOrderByUpdatedAtDesc(memberSub).stream()
                .map(FeedableMemoResponse::from)
                .toList();
    }

    /** "사자에게 물어보기" 패널의 "내가 먹인 요약 메모" 목록 — 이미 먹인(fedAt 있는) 메모만. */
    public List<FedMemoResponse> listFedMemos(String memberSub) {
        return bookMemoRepository.findByMemberSubAndFedAtIsNotNullOrderByFedAtDesc(memberSub).stream()
                .map(FedMemoResponse::from)
                .toList();
    }

    /**
     * ai-service feed 호출이 성공한 뒤 프론트가 부른다. 멱등하다 — 이미 fedAt이 있어도
     * 다시 지금 시각으로 덮어쓸 뿐 오류가 아니다(재작성 후 재-feed 시나리오).
     */
    @Transactional
    public void markFed(Long bookId, String memberSub) {
        BookMemo memo = bookMemoRepository
                .findByMemberSubAndBook_BookId(memberSub, bookId)
                .orElseThrow(() -> new IllegalStateException(
                        "먹이기 완료 처리할 메모가 없습니다: memberSub=%s bookId=%d".formatted(memberSub, bookId)));
        memo.markFed(LocalDateTime.now());
    }
}
