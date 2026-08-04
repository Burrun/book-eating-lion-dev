package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.dto.ReviewResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.ReviewAccessDeniedException;
import com.bookeatinglion.book.exception.ReviewNotFoundException;
import com.bookeatinglion.book.service.ReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    private ReviewResponse response(Long id) {
        return new ReviewResponse(id, 1L, 1L, 5, "좋아요", LocalDateTime.now());
    }

    @Test
    void 리뷰_목록_조회는_200과_데이터를_반환한다() throws Exception {
        when(reviewService.getReviews(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(response(100L)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/books/1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].content").value("좋아요"));
    }

    @Test
    void 존재하지_않는_책의_리뷰_목록_조회는_404를_반환한다() throws Exception {
        when(reviewService.getReviews(eq(999L), any())).thenThrow(new BookNotFoundException(999L));

        mockMvc.perform(get("/api/books/999/reviews"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 리뷰_생성은_201과_데이터를_반환한다() throws Exception {
        when(reviewService.createReview(eq(1L), eq(1L), any())).thenReturn(response(100L));

        mockMvc.perform(post("/api/books/1/reviews")
                        .header("X-Member-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TestReviewRequest(5, "좋아요"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("좋아요"));
    }

    @Test
    void 평점_범위를_벗어나면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/books/1/reviews")
                        .header("X-Member-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TestReviewRequest(6, "좋아요"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 리뷰_삭제는_200을_반환한다() throws Exception {
        mockMvc.perform(delete("/api/reviews/100").header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 존재하지_않는_리뷰_삭제는_404를_반환한다() throws Exception {
        org.mockito.Mockito.doThrow(new ReviewNotFoundException(999L))
                .when(reviewService).deleteReview(999L, 1L);

        mockMvc.perform(delete("/api/reviews/999").header("X-Member-Id", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 작성자가_아니면_리뷰_삭제는_403을_반환한다() throws Exception {
        org.mockito.Mockito.doThrow(new ReviewAccessDeniedException(100L, 2L))
                .when(reviewService).deleteReview(100L, 2L);

        mockMvc.perform(delete("/api/reviews/100").header("X-Member-Id", "2"))
                .andExpect(status().isForbidden());
    }

    private record TestReviewRequest(int rating, String content) {
    }
}
