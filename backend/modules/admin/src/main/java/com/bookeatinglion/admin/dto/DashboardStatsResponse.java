package com.bookeatinglion.admin.dto;

import java.util.List;

public record DashboardStatsResponse(
        long totalSalesRevenue,
        long totalOrderCount,
        long totalMemberCount,
        long activeSubscriptionCount,
        List<RecentOrderSummary> recentOrderList
) {
}
