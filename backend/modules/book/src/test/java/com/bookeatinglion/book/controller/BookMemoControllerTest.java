package com.bookeatinglion.book.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.dto.BookMemoResponse;
import com.bookeatinglion.book.dto.FeedableMemoResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.BookMemoService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {BookMemoController.class, BookExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = BookModuleTestApplication.class)
class BookMemoControllerTest {

    private static final String MEMBER_SUB = "member-sub-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookMemoService bookMemoService;

    @MockBean
    private CatalogMemberIdentity memberIdentity;

    @Test
    void 메모_저장은_200과_저장된_값을_반환한다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn(MEMBER_SUB);
        when(bookMemoService.upsertMemo(eq(1L), eq(MEMBER_SUB), eq("요약")))
                .thenReturn(new BookMemoResponse(1L, "요약", null, LocalDateTime.now()));

        mockMvc.perform(put("/api/catalog/books/1/memo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memoText\":\"요약\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memoText").value("요약"))
                .andExpect(jsonPath("$.data.fedAt").doesNotExist());
    }

    @Test
    void 빈_메모_저장은_400을_반환한다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn(MEMBER_SUB);

        mockMvc.perform(put("/api/catalog/books/1/memo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memoText\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 존재하지_않는_책에_메모_저장은_404를_반환한다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn(MEMBER_SUB);
        when(bookMemoService.upsertMemo(eq(999L), eq(MEMBER_SUB), eq("요약")))
                .thenThrow(new BookNotFoundException(999L));

        mockMvc.perform(put("/api/catalog/books/999/memo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memoText\":\"요약\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 메모_없는_책_조회는_200과_null_데이터를_반환한다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn(MEMBER_SUB);
        when(bookMemoService.getMemo(1L, MEMBER_SUB)).thenReturn(null);

        mockMvc.perform(get("/api/catalog/books/1/memo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 피더블_메모_목록을_반환한다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn(MEMBER_SUB);
        when(bookMemoService.listFeedableMemos(MEMBER_SUB))
                .thenReturn(List.of(new FeedableMemoResponse(1L, "책 제목", "요약")));

        mockMvc.perform(get("/api/catalog/members/me/memos/feedable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].bookId").value(1))
                .andExpect(jsonPath("$.data[0].bookTitle").value("책 제목"));
    }

    @Test
    void 먹인_메모_목록을_반환한다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn(MEMBER_SUB);
        when(bookMemoService.listFedMemos(MEMBER_SUB))
                .thenReturn(List.of(new com.bookeatinglion.book.dto.FedMemoResponse(1L, "책 제목", "요약")));

        mockMvc.perform(get("/api/catalog/members/me/memos/fed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].bookId").value(1))
                .andExpect(jsonPath("$.data[0].bookTitle").value("책 제목"));
    }

    @Test
    void 먹이기_완료_처리는_200을_반환한다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn(MEMBER_SUB);

        mockMvc.perform(patch("/api/catalog/books/1/memo/fed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
