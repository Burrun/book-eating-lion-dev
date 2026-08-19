package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.SubscriptionBanner;
import java.time.LocalDateTime;

public record SubscriptionBannerResponse(
        Long bannerId,
        String imageUrl,
        String title,
        String linkUrl,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int sortOrder,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static SubscriptionBannerResponse from(SubscriptionBanner banner) {
        return new SubscriptionBannerResponse(
                banner.getBannerId(),
                banner.getImageUrl(),
                banner.getTitle(),
                banner.getLinkUrl(),
                banner.getStartAt(),
                banner.getEndAt(),
                banner.getSortOrder(),
                banner.isActive(),
                banner.getCreatedAt(),
                banner.getUpdatedAt());
    }
}
