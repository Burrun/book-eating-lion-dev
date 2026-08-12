package com.bookeatinglion.order.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.order.coupon.domain.Coupon;
import com.bookeatinglion.order.coupon.domain.MemberCoupon;
import com.bookeatinglion.order.coupon.dto.MemberCouponView;
import com.bookeatinglion.order.coupon.exception.CouponAlreadyIssuedException;
import com.bookeatinglion.order.coupon.exception.CouponExpiredException;
import com.bookeatinglion.order.coupon.exception.CouponNotFoundException;
import com.bookeatinglion.order.coupon.repository.CouponRepository;
import com.bookeatinglion.order.coupon.repository.MemberCouponRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    private static final String MEMBER_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private MemberCouponRepository memberCouponRepository;

    @InjectMocks
    private CouponService couponService;

    private Coupon coupon(Long id, String code, LocalDateTime expiresAt) {
        Coupon coupon = new Coupon(code, code + " 할인", 3000, 10000, expiresAt);
        ReflectionTestUtils.setField(coupon, "id", id);
        return coupon;
    }

    @Test
    void 보유_쿠폰_목록을_조회한다() {
        Coupon coupon = coupon(1L, "VALID", LocalDateTime.now().plusDays(1));
        MemberCoupon memberCoupon = new MemberCoupon(MEMBER_ID, coupon);
        when(memberCouponRepository.findAvailableByMemberId(eq(MEMBER_ID), any()))
                .thenReturn(List.of(memberCoupon));

        List<MemberCouponView> result = couponService.getAvailableCoupons(MEMBER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).couponCode()).isEqualTo("VALID");
    }

    @Test
    void 쿠폰코드로_등록한다() {
        Coupon coupon = coupon(1L, "WELCOME3000", LocalDateTime.now().plusDays(30));
        when(couponRepository.findByCouponCode("WELCOME3000")).thenReturn(Optional.of(coupon));
        when(memberCouponRepository.existsByMemberIdAndCoupon(MEMBER_ID, coupon))
                .thenReturn(false);
        when(memberCouponRepository.saveAndFlush(any(MemberCoupon.class))).thenAnswer(invocation -> {
            MemberCoupon saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });

        MemberCouponView view = couponService.registerCoupon(MEMBER_ID, "WELCOME3000");

        assertThat(view.memberCouponId()).isEqualTo(10L);
        assertThat(view.couponCode()).isEqualTo("WELCOME3000");
    }

    @Test
    void 존재하지_않는_코드는_예외를_던진다() {
        when(couponRepository.findByCouponCode("NO-SUCH")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.registerCoupon(MEMBER_ID, "NO-SUCH"))
                .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    void 만료된_쿠폰은_예외를_던진다() {
        Coupon expired = coupon(1L, "EXPIRED", LocalDateTime.now().minusDays(1));
        when(couponRepository.findByCouponCode("EXPIRED")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> couponService.registerCoupon(MEMBER_ID, "EXPIRED"))
                .isInstanceOf(CouponExpiredException.class);
    }

    @Test
    void 이미_보유한_쿠폰은_예외를_던진다() {
        Coupon coupon = coupon(1L, "DUP", LocalDateTime.now().plusDays(1));
        when(couponRepository.findByCouponCode("DUP")).thenReturn(Optional.of(coupon));
        when(memberCouponRepository.existsByMemberIdAndCoupon(MEMBER_ID, coupon))
                .thenReturn(true);

        assertThatThrownBy(() -> couponService.registerCoupon(MEMBER_ID, "DUP"))
                .isInstanceOf(CouponAlreadyIssuedException.class);

        verify(memberCouponRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시_등록_경합으로_DB_제약이_위반되면_이미_보유한_쿠폰_예외로_변환한다() {
        Coupon coupon = coupon(1L, "RACE", LocalDateTime.now().plusDays(1));
        when(couponRepository.findByCouponCode("RACE")).thenReturn(Optional.of(coupon));
        // existsBy 체크는 통과했지만(경합 창), 실제 저장 시점엔 이미 다른 요청이 먼저 넣어
        // UNIQUE(member_id, coupon_id) 위반이 난 상황을 흉내낸다.
        when(memberCouponRepository.existsByMemberIdAndCoupon(MEMBER_ID, coupon))
                .thenReturn(false);
        when(memberCouponRepository.saveAndFlush(any(MemberCoupon.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> couponService.registerCoupon(MEMBER_ID, "RACE"))
                .isInstanceOf(CouponAlreadyIssuedException.class);
    }
}
