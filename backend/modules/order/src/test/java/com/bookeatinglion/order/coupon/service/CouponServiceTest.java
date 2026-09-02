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
import com.bookeatinglion.order.coupon.dto.CouponCreateRequest;
import com.bookeatinglion.order.coupon.dto.CouponResponse;
import com.bookeatinglion.order.coupon.dto.CouponUpdateRequest;
import com.bookeatinglion.order.coupon.dto.MemberCouponView;
import com.bookeatinglion.order.coupon.exception.CouponAlreadyIssuedException;
import com.bookeatinglion.order.coupon.exception.CouponCodeDuplicatedException;
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

    @Test
    void 전체_쿠폰_목록을_조회한다() {
        Coupon coupon = coupon(1L, "WELCOME3000", LocalDateTime.now().plusDays(30));
        when(couponRepository.findAllByOrderByExpiresAtDesc()).thenReturn(List.of(coupon));

        List<CouponResponse> result = couponService.getAllCoupons();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).couponCode()).isEqualTo("WELCOME3000");
    }

    @Test
    void 쿠폰_단건을_조회한다() {
        Coupon coupon = coupon(1L, "WELCOME3000", LocalDateTime.now().plusDays(30));
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));

        CouponResponse result = couponService.getCoupon(1L);

        assertThat(result.couponCode()).isEqualTo("WELCOME3000");
    }

    @Test
    void 존재하지_않는_쿠폰_단건_조회는_예외를_던진다() {
        when(couponRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.getCoupon(999L)).isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    void 쿠폰을_등록한다() {
        CouponCreateRequest request = new CouponCreateRequest(
                "NEW3000", "신규 쿠폰", 3000, 10000, LocalDateTime.now().plusDays(30));
        when(couponRepository.existsByCouponCode("NEW3000")).thenReturn(false);
        when(couponRepository.saveAndFlush(any(Coupon.class))).thenAnswer(invocation -> {
            Coupon saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        CouponResponse result = couponService.createCoupon(request);

        assertThat(result.couponId()).isEqualTo(1L);
        assertThat(result.couponCode()).isEqualTo("NEW3000");
    }

    @Test
    void 중복된_쿠폰_코드_등록은_예외를_던진다() {
        CouponCreateRequest request = new CouponCreateRequest(
                "DUP", "중복 쿠폰", 3000, 10000, LocalDateTime.now().plusDays(30));
        when(couponRepository.existsByCouponCode("DUP")).thenReturn(true);

        assertThatThrownBy(() -> couponService.createCoupon(request)).isInstanceOf(CouponCodeDuplicatedException.class);

        verify(couponRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시_등록_경합으로_코드_중복_제약이_위반되면_중복_예외로_변환한다() {
        CouponCreateRequest request = new CouponCreateRequest(
                "RACE", "경합 쿠폰", 3000, 10000, LocalDateTime.now().plusDays(30));
        when(couponRepository.existsByCouponCode("RACE")).thenReturn(false);
        when(couponRepository.saveAndFlush(any(Coupon.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> couponService.createCoupon(request)).isInstanceOf(CouponCodeDuplicatedException.class);
    }

    @Test
    void 쿠폰을_수정한다() {
        Coupon coupon = coupon(1L, "WELCOME3000", LocalDateTime.now().plusDays(30));
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));
        LocalDateTime newExpiresAt = LocalDateTime.now().plusDays(60);
        CouponUpdateRequest request = new CouponUpdateRequest("수정된 이름", 5000, 20000, newExpiresAt);

        CouponResponse result = couponService.updateCoupon(1L, request);

        assertThat(result.couponName()).isEqualTo("수정된 이름");
        assertThat(result.discountAmount()).isEqualTo(5000);
        assertThat(result.minimumOrderAmount()).isEqualTo(20000);
        assertThat(result.expiresAt()).isEqualTo(newExpiresAt);
    }

    @Test
    void 존재하지_않는_쿠폰_수정은_예외를_던진다() {
        when(couponRepository.findById(999L)).thenReturn(Optional.empty());
        CouponUpdateRequest request = new CouponUpdateRequest("이름", 3000, 10000, LocalDateTime.now());

        assertThatThrownBy(() -> couponService.updateCoupon(999L, request)).isInstanceOf(CouponNotFoundException.class);
    }
}
