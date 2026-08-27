package com.bookeatinglion.book.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.dto.EpubUploadUrlResponse;
import com.bookeatinglion.book.service.AdminBookService;
import com.bookeatinglion.book.service.EbookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

// AdminBookController의 기존 CRUD는 AdminBookServiceTest(서비스 레벨)로 커버돼 있다 — 여기서는
// 이번에 새로 추가한 EPUB 업로드 URL 발급 엔드포인트만 검증한다.
@WebMvcTest(controllers = AdminBookController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = BookModuleTestApplication.class)
class AdminBookControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    AdminBookService adminBookService;

    @MockBean
    EbookService ebookService;

    @Test
    void EPUB_업로드_URL을_발급한다() throws Exception {
        when(ebookService.issueUploadUrl(any()))
                .thenReturn(new EpubUploadUrlResponse(
                        "https://signed.example/upload",
                        "epubs/uuid_alice.epub",
                        OffsetDateTime.now().plusMinutes(10)));

        mockMvc.perform(post("/api/catalog/admin/books/epub-upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FileNameRequest("alice.epub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.epubS3Key").value("epubs/uuid_alice.epub"));
    }

    @Test
    void 파일명이_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/catalog/admin/books/epub-upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FileNameRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 기존_EPUB_전체_재인제스트를_요청한다() throws Exception {
        when(adminBookService.reindexEbooks()).thenReturn(2);

        mockMvc.perform(post("/api/catalog/admin/books/ingest-index/rebuild"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
    }

    private record FileNameRequest(String fileName) {}
}
