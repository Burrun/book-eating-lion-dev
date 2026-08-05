package com.bookeatinglion.admin.controller;

import com.bookeatinglion.admin.AdminModuleTestApplication;
import com.bookeatinglion.admin.dto.AdminBookResponse;
import com.bookeatinglion.admin.dto.AdminMemberResponse;
import com.bookeatinglion.admin.dto.AdminOrderResponse;
import com.bookeatinglion.admin.dto.AuditLogResponse;
import com.bookeatinglion.admin.dto.DashboardStatsResponse;
import com.bookeatinglion.admin.dto.RecentOrderSummary;
import com.bookeatinglion.admin.service.AdminService;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.member.domain.MemberGrade;
import com.bookeatinglion.member.domain.Role;
import com.bookeatinglion.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminController.class)
@ContextConfiguration(classes = AdminModuleTestApplication.class)
class AdminControllerTest {

    private static final String ADMIN_SUB = "admin-sub-1";
    private static final String USER_SUB = "user-sub-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    private RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject(ADMIN_SUB)).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private RequestPostProcessor nonAdmin() {
        return jwt().jwt(j -> j.subject(USER_SUB));
    }

    // ---- 401 인증되지 않은 요청 ----

    @Test
    void 도서목록_미인증_요청은_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/books")).andExpect(status().isUnauthorized());
    }

    @Test
    void 주문목록_미인증_요청은_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void 회원목록_미인증_요청은_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/members")).andExpect(status().isUnauthorized());
    }

    @Test
    void 대시보드_미인증_요청은_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void 감사로그_미인증_요청은_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")).andExpect(status().isUnauthorized());
    }

    // ---- 403 인증됐지만 관리자가 아닌 요청 ----

    @Test
    void 도서목록_비관리자_요청은_403을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/books").with(nonAdmin())).andExpect(status().isForbidden());
    }

    @Test
    void 주문목록_비관리자_요청은_403을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/orders").with(nonAdmin())).andExpect(status().isForbidden());
    }

    @Test
    void 회원목록_비관리자_요청은_403을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/members").with(nonAdmin())).andExpect(status().isForbidden());
    }

    @Test
    void 대시보드_비관리자_요청은_403을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/stats").with(nonAdmin())).andExpect(status().isForbidden());
    }

    @Test
    void 감사로그_비관리자_요청은_403을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs").with(nonAdmin())).andExpect(status().isForbidden());
    }

    // ---- 200 정상 케이스 ----

    @Test
    void 도서_목록_조회는_200과_데이터를_반환한다() throws Exception {
        AdminBookResponse book = new AdminBookResponse(1L, "책1", "저자", "출판사", "9791100000001",
                "소설", 10000, 5, 3, "cover.jpg",
                SaleStatus.ON_SALE, LocalDate.of(2026, 1, 1),
                LocalDateTime.now(), LocalDateTime.now());
        when(adminService.getBooks(any(), any()))
                .thenReturn(new PageImpl<>(List.of(book), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/books").param("page", "0").param("size", "20").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].title").value("책1"));
    }

    @Test
    void 주문_목록_조회는_상태_필터와_함께_200을_반환한다() throws Exception {
        AdminOrderResponse order = new AdminOrderResponse(1L, 10L, "a@a.com", "홍길동",
                OrderStatus.PAID, 20000L, LocalDateTime.now());
        when(adminService.getOrders(eq(OrderStatus.PAID), any()))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/orders").param("status", "PAID").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].orderStatus").value("PAID"));
    }

    @Test
    void 회원_목록_조회는_200을_반환한다() throws Exception {
        AdminMemberResponse member = new AdminMemberResponse(1L, "a@a.com", "홍길동", "010-0000-0000",
                Role.USER, MemberGrade.BRONZE, 0, LocalDateTime.now());
        when(adminService.getMembers(any()))
                .thenReturn(new PageImpl<>(List.of(member), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/members").param("page", "0").param("size", "20").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].email").value("a@a.com"));
    }

    @Test
    void 대시보드_통계_조회는_200을_반환한다() throws Exception {
        RecentOrderSummary recent = new RecentOrderSummary(1L, 10L, "a@a.com", "홍길동",
                OrderStatus.PAID, 20000L, LocalDateTime.now());
        when(adminService.getDashboardStats())
                .thenReturn(new DashboardStatsResponse(1_000_000L, 50L, 30L, 5L, List.of(recent)));

        mockMvc.perform(get("/api/admin/dashboard/stats").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSalesRevenue").value(1_000_000))
                .andExpect(jsonPath("$.data.recentOrderList[0].orderId").value(1));
    }

    @Test
    void 감사로그_목록_조회는_200을_반환한다() throws Exception {
        AuditLogResponse log = new AuditLogResponse(1L, 1L, "ORDER_CANCELLED", "ORDER", 5L,
                "127.0.0.1", "관리자가 주문을 취소함", LocalDateTime.now());
        when(adminService.getAuditLogs(any()))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/audit-logs").param("page", "0").param("size", "20").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].action").value("ORDER_CANCELLED"));
    }
}
