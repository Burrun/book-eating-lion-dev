package com.bookeatinglion.order.coupon.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;

    public List<MemberCouponView> getAvailableCoupons(Long memberId) {
        return memberCouponRepository.findAvailableByMemberId(memberId, LocalDateTime.now()).stream()
                .map(MemberCouponView::from)
                .toList();
    }

    @Transactional
    public MemberCouponView registerCoupon(Long memberId, String couponCode) {
        Coupon coupon = couponRepository
                .findByCouponCode(couponCode)
                .orElseThrow(() -> new CouponNotFoundException(couponCode));

        if (coupon.isExpired(LocalDateTime.now())) {
            throw new CouponExpiredException(couponCode);
        }

        if (memberCouponRepository.existsByMemberIdAndCoupon(memberId, coupon)) {
            throw new CouponAlreadyIssuedException(couponCode);
        }

        MemberCoupon memberCoupon = memberCouponRepository.save(new MemberCoupon(memberId, coupon));
        return MemberCouponView.from(memberCoupon);
    }
}
