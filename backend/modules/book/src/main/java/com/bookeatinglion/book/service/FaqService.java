package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.Faq;
import com.bookeatinglion.book.dto.FaqCreateRequest;
import com.bookeatinglion.book.dto.FaqResponse;
import com.bookeatinglion.book.dto.FaqUpdateRequest;
import com.bookeatinglion.book.exception.FaqNotFoundException;
import com.bookeatinglion.book.repository.FaqRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {

    private final FaqRepository faqRepository;

    public List<FaqResponse> getActiveFaqs(String category) {
        List<Faq> faqs = category == null || category.isBlank()
                ? faqRepository.findByActiveTrueOrderBySortOrderAscFaqIdAsc()
                : faqRepository.findByActiveTrueAndCategoryOrderBySortOrderAscFaqIdAsc(category);
        return faqs.stream().map(FaqResponse::from).toList();
    }

    public List<FaqResponse> getAdminFaqs(String category) {
        List<Faq> faqs = category == null || category.isBlank()
                ? faqRepository.findAllByOrderBySortOrderAscFaqIdAsc()
                : faqRepository.findByCategoryOrderBySortOrderAscFaqIdAsc(category);
        return faqs.stream().map(FaqResponse::from).toList();
    }

    @Transactional
    public FaqResponse create(FaqCreateRequest request) {
        Faq faq = Faq.builder()
                .category(request.category())
                .question(request.question())
                .answer(request.answer())
                .sortOrder(request.sortOrder())
                .active(request.active())
                .build();
        return FaqResponse.from(faqRepository.save(faq));
    }

    @Transactional
    public FaqResponse update(Long faqId, FaqUpdateRequest request) {
        Faq faq = getFaq(faqId);
        faq.update(request.category(), request.question(), request.answer(), request.sortOrder(), request.active());
        return FaqResponse.from(faq);
    }

    @Transactional
    public void delete(Long faqId) {
        getFaq(faqId).deactivate();
    }

    private Faq getFaq(Long faqId) {
        return faqRepository.findById(faqId).orElseThrow(() -> new FaqNotFoundException(faqId));
    }
}
