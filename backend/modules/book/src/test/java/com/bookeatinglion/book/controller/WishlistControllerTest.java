package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.service.WishlistService;
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

    @Test
    void 찜하기는_200을_반환한다() throws Exception {
        mockMvc.perform(post("/api/wishlist/1").header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(wishlistService, times(1)).addWishlist(1L, 1L);
    }

    @Test
    void 존재하지_않는_책_찜하기는_404를_반환한다() throws Exception {
        doThrow(new BookNotFoundException(999L)).when(wishlistService).addWishlist(999L, 1L);

        mockMvc.perform(post("/api/wishlist/999").header("X-Member-Id", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 찜_삭제는_200을_반환한다() throws Exception {
        mockMvc.perform(delete("/api/wishlist/1").header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(wishlistService, times(1)).removeWishlist(1L, 1L);
    }
}
