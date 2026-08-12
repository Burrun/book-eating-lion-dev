package com.bookeatinglion.book.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.dto.ReviewResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.ReviewAccessDeniedException;
import com.bookeatinglion.book.exception.ReviewNotFoundException;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.ReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = BookModuleTestApplication.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReviewService reviewService;

    @MockBean
    private CatalogMemberIdentity memberIdentity;

    private ReviewResponse response(Long id) {
        return new ReviewResponse(id, 1L, "member-1", "테스트유저", 5, "좋아요", LocalDateTime.now());
    }

    @Test
    void 리뷰_목록_조회는_200과_데이터를_반환한다() throws Exception {
        when(reviewService.getReviews(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(response(100L)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/catalog/books/1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].content").value("좋아요"));
    }

    @Test
    void 존재하지_않는_책의_리뷰_목록_조회는_404를_반환한다() throws Exception {
        when(reviewService.getReviews(eq(999L), any())).thenThrow(new BookNotFoundException(999L));

        mockMvc.perform(get("/api/catalog/books/999/reviews")).andExpect(status().isNotFound());
    }

    @Test
    void 리뷰_생성은_201과_데이터를_반환한다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn("member-1");
        when(reviewService.createReview(eq(1L), eq("member-1"), any())).thenReturn(response(100L));

        mockMvc.perform(post("/api/catalog/books/1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TestReviewRequest(5, "좋아요"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("좋아요"));
    }

    @Test
    void 평점_범위를_벗어나면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/catalog/books/1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TestReviewRequest(6, "좋아요"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 리뷰_삭제는_200을_반환한다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn("member-1");
        mockMvc.perform(delete("/api/catalog/reviews/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 리뷰_수정은_200과_수정된_데이터를_반환한다() throws Exception {
        ReviewResponse updated = new ReviewResponse(100L, 1L, "member-1", "테스트유저", 4, "수정했어요", LocalDateTime.now());
        when(memberIdentity.requiredMemberId()).thenReturn("member-1");
        when(reviewService.updateReview(eq(100L), eq("member-1"), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/catalog/reviews/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TestReviewRequest(4, "수정했어요"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(4))
                .andExpect(jsonPath("$.data.content").value("수정했어요"));
    }

    @Test
    void 존재하지_않는_리뷰_삭제는_404를_반환한다() throws Exception {
        org.mockito.Mockito.doThrow(new ReviewNotFoundException(999L))
                .when(reviewService)
                .deleteReview(999L, "member-1");
        when(memberIdentity.requiredMemberId()).thenReturn("member-1");

        mockMvc.perform(delete("/api/catalog/reviews/999")).andExpect(status().isNotFound());
    }

    @Test
    void 작성자가_아니면_리뷰_삭제는_403을_반환한다() throws Exception {
        org.mockito.Mockito.doThrow(new ReviewAccessDeniedException(100L, "member-2"))
                .when(reviewService)
                .deleteReview(100L, "member-2");
        when(memberIdentity.requiredMemberId()).thenReturn("member-2");

        mockMvc.perform(delete("/api/catalog/reviews/100")).andExpect(status().isForbidden());
    }

    private record TestReviewRequest(int rating, String content) {}
}
