package com.bookeatinglion.admin.service;

import com.bookeatinglion.admin.domain.SubscriptionStatus;
import com.bookeatinglion.admin.dto.AdminBookResponse;
import com.bookeatinglion.admin.dto.AdminMemberResponse;
import com.bookeatinglion.admin.dto.AdminOrderResponse;
import com.bookeatinglion.admin.dto.AuditLogResponse;
import com.bookeatinglion.admin.dto.DashboardStatsResponse;
import com.bookeatinglion.admin.repository.AuditLogRepository;
import com.bookeatinglion.admin.repository.SubscriptionRepository;
import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.member.domain.Member;
import com.bookeatinglion.member.repository.MemberRepository;
import com.bookeatinglion.order.domain.Order;
import com.bookeatinglion.order.domain.OrderStatus;
import com.bookeatinglion.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AdminService adminService;

    private Book book(Long id, String title) throws Exception {
        Book book = Book.builder()
                .title(title).author("저자").publisher("출판사").isbn("978110000" + id)
                .category("소설").price(10000).stockQuantity(5)
                .saleStatus(SaleStatus.ON_SALE).publishedDate(LocalDate.of(2026, 1, 1)).salesCount(0)
                .build();
        setId(Book.class, "bookId", book, id);
        return book;
    }

    private Order order(Long id, Long memberId, OrderStatus status, long totalAmount) throws Exception {
        Order order = Order.builder()
                .memberId(memberId).bookId(1L).orderStatus(status).totalAmount(totalAmount)
                .build();
        setId(Order.class, "id", order, id);
        return order;
    }

    private Member member(Long id, String email, String name) throws Exception {
        Member member = Member.register("sub-" + id, email, name);
        setId(Member.class, "id", member, id);
        return member;
    }

    private void setId(Class<?> clazz, String fieldName, Object target, Long value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void 카테고리없이_도서목록을_조회하면_findAll을_호출한다() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(bookRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(book(1L, "책1"))));

        Page<AdminBookResponse> result = adminService.getBooks(null, pageable);

        assertThat(result.getContent()).extracting(AdminBookResponse::title).containsExactly("책1");
    }

    @Test
    void 카테고리로_도서목록을_조회하면_findByCategory를_호출한다() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(bookRepository.findByCategory(eq("소설"), any())).thenReturn(new PageImpl<>(List.of(book(1L, "소설책"))));

        Page<AdminBookResponse> result = adminService.getBooks("소설", pageable);

        assertThat(result.getContent()).extracting(AdminBookResponse::title).containsExactly("소설책");
    }

    @Test
    void 주문목록_조회시_회원정보를_배치조회한다() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        Order order1 = order(1L, 10L, OrderStatus.PAID, 10000L);
        Order order2 = order(2L, 20L, OrderStatus.PAID, 20000L);
        when(orderRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(order1, order2)));
        when(memberRepository.findAllById(any())).thenReturn(List.of(member(10L, "a@a.com", "회원A")));

        Page<AdminOrderResponse> result = adminService.getOrders(null, pageable);

        assertThat(result.getContent()).hasSize(2);
        verify(memberRepository, times(1)).findAllById(any());
    }

    @Test
    void 상태_필터로_주문목록을_조회한다() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(orderRepository.findByOrderStatus(eq(OrderStatus.PAID), any()))
                .thenReturn(new PageImpl<>(List.of(order(1L, 10L, OrderStatus.PAID, 10000L))));
        when(memberRepository.findAllById(any())).thenReturn(List.of(member(10L, "a@a.com", "회원A")));

        Page<AdminOrderResponse> result = adminService.getOrders(OrderStatus.PAID, pageable);

        assertThat(result.getContent()).extracting(AdminOrderResponse::orderStatus).containsExactly(OrderStatus.PAID);
    }

    @Test
    void 회원목록을_조회한다() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(memberRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(member(1L, "a@a.com", "회원A"))));

        Page<AdminMemberResponse> result = adminService.getMembers(pageable);

        assertThat(result.getContent()).extracting(AdminMemberResponse::email).containsExactly("a@a.com");
    }

    @Test
    void 대시보드_통계는_리포지토리_집계값을_그대로_조합한다() throws Exception {
        when(orderRepository.sumTotalAmountByOrderStatusNotIn(any())).thenReturn(1_000_000L);
        when(orderRepository.count()).thenReturn(50L);
        when(memberRepository.count()).thenReturn(30L);
        when(subscriptionRepository.countBySubscriptionStatus(SubscriptionStatus.ACTIVE)).thenReturn(5L);
        Order recent = order(1L, 10L, OrderStatus.PAID, 10000L);
        when(orderRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of(recent));
        when(memberRepository.findAllById(any())).thenReturn(List.of(member(10L, "a@a.com", "회원A")));

        DashboardStatsResponse result = adminService.getDashboardStats();

        assertThat(result.totalSalesRevenue()).isEqualTo(1_000_000L);
        assertThat(result.totalOrderCount()).isEqualTo(50L);
        assertThat(result.totalMemberCount()).isEqualTo(30L);
        assertThat(result.activeSubscriptionCount()).isEqualTo(5L);
        assertThat(result.recentOrderList()).hasSize(1);
    }

    @Test
    void 감사로그_목록을_페이지로_조회한다() {
        Pageable pageable = PageRequest.of(0, 20);
        when(auditLogRepository.findAll(pageable)).thenReturn(Page.empty(pageable));

        Page<AuditLogResponse> result = adminService.getAuditLogs(pageable);

        assertThat(result.getContent()).isEmpty();
    }
}
