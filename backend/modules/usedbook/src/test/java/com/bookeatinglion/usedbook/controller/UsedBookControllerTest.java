package com.bookeatinglion.usedbook.controller;

import com.bookeatinglion.s3.dto.PresignedUrlResponse;
import com.bookeatinglion.s3.service.S3PresignedUrlService;
import com.bookeatinglion.usedbook.UsedBookModuleTestApplication;
import com.bookeatinglion.usedbook.domain.UsedBookCondition;
import com.bookeatinglion.usedbook.domain.UsedBookStatus;
import com.bookeatinglion.usedbook.dto.UsedBookCreateRequest;
import com.bookeatinglion.usedbook.dto.UsedBookResponse;
import com.bookeatinglion.usedbook.dto.UsedBookSummaryResponse;
import com.bookeatinglion.usedbook.exception.UsedBookNotFoundException;
import com.bookeatinglion.usedbook.service.UsedBookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UsedBookController.class)
@ContextConfiguration(classes = UsedBookModuleTestApplication.class)
class UsedBookControllerTest {

    private static final String SUB = "seller-sub-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UsedBookService usedBookService;

    @MockBean
    private S3PresignedUrlService s3PresignedUrlService;

    private UsedBookResponse usedBookResponse() {
        return new UsedBookResponse(1L, SUB, "9791100000001", "스프링 입문", "저자", "출판사", "cover.jpg",
                10000, UsedBookCondition.GOOD, "설명", UsedBookStatus.ON_SALE,
                List.of("https://example.com/1.jpg"), LocalDateTime.now(), LocalDateTime.now());
    }

    private UsedBookSummaryResponse usedBookSummaryResponse() {
        return new UsedBookSummaryResponse(1L, "9791100000001", "스프링 입문", "cover.jpg",
                10000, UsedBookCondition.GOOD, UsedBookStatus.ON_SALE, LocalDateTime.now());
    }

    @Test
    void 매물을_등록하면_201과_데이터를_반환한다() throws Exception {
        UsedBookCreateRequest request = new UsedBookCreateRequest(
                "9791100000001", "스프링 입문", "저자", "출판사", "cover.jpg",
                10000, UsedBookCondition.GOOD, "설명", List.of("https://example.com/1.jpg"));
        when(usedBookService.createUsedBook(eq(SUB), any())).thenReturn(usedBookResponse());

        mockMvc.perform(post("/api/used-books")
                        .with(jwt().jwt(jwt -> jwt.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("스프링 입문"))
                .andExpect(jsonPath("$.data.sellerId").value(SUB));
    }

    @Test
    void 필수값이_없으면_매물_등록은_400을_반환한다() throws Exception {
        UsedBookCreateRequest invalid = new UsedBookCreateRequest(
                "", "", null, null, null, 0, null, null, List.of());

        mockMvc.perform(post("/api/used-books")
                        .with(jwt().jwt(jwt -> jwt.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void presigned_url_발급은_200과_데이터를_반환한다() throws Exception {
        when(s3PresignedUrlService.generatePresignedUrl(SUB, "cover.jpg"))
                .thenReturn(new PresignedUrlResponse("https://s3.example.com/upload", "https://s3.example.com/file", "used-books/" + SUB + "/uuid_cover.jpg"));

        mockMvc.perform(post("/api/used-books/presigned-url")
                        .with(jwt().jwt(jwt -> jwt.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"cover.jpg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl").value("https://s3.example.com/upload"))
                .andExpect(jsonPath("$.data.key").value("used-books/" + SUB + "/uuid_cover.jpg"));
    }

    @Test
    void 목록_조회는_200과_페이지_데이터를_반환한다() throws Exception {
        when(usedBookService.getUsedBooks(eq(null), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(usedBookSummaryResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/used-books").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].title").value("스프링 입문"));
    }

    @Test
    void isbn_필터로_목록을_조회한다() throws Exception {
        when(usedBookService.getUsedBooks(eq("9791100000001"), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(usedBookSummaryResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/used-books").param("isbn", "9791100000001").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].isbn").value("9791100000001"));
    }

    @Test
    void 상세_조회는_200과_데이터를_반환한다() throws Exception {
        when(usedBookService.getUsedBook(1L)).thenReturn(usedBookResponse());

        mockMvc.perform(get("/api/used-books/1").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.imageUrls[0]").value("https://example.com/1.jpg"));
    }

    @Test
    void 존재하지_않는_매물_조회는_404를_반환한다() throws Exception {
        when(usedBookService.getUsedBook(999L)).thenThrow(new UsedBookNotFoundException(999L));

        mockMvc.perform(get("/api/used-books/999").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
