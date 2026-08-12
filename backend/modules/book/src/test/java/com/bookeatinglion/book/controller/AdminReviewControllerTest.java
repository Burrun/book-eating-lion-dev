package com.bookeatinglion.book.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.dto.ReviewResponse;
import com.bookeatinglion.book.service.ReviewService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = BookModuleTestApplication.class)
class AdminReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewService reviewService;

    @Test
    void 관리자는_삭제된_도서를_포함해_리뷰를_조회한다() throws Exception {
        ReviewResponse review = new ReviewResponse(100L, 1L, "member-1", "테스트유저", 5, "좋아요", LocalDateTime.now());
        when(reviewService.getAdminReviews(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(review), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/catalog/admin/books/1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].content").value("좋아요"));
    }
}
