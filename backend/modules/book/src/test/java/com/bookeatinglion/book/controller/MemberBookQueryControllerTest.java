package com.bookeatinglion.book.controller;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.BookSummaryResponse;
import com.bookeatinglion.book.service.RecentViewedBookService;
import com.bookeatinglion.book.service.WishlistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MemberBookQueryController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = BookModuleTestApplication.class)
class MemberBookQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WishlistService wishlistService;

    @MockBean
    private RecentViewedBookService recentViewedBookService;

    private BookSummaryResponse summary(Long id, String title) {
        return new BookSummaryResponse(id, title, "저자", 10000, "cover.jpg", "소설", SaleStatus.ON_SALE);
    }

    @Test
    void 내_찜_목록_조회는_200과_데이터를_반환한다() throws Exception {
        when(wishlistService.getMyWishlist(1L)).thenReturn(List.of(summary(1L, "찜한책")));

        mockMvc.perform(get("/api/members/me/wishlist").header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("찜한책"));
    }

    @Test
    void 내_최근_본_책_조회는_200과_데이터를_반환한다() throws Exception {
        when(recentViewedBookService.getMyRecentBooks(eq(1L), eq(20)))
                .thenReturn(List.of(summary(1L, "최근본책")));

        mockMvc.perform(get("/api/members/me/recent-books").header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("최근본책"));
    }

    @Test
    void 최근_본_책_조회시_limit을_지정할_수_있다() throws Exception {
        when(recentViewedBookService.getMyRecentBooks(eq(1L), eq(5)))
                .thenReturn(List.of(summary(1L, "최근본책")));

        mockMvc.perform(get("/api/members/me/recent-books")
                        .header("X-Member-Id", "1")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("최근본책"));
    }

    @Test
    void 회원_헤더가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/members/me/wishlist"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
