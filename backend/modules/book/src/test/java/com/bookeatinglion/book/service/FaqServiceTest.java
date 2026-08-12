package com.bookeatinglion.book.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.book.domain.Faq;
import com.bookeatinglion.book.dto.FaqCreateRequest;
import com.bookeatinglion.book.repository.FaqRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FaqServiceTest {

    @Mock
    FaqRepository faqRepository;

    @InjectMocks
    FaqService faqService;

    @Test
    void 사용자에게는_활성_FAQ만_조회한다() {
        Faq active = faq(true);
        when(faqRepository.findByActiveTrueOrderBySortOrderAscFaqIdAsc()).thenReturn(List.of(active));

        var result = faqService.getActiveFaqs(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).active()).isTrue();
        verify(faqRepository, never()).findAllByOrderBySortOrderAscFaqIdAsc();
    }

    @Test
    void 관리자가_FAQ를_등록한다() {
        when(faqRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = faqService.create(new FaqCreateRequest("ORDER", "배송은?", "이틀입니다.", 1, true));

        assertThat(result.category()).isEqualTo("ORDER");
        assertThat(result.active()).isTrue();
    }

    @Test
    void FAQ_삭제는_비활성화로_처리한다() {
        Faq faq = faq(true);
        when(faqRepository.findById(1L)).thenReturn(Optional.of(faq));

        faqService.delete(1L);

        assertThat(faq.isActive()).isFalse();
        verify(faqRepository, never()).delete(any());
    }

    private Faq faq(boolean active) {
        return Faq.builder()
                .category("ORDER")
                .question("배송은?")
                .answer("이틀입니다.")
                .sortOrder(1)
                .active(active)
                .build();
    }
}
