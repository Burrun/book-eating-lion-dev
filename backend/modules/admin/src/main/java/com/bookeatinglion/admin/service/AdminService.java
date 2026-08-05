package com.bookeatinglion.admin.service;

import com.bookeatinglion.admin.domain.SubscriptionStatus;
import com.bookeatinglion.admin.dto.AdminBookResponse;
import com.bookeatinglion.admin.dto.AdminMemberResponse;
import com.bookeatinglion.admin.dto.AdminOrderResponse;
import com.bookeatinglion.admin.dto.AuditLogResponse;
import com.bookeatinglion.admin.dto.DashboardStatsResponse;
import com.bookeatinglion.admin.dto.RecentOrderSummary;
import com.bookeatinglion.admin.repository.AuditLogRepository;
import com.bookeatinglion.admin.repository.SubscriptionRepository;
import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.member.domain.Member;
import com.bookeatinglion.member.repository.MemberRepository;
import com.bookeatinglion.order.domain.Order;
import com.bookeatinglion.order.domain.OrderStatus;
import com.bookeatinglion.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    // 매출로 집계하지 않는 주문 상태. 1차 비즈니스 규칙이므로 기획/정산팀 확인 후 조정 필요.
    private static final EnumSet<OrderStatus> NON_REVENUE_STATUSES = EnumSet.of(
            OrderStatus.PENDING_PAYMENT,
            OrderStatus.PAYMENT_FAILED,
            OrderStatus.CANCELLED,
            OrderStatus.REFUNDED
    );

    private final BookRepository bookRepository;
    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AuditLogRepository auditLogRepository;

    public Page<AdminBookResponse> getBooks(String category, Pageable pageable) {
        Page<Book> books = (category == null || category.isBlank())
                ? bookRepository.findAll(pageable)
                : bookRepository.findByCategory(category, pageable);
        return books.map(AdminBookResponse::from);
    }

    public Page<AdminOrderResponse> getOrders(OrderStatus status, Pageable pageable) {
        Page<Order> orders = (status == null)
                ? orderRepository.findAll(pageable)
                : orderRepository.findByOrderStatus(status, pageable);
        Map<Long, Member> membersById = findMembersByOrders(orders.getContent());
        return orders.map(order -> AdminOrderResponse.from(order, membersById.get(order.getMemberId())));
    }

    public Page<AdminMemberResponse> getMembers(Pageable pageable) {
        return memberRepository.findAll(pageable).map(AdminMemberResponse::from);
    }

    public DashboardStatsResponse getDashboardStats() {
        long totalSalesRevenue = orderRepository.sumTotalAmountByOrderStatusNotIn(NON_REVENUE_STATUSES);
        long totalOrderCount = orderRepository.count();
        long totalMemberCount = memberRepository.count();
        long activeSubscriptionCount = subscriptionRepository.countBySubscriptionStatus(SubscriptionStatus.ACTIVE);

        List<Order> recentOrders = orderRepository.findTop5ByOrderByCreatedAtDesc();
        Map<Long, Member> membersById = findMembersByOrders(recentOrders);
        List<RecentOrderSummary> recentOrderList = recentOrders.stream()
                .map(order -> RecentOrderSummary.from(order, membersById.get(order.getMemberId())))
                .toList();

        return new DashboardStatsResponse(
                totalSalesRevenue, totalOrderCount, totalMemberCount, activeSubscriptionCount, recentOrderList);
    }

    public Page<AuditLogResponse> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable).map(AuditLogResponse::from);
    }

    private Map<Long, Member> findMembersByOrders(List<Order> orders) {
        List<Long> memberIds = orders.stream().map(Order::getMemberId).distinct().toList();
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
    }
}
