package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.dto.EbookAccessResponse;
import com.bookeatinglion.book.dto.EpubUploadUrlResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.port.EbookStoragePort;
import com.bookeatinglion.book.repository.BookRepository;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EbookService {

    private final BookRepository bookRepository;
    private final EbookStoragePort ebookStoragePort;
    private final Duration readUrlValidity;
    private final Duration uploadUrlValidity;

    public EbookService(
            BookRepository bookRepository,
            EbookStoragePort ebookStoragePort,
            @Value("${ebooks.read-url-validity:PT10M}") Duration readUrlValidity,
            @Value("${ebooks.upload-url-validity:PT10M}") Duration uploadUrlValidity) {
        this.bookRepository = bookRepository;
        this.ebookStoragePort = ebookStoragePort;
        this.readUrlValidity = readUrlValidity;
        this.uploadUrlValidity = uploadUrlValidity;
    }

    public EbookAccessResponse getAccess(Long bookId) {
        Book book = bookRepository
                .findByBookIdAndIsDeletedFalse(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        if (!book.isEbookAvailable()) {
            return EbookAccessResponse.unavailable(bookId);
        }
        EbookStoragePort.ReadUrl readUrl = ebookStoragePort.createReadUrl(book.getEpubS3Key(), readUrlValidity);
        return new EbookAccessResponse(bookId, true, readUrl.url(), readUrl.expiresAt());
    }

    /** 신간 등록 화면에서 EPUB 파일을 고르면 바로 호출한다 — 도서가 아직 없어도 된다. */
    public EpubUploadUrlResponse issueUploadUrl(String fileName) {
        EbookStoragePort.UploadUrl uploadUrl = ebookStoragePort.createUploadUrl(fileName, uploadUrlValidity);
        return new EpubUploadUrlResponse(uploadUrl.url(), uploadUrl.key(), uploadUrl.expiresAt());
    }
}
