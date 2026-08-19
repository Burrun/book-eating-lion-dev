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
import com.bookeatinglion.book.dto.SubscriptionBannerResponse;
import com.bookeatinglion.book.exception.SubscriptionBannerNotFoundException;
import com.bookeatinglion.book.service.SubscriptionBannerService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@WebMvcTest(controllers = {SubscriptionBannerController.class, AdminSubscriptionBannerController.class})
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = BookModuleTestApplication.class)
class SubscriptionBannerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    SubscriptionBannerService subscriptionBannerService;

    private SubscriptionBannerResponse response() {
        return new SubscriptionBannerResponse(
                1L,
                "https://cdn.example.com/banner.png",
                "정기구독 첫 달 무료",
                "/subscribe",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 12, 31, 23, 59),
                1,
                true,
                null,
                null);
    }

    @Test
    void 공개_조회와_관리자_CRUD_경로가_동작한다() throws Exception {
        SubscriptionBannerResponse response = response();
        when(subscriptionBannerService.getCurrentlyActiveBanners()).thenReturn(List.of(response));
        when(subscriptionBannerService.getAdminBanners()).thenReturn(List.of(response));
        when(subscriptionBannerService.getBanner(1L)).thenReturn(response);
        when(subscriptionBannerService.create(any())).thenReturn(response);
        when(subscriptionBannerService.update(eq(1L), any())).thenReturn(response);
        WriteRequest body = new WriteRequest(
                "https://cdn.example.com/banner.png",
                "정기구독 첫 달 무료",
                "/subscribe",
                "2026-01-01T00:00:00",
                "2026-12-31T23:59:00",
                1,
                true);

        mockMvc.perform(get("/api/catalog/subscription-banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("정기구독 첫 달 무료"));
        mockMvc.perform(get("/api/catalog/admin/subscription-banners")).andExpect(status().isOk());
        mockMvc.perform(get("/api/catalog/admin/subscription-banners/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bannerId").value(1));
        mockMvc.perform(post("/api/catalog/admin/subscription-banners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/catalog/admin/subscription-banners/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/catalog/admin/subscription-banners/1")).andExpect(status().isOk());
    }

    @Test
    void 존재하지_않는_배너_조회는_404를_반환한다() throws Exception {
        when(subscriptionBannerService.getBanner(999L)).thenThrow(new SubscriptionBannerNotFoundException(999L));

        mockMvc.perform(get("/api/catalog/admin/subscription-banners/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 이미지URL이_비어있으면_등록은_400을_반환한다() throws Exception {
        WriteRequest body = new WriteRequest("", "제목", null, "2026-01-01T00:00:00", "2026-12-31T23:59:00", 1, true);

        mockMvc.perform(post("/api/catalog/admin/subscription-banners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private record WriteRequest(
            String imageUrl,
            String title,
            String linkUrl,
            String startAt,
            String endAt,
            int sortOrder,
            boolean active) {}
}
