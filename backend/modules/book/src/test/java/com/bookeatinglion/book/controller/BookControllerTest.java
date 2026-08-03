package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.BookDetailResponse;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.dto.BookSynopsisDetailResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.service.BookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BookController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = BookModuleTestApplication.class)
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookService bookService;

    private BookSummaryResponse summary(Long id, String title) {
        return new BookSummaryResponse(id, title, "저자", 10000, "cover.jpg", "소설", SaleStatus.ON_SALE);
    }

    @Test
    void 목록_조회는_200과_데이터를_반환한다() throws Exception {
        when(bookService.getBooks(eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(summary(1L, "책1")), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].title").value("책1"));
    }

    @Test
    void 검색은_200과_데이터를_반환한다() throws Exception {
        when(bookService.search(eq("스프링"), any()))
                .thenReturn(new PageImpl<>(List.of(summary(1L, "스프링 입문")), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/books/search").param("q", "스프링"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("스프링 입문"));
    }

    @Test
    void 베스트셀러는_200과_리스트를_반환한다() throws Exception {
        when(bookService.getBestsellers(anyInt())).thenReturn(List.of(summary(1L, "베스트셀러책")));

        mockMvc.perform(get("/api/books/bestsellers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("베스트셀러책"));
    }

    @Test
    void 신간은_200과_리스트를_반환한다() throws Exception {
        when(bookService.getNewReleases(anyInt())).thenReturn(List.of(summary(1L, "신간책")));

        mockMvc.perform(get("/api/books/new-releases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("신간책"));
    }

    @Test
    void 상세_조회는_200과_데이터를_반환한다() throws Exception {
        BookDetailResponse detail = new BookDetailResponse(
                1L, "상세책", "저자", "출판사", "9791100000001", "소설", 10000, 5,
                "cover.jpg", "설명", SaleStatus.ON_SALE, LocalDate.of(2026, 1, 1),
                LocalDateTime.now(), LocalDateTime.now());
        when(bookService.getBook(1L)).thenReturn(detail);

        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("상세책"));
    }

    @Test
    void 존재하지_않는_책_상세조회는_404를_반환한다() throws Exception {
        when(bookService.getBook(999L)).thenThrow(new BookNotFoundException(999L));

        mockMvc.perform(get("/api/books/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 상세줄거리_조회는_200과_데이터를_반환한다() throws Exception {
        when(bookService.getSynopsisDetail(1L))
                .thenReturn(new BookSynopsisDetailResponse(1L, "책제목", "상세 줄거리 본문"));

        mockMvc.perform(get("/api/books/1/synopsis/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detailedSynopsis").value("상세 줄거리 본문"));
    }

    @Test
    void 존재하지_않는_책의_상세줄거리_조회는_404를_반환한다() throws Exception {
        when(bookService.getSynopsisDetail(999L)).thenThrow(new BookNotFoundException(999L));

        mockMvc.perform(get("/api/books/999/synopsis/detail"))
                .andExpect(status().isNotFound());
    }
}
