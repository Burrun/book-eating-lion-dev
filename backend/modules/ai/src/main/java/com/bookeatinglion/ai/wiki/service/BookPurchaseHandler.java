package com.bookeatinglion.ai.wiki.service;

import com.bookeatinglion.ai.wiki.domain.PurchasedBook;
import com.bookeatinglion.ai.wiki.event.BookPurchaseEvent;
import com.bookeatinglion.ai.wiki.repository.PurchasedBookRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class BookPurchaseHandler {

    private static final Logger log = LoggerFactory.getLogger(BookPurchaseHandler.class);

    private final PurchasedBookRepository purchasedBookRepository;
    private final PurchasedBookCache purchasedBookCache;

    @Transactional
    public void handle(BookPurchaseEvent event) {
        boolean exists = purchasedBookRepository.existsById(new PurchasedBook.Key(event.memberId(), event.bookId()));
        if (!exists) {
            purchasedBookRepository.save(new PurchasedBook(event.memberId(), event.bookId(), LocalDateTime.now()));
            cacheAfterCommit(event.memberId(), event.bookId());
            log.info("구매 이벤트 처리 완료. memberId={}, bookId={}", event.memberId(), event.bookId());
        } else {
            log.info("이미 처리된 구매 이벤트. memberId={}, bookId={}", event.memberId(), event.bookId());
        }
    }

    private void cacheAfterCommit(String memberId, Long bookId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    purchasedBookCache.add(memberId, bookId);
                }
            });
        } else {
            purchasedBookCache.add(memberId, bookId);
        }
    }
}
