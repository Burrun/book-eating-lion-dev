package com.bookeatinglion.book.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.book.domain.SubscriptionBanner;
import com.bookeatinglion.book.dto.SubscriptionBannerCreateRequest;
import com.bookeatinglion.book.exception.SubscriptionBannerNotFoundException;
import com.bookeatinglion.book.repository.SubscriptionBannerRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionBannerServiceTest {

    @Mock
    SubscriptionBannerRepository subscriptionBannerRepository;

    @InjectMocks
    SubscriptionBannerService subscriptionBannerService;

    @Test
    void 홈_화면은_기간_안의_활성_배너만_조회한다() {
        SubscriptionBanner banner = banner(true);
        when(subscriptionBannerRepository.findCurrentlyActive(any())).thenReturn(List.of(banner));

        var result = subscriptionBannerService.getCurrentlyActiveBanners();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).active()).isTrue();
        verify(subscriptionBannerRepository, never()).findAllByOrderBySortOrderAscBannerIdAsc();
    }

    @Test
    void 관리자는_기간_비활성_여부와_무관하게_전체_배너를_조회한다() {
        when(subscriptionBannerRepository.findAllByOrderBySortOrderAscBannerIdAsc())
                .thenReturn(List.of(banner(true), banner(false)));

        var result = subscriptionBannerService.getAdminBanners();

        assertThat(result).hasSize(2);
    }

    @Test
    void 관리자가_배너를_등록한다() {
        when(subscriptionBannerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = subscriptionBannerService.create(new SubscriptionBannerCreateRequest(
                "https://cdn.example.com/banner.png",
                "정기구독 첫 달 무료",
                "/subscribe",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 12, 31, 23, 59),
                1,
                true));

        assertThat(result.title()).isEqualTo("정기구독 첫 달 무료");
        assertThat(result.active()).isTrue();
    }

    @Test
    void 존재하지_않는_배너_조회는_예외를_던진다() {
        when(subscriptionBannerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionBannerService.getBanner(999L))
                .isInstanceOf(SubscriptionBannerNotFoundException.class);
    }

    @Test
    void 배너_삭제는_비활성화로_처리한다() {
        SubscriptionBanner banner = banner(true);
        when(subscriptionBannerRepository.findById(1L)).thenReturn(Optional.of(banner));

        subscriptionBannerService.delete(1L);

        assertThat(banner.isActive()).isFalse();
        verify(subscriptionBannerRepository, never()).delete(any());
    }

    private SubscriptionBanner banner(boolean active) {
        return SubscriptionBanner.builder()
                .imageUrl("https://cdn.example.com/banner.png")
                .title("정기구독 첫 달 무료")
                .linkUrl("/subscribe")
                .startAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .endAt(LocalDateTime.of(2026, 12, 31, 23, 59))
                .sortOrder(1)
                .active(active)
                .build();
    }
}
