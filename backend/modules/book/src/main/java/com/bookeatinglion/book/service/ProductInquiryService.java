package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.InquiryStatus;
import com.bookeatinglion.book.domain.ProductInquiry;
import com.bookeatinglion.book.dto.InquiryAnswerRequest;
import com.bookeatinglion.book.dto.InquiryCreateRequest;
import com.bookeatinglion.book.dto.InquiryResponse;
import com.bookeatinglion.book.dto.InquiryUpdateRequest;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.InquiryAccessDeniedException;
import com.bookeatinglion.book.exception.InquiryNotFoundException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.ProductInquiryRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductInquiryService {

    private final ProductInquiryRepository inquiryRepository;
    private final BookRepository bookRepository;

    public Page<InquiryResponse> getBookInquiries(Long bookId, String memberId, Pageable pageable) {
        if (bookRepository.findByBookIdAndIsDeletedFalse(bookId).isEmpty()) {
            throw new BookNotFoundException(bookId);
        }
        String viewerId = memberId == null ? "" : memberId;
        return inquiryRepository.findVisibleByBookId(bookId, viewerId, pageable).map(InquiryResponse::from);
    }

    @Transactional
    public InquiryResponse create(Long bookId, String memberId, InquiryCreateRequest request) {
        Book book = bookRepository
                .findByBookIdAndIsDeletedFalse(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        ProductInquiry inquiry = ProductInquiry.builder()
                .book(book)
                .memberId(memberId)
                .title(request.title())
                .content(request.content())
                .privateInquiry(request.privateInquiry())
                .build();
        return InquiryResponse.from(inquiryRepository.save(inquiry));
    }

    @Transactional
    public InquiryResponse update(Long inquiryId, String memberId, InquiryUpdateRequest request) {
        ProductInquiry inquiry = getActiveInquiry(inquiryId);
        requireOwner(inquiry, memberId);
        if (inquiry.getBook().isDeleted()) {
            throw new BookNotFoundException(inquiry.getBook().getBookId());
        }
        inquiry.update(request.title(), request.content(), request.privateInquiry());
        return InquiryResponse.from(inquiry);
    }

    @Transactional
    public void delete(Long inquiryId, String memberId) {
        ProductInquiry inquiry = getActiveInquiry(inquiryId);
        requireOwner(inquiry, memberId);
        inquiry.delete(LocalDateTime.now());
    }

    public Page<InquiryResponse> getAdminInquiries(Long bookId, InquiryStatus status, Pageable pageable) {
        if (bookId != null && !bookRepository.existsById(bookId)) {
            throw new BookNotFoundException(bookId);
        }
        return inquiryRepository.findForAdmin(bookId, status, pageable).map(InquiryResponse::from);
    }

    @Transactional
    public InquiryResponse answer(Long inquiryId, String adminId, InquiryAnswerRequest request) {
        ProductInquiry inquiry = getActiveInquiry(inquiryId);
        inquiry.answer(request.answer(), adminId, LocalDateTime.now());
        return InquiryResponse.from(inquiry);
    }

    private ProductInquiry getActiveInquiry(Long inquiryId) {
        ProductInquiry inquiry =
                inquiryRepository.findById(inquiryId).orElseThrow(() -> new InquiryNotFoundException(inquiryId));
        if (inquiry.isDeleted()) {
            throw new InquiryNotFoundException(inquiryId);
        }
        return inquiry;
    }

    private void requireOwner(ProductInquiry inquiry, String memberId) {
        if (!inquiry.getMemberId().equals(memberId)) {
            throw new InquiryAccessDeniedException(inquiry.getInquiryId(), memberId);
        }
    }
}
