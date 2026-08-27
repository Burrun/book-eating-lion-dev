package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.ReviewPermission;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.dto.EbookAccessResponse;
import com.bookeatinglion.book.dto.EpubUploadUrlResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.EbookOwnershipRequiredException;
import com.bookeatinglion.book.port.EbookStoragePort;
import com.bookeatinglion.book.port.MemberSubscriptionPort;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.ReviewPermissionRepository;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EbookService {

    private final BookRepository bookRepository;
    private final ReviewPermissionRepository reviewPermissionRepository;
    private final MemberSubscriptionPort memberSubscriptionPort;
    private final EbookStoragePort ebookStoragePort;
    private final Duration readUrlValidity;
    private final Duration uploadUrlValidity;

    public EbookService(
            BookRepository bookRepository,
            ReviewPermissionRepository reviewPermissionRepository,
            MemberSubscriptionPort memberSubscriptionPort,
            EbookStoragePort ebookStoragePort,
            @Value("${ebooks.read-url-validity:PT10M}") Duration readUrlValidity,
            @Value("${ebooks.upload-url-validity:PT10M}") Duration uploadUrlValidity) {
        this.bookRepository = bookRepository;
        this.reviewPermissionRepository = reviewPermissionRepository;
        this.memberSubscriptionPort = memberSubscriptionPort;
        this.ebookStoragePort = ebookStoragePort;
        this.readUrlValidity = readUrlValidity;
        this.uploadUrlValidity = uploadUrlValidity;
    }

    /**
     * eBook 미지원 도서는 구매/구독 여부와 무관하게 미지원으로 응답한다. 지원 도서는
     * review_permissions에 구매 확정 기록이 있거나 구독 중인 회원에게만 presigned URL을
     * 발급한다 — 둘 다 아니면 403(EbookOwnershipRequiredException). 구매 확정 여부를 먼저
     * 확인해 구매자는 member-service 호출 없이 바로 통과한다.
     */
    public EbookAccessResponse getAccess(Long bookId, String memberId) {
        Book book = bookRepository
                .findByBookIdAndIsDeletedFalse(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        if (!book.isEbookAvailable()) {
            return EbookAccessResponse.unavailable(bookId);
        }
        boolean purchased = reviewPermissionRepository.existsByIdMemberIdAndBookId(memberId, bookId);
        if (!purchased && !memberSubscriptionPort.isSubscribed(memberId)) {
            throw new EbookOwnershipRequiredException(bookId);
        }
        EbookStoragePort.ReadUrl readUrl = ebookStoragePort.createReadUrl(book.getEpubS3Key(), readUrlValidity);
        return new EbookAccessResponse(bookId, true, readUrl.url(), readUrl.expiresAt());
    }

    /** 신간 등록 화면에서 EPUB 파일을 고르면 바로 호출한다 — 도서가 아직 없어도 된다. */
    public EpubUploadUrlResponse issueUploadUrl(String fileName) {
        EbookStoragePort.UploadUrl uploadUrl = ebookStoragePort.createUploadUrl(fileName, uploadUrlValidity);
        return new EpubUploadUrlResponse(uploadUrl.url(), uploadUrl.key(), uploadUrl.expiresAt());
    }

    /**
     * 내 이북 보관함: 구매 확정(review_permissions) 도서 ∪ (구독 중이면) eBook 보유 도서 전체.
     * 구독 중일 때는 구매한 eBook이 항상 "eBook 보유 도서 전체"의 부분집합이라 합집합이 후자와
     * 같아진다 — 그래서 별도로 합치지 않고 바로 전체 목록을 반환한다.
     */
    public List<BookSummaryResponse> getMyEbooks(String memberId) {
        if (memberSubscriptionPort.isSubscribed(memberId)) {
            return bookRepository.findByEpubS3KeyIsNotNullAndIsDeletedFalse().stream()
                    .map(BookSummaryResponse::from)
                    .toList();
        }
        List<Long> purchasedBookIds = reviewPermissionRepository.findByIdMemberId(memberId).stream()
                .map(ReviewPermission::getBookId)
                .distinct()
                .toList();
        if (purchasedBookIds.isEmpty()) {
            return List.of();
        }
        return bookRepository.findByBookIdInAndEpubS3KeyIsNotNullAndIsDeletedFalse(purchasedBookIds).stream()
                .map(BookSummaryResponse::from)
                .toList();
    }
}
