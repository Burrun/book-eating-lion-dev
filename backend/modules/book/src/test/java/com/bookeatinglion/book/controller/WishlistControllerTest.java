package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.service.WishlistService;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WishlistController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = BookModuleTestApplication.class)
class WishlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WishlistService wishlistService;

    @MockBean
    private CatalogMemberIdentity memberIdentity;

    @Test
    void 찜하기는_200을_반환한다() throws Exception {
        org.mockito.Mockito.when(memberIdentity.requiredMemberId()).thenReturn("member-1");
        mockMvc.perform(post("/api/catalog/wishlist/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(wishlistService, times(1)).addWishlist(1L, "member-1");
    }

    @Test
    void 존재하지_않는_책_찜하기는_404를_반환한다() throws Exception {
        org.mockito.Mockito.when(memberIdentity.requiredMemberId()).thenReturn("member-1");
        doThrow(new BookNotFoundException(999L)).when(wishlistService).addWishlist(999L, "member-1");

        mockMvc.perform(post("/api/catalog/wishlist/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 찜_삭제는_200을_반환한다() throws Exception {
        org.mockito.Mockito.when(memberIdentity.requiredMemberId()).thenReturn("member-1");
        mockMvc.perform(delete("/api/catalog/wishlist/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(wishlistService, times(1)).removeWishlist(1L, "member-1");
    }
}
