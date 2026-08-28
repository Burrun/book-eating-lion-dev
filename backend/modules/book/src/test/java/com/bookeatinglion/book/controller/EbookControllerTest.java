package com.bookeatinglion.book.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.dto.EbookAccessResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.EbookOwnershipRequiredException;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.EbookService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {EbookController.class, BookExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = BookModuleTestApplication.class)
class EbookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EbookService ebookService;

    @MockBean
    private CatalogMemberIdentity memberIdentity;

    @Test
    void 구매한_회원은_200과_열람_URL을_받는다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn("member-1");
        OffsetDateTime expiresAt = OffsetDateTime.now();
        when(ebookService.getAccess(101L, "member-1"))
                .thenReturn(new EbookAccessResponse(101L, true, "https://signed.example/frankenstein", expiresAt));

        mockMvc.perform(get("/api/catalog/books/101/ebook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presignedUrl").value("https://signed.example/frankenstein"));
    }

    @Test
    void 구매하지_않은_회원은_403을_받는다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn("member-2");
        when(ebookService.getAccess(101L, "member-2")).thenThrow(new EbookOwnershipRequiredException(101L));

        mockMvc.perform(get("/api/catalog/books/101/ebook"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EBOOK_OWNERSHIP_REQUIRED"));
    }

    @Test
    void 존재하지_않는_도서는_404를_받는다() throws Exception {
        when(memberIdentity.requiredMemberId()).thenReturn("member-1");
        when(ebookService.getAccess(999L, "member-1")).thenThrow(new BookNotFoundException(999L));

        mockMvc.perform(get("/api/catalog/books/999/ebook")).andExpect(status().isNotFound());
    }
}
