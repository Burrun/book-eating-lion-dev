package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.dto.ReadingProgressResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.ReadingProgressService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ReadingProgressController.class, BookExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = BookModuleTestApplication.class)
class ReadingProgressControllerTest {

    private static final String MEMBER_SUB = "member-sub-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReadingProgressService readingProgressService;

    @MockBean
    private CatalogMemberIdentity memberIdentity;

    @Test
    void 위치_저장은_200과_저장된_값을_반환한다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn(MEMBER_SUB);
        when(readingProgressService.saveProgress(eq(1L), eq(MEMBER_SUB), any()))
                .thenReturn(new ReadingProgressResponse(1L, "epubcfi(/6/4)", 55, LocalDateTime.now()));

        mockMvc.perform(put("/api/catalog/books/1/reading-progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cfi\":\"epubcfi(/6/4)\",\"percentage\":55}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cfi").value("epubcfi(/6/4)"))
                .andExpect(jsonPath("$.data.percentage").value(55));
    }

    @Test
    void cfi가_없으면_400을_반환한다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn(MEMBER_SUB);

        mockMvc.perform(put("/api/catalog/books/1/reading-progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"percentage\":55}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 존재하지_않는_책에_위치_저장은_404를_반환한다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn(MEMBER_SUB);
        when(readingProgressService.saveProgress(eq(999L), eq(MEMBER_SUB), any()))
                .thenThrow(new BookNotFoundException(999L));

        mockMvc.perform(put("/api/catalog/books/999/reading-progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cfi\":\"epubcfi(/6/4)\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 기록_없는_책_조회는_200과_null_데이터를_반환한다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn(MEMBER_SUB);
        when(readingProgressService.getProgress(1L, MEMBER_SUB)).thenReturn(null);

        mockMvc.perform(get("/api/catalog/books/1/reading-progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 기록_있는_책_조회는_저장된_값을_반환한다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn(MEMBER_SUB);
        when(readingProgressService.getProgress(1L, MEMBER_SUB))
                .thenReturn(new ReadingProgressResponse(1L, "epubcfi(/6/4)", 55, LocalDateTime.now()));

        mockMvc.perform(get("/api/catalog/books/1/reading-progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.percentage").value(55));
    }
}
