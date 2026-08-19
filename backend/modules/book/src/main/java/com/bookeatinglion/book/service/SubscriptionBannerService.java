package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.SubscriptionBanner;
import com.bookeatinglion.book.dto.SubscriptionBannerCreateRequest;
import com.bookeatinglion.book.dto.SubscriptionBannerResponse;
import com.bookeatinglion.book.dto.SubscriptionBannerUpdateRequest;
import com.bookeatinglion.book.exception.SubscriptionBannerNotFoundException;
import com.bookeatinglion.book.repository.SubscriptionBannerRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionBannerService {

    private final SubscriptionBannerRepository subscriptionBannerRepository;

    /** 홈 화면 노출용 — active && 기간(startAt~endAt) 안에 든 것만. */
    public List<SubscriptionBannerResponse> getCurrentlyActiveBanners() {
        return subscriptionBannerRepository.findCurrentlyActive(LocalDateTime.now()).stream()
                .map(SubscriptionBannerResponse::from)
                .toList();
    }

    public List<SubscriptionBannerResponse> getAdminBanners() {
        return subscriptionBannerRepository.findAllByOrderBySortOrderAscBannerIdAsc().stream()
                .map(SubscriptionBannerResponse::from)
                .toList();
    }

    public SubscriptionBannerResponse getBanner(Long bannerId) {
        return SubscriptionBannerResponse.from(findBanner(bannerId));
    }

    @Transactional
    public SubscriptionBannerResponse create(SubscriptionBannerCreateRequest request) {
        SubscriptionBanner banner = SubscriptionBanner.builder()
                .imageUrl(request.imageUrl())
                .title(request.title())
                .linkUrl(request.linkUrl())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .sortOrder(request.sortOrder())
                .active(request.active())
                .build();
        return SubscriptionBannerResponse.from(subscriptionBannerRepository.save(banner));
    }

    @Transactional
    public SubscriptionBannerResponse update(Long bannerId, SubscriptionBannerUpdateRequest request) {
        SubscriptionBanner banner = findBanner(bannerId);
        banner.update(
                request.imageUrl(),
                request.title(),
                request.linkUrl(),
                request.startAt(),
                request.endAt(),
                request.sortOrder(),
                request.active());
        return SubscriptionBannerResponse.from(banner);
    }

    @Transactional
    public void delete(Long bannerId) {
        findBanner(bannerId).deactivate();
    }

    private SubscriptionBanner findBanner(Long bannerId) {
        return subscriptionBannerRepository
                .findById(bannerId)
                .orElseThrow(() -> new SubscriptionBannerNotFoundException(bannerId));
    }
}
