package com.bookeatinglion.book.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 홈 화면에 노출하는 정기구독 홍보 배너의 콘텐츠(이미지/문구/기간)다. member 모듈의
 * Subscription(실제 구독 계약) 도메인과는 데이터 의존이 없다 — 여기는 순수 마케팅 콘텐츠다.
 */
@Entity
@Table(name = "subscription_banners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionBanner extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "banner_id")
    private Long bannerId;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Builder
    public SubscriptionBanner(
            String imageUrl,
            String title,
            String linkUrl,
            LocalDateTime startAt,
            LocalDateTime endAt,
            int sortOrder,
            boolean active) {
        this.imageUrl = imageUrl;
        this.title = title;
        this.linkUrl = linkUrl;
        this.startAt = startAt;
        this.endAt = endAt;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public void update(
            String imageUrl,
            String title,
            String linkUrl,
            LocalDateTime startAt,
            LocalDateTime endAt,
            int sortOrder,
            boolean active) {
        this.imageUrl = imageUrl;
        this.title = title;
        this.linkUrl = linkUrl;
        this.startAt = startAt;
        this.endAt = endAt;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public void deactivate() {
        this.active = false;
    }
}
