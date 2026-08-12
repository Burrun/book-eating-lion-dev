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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;

    public List<MemberCouponView> getAvailableCoupons(String memberId) {
        return memberCouponRepository.findAvailableByMemberId(memberId, LocalDateTime.now()).stream()
                .map(MemberCouponView::from)
                .toList();
    }

    @Transactional
    public MemberCouponView registerCoupon(String memberId, String couponCode) {
        Coupon coupon = couponRepository
                .findByCouponCode(couponCode)
                .orElseThrow(() -> new CouponNotFoundException(couponCode));

        if (coupon.isExpired(LocalDateTime.now())) {
            throw new CouponExpiredException(couponCode);
        }

        if (memberCouponRepository.existsByMemberIdAndCoupon(memberId, coupon)) {
            throw new CouponAlreadyIssuedException(couponCode);
        }

        // existsBy 체크와 저장 사이의 동시성 창에서 두 요청이 동시에 통과하면 UNIQUE
        // (member_id, coupon_id) 제약을 둘 다 건드린다 — saveAndFlush 로 즉시 반영시켜
        // 이 메서드 안에서 잡고, 원래 의도한 409 로 변환한다. flush 없이 save() 만 하면
        // 트랜잭션 커밋 시점까지 위반이 지연돼 여기서 못 잡는다.
        try {
            MemberCoupon memberCoupon = memberCouponRepository.saveAndFlush(new MemberCoupon(memberId, coupon));
            return MemberCouponView.from(memberCoupon);
        } catch (DataIntegrityViolationException e) {
            throw new CouponAlreadyIssuedException(couponCode);
        }
    }
}
