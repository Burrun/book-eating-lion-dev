package com.bookeatinglion.book.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.dto.FaqResponse;
import com.bookeatinglion.book.service.FaqService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {FaqController.class, AdminFaqController.class})
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = BookModuleTestApplication.class)
class FaqControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean FaqService faqService;

    @Test
    void 사용자_조회와_관리자_CRUD_경로가_동작한다() throws Exception {
        FaqResponse response = new FaqResponse(1L, "ORDER", "배송은?", "이틀입니다.", 1, true, null, null);
        when(faqService.getActiveFaqs(any())).thenReturn(List.of(response));
        when(faqService.getAdminFaqs(any())).thenReturn(List.of(response));
        when(faqService.create(any())).thenReturn(response);
        when(faqService.update(any(), any())).thenReturn(response);
        WriteRequest body = new WriteRequest("ORDER", "배송은?", "이틀입니다.", 1, true);

        mockMvc.perform(get("/api/catalog/faqs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].question").value("배송은?"));
        mockMvc.perform(get("/api/catalog/admin/faqs")).andExpect(status().isOk());
        mockMvc.perform(post("/api/catalog/admin/faqs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/catalog/admin/faqs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/catalog/admin/faqs/1")).andExpect(status().isOk());
    }

    private record WriteRequest(String category, String question, String answer, int sortOrder, boolean active) {}
}
