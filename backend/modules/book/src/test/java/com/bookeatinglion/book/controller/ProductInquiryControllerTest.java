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
import com.bookeatinglion.book.domain.InquiryStatus;
import com.bookeatinglion.book.dto.InquiryResponse;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.ProductInquiryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {ProductInquiryController.class, AdminInquiryController.class})
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = BookModuleTestApplication.class)
class ProductInquiryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ProductInquiryService inquiryService;
    @MockBean CatalogMemberIdentity memberIdentity;

    @Test
    void 문의_조회_작성_수정_삭제_경로가_동작한다() throws Exception {
        InquiryResponse response = response();
        when(memberIdentity.requiredMemberId()).thenReturn("member-1");
        when(inquiryService.getBookInquiries(eq(1L), any(), any()))
                .thenReturn(new PageImpl<>(List.of(response)));
        when(inquiryService.create(eq(1L), eq("member-1"), any())).thenReturn(response);
        when(inquiryService.update(eq(10L), eq("member-1"), any())).thenReturn(response);

        mockMvc.perform(get("/api/catalog/books/1/inquiries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("배송 문의"));
        mockMvc.perform(post("/api/catalog/books/1/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WriteRequest("배송 문의", "언제 오나요?", true))))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/catalog/inquiries/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WriteRequest("배송 문의", "내일 오나요?", false))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/catalog/inquiries/10"))
                .andExpect(status().isOk());
    }

    @Test
    void 관리자_문의_목록과_답변_경로가_동작한다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn("admin-sub");
        when(inquiryService.getAdminInquiries(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(response())));
        when(inquiryService.answer(eq(10L), eq("admin-sub"), any())).thenReturn(response());

        mockMvc.perform(get("/api/catalog/admin/inquiries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].inquiryId").value(10));
        mockMvc.perform(patch("/api/catalog/admin/inquiries/10/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"내일 입고됩니다.\"}"))
                .andExpect(status().isOk());
    }

    private InquiryResponse response() {
        return new InquiryResponse(10L, 1L, "member-1", "배송 문의", "언제 오나요?", true,
                InquiryStatus.WAITING, null, null, null, false, null, null);
    }

    private record WriteRequest(String title, String content, boolean privateInquiry) {}
}
