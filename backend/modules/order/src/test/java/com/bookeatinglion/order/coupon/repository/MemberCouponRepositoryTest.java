package com.bookeatinglion.order.coupon.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookeatinglion.order.OrderModuleTestApplication;
import com.bookeatinglion.order.coupon.domain.Coupon;
import com.bookeatinglion.order.coupon.domain.MemberCoupon;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = OrderModuleTestApplication.class)
class MemberCouponRepositoryTest {

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private MemberCouponRepository memberCouponRepository;

    private Coupon coupon(String code, LocalDateTime expiresAt) {
        return couponRepository.save(new Coupon(code, code + " 할인", 1000, 5000, expiresAt));
    }

    @Test
    void 미사용_미만료_쿠폰만_조회한다() {
        Coupon valid = coupon("VALID", LocalDateTime.now().plusDays(1));
        Coupon expired = coupon("EXPIRED", LocalDateTime.now().minusDays(1));
        Coupon used = coupon("USED", LocalDateTime.now().plusDays(1));

        memberCouponRepository.save(new MemberCoupon(1L, valid));
        memberCouponRepository.save(new MemberCoupon(1L, expired));
        MemberCoupon usedCoupon = new MemberCoupon(1L, used);
        usedCoupon.use(LocalDateTime.now(), 100L);
        memberCouponRepository.save(usedCoupon);

        List<MemberCoupon> result = memberCouponRepository.findAvailableByMemberId(1L, LocalDateTime.now());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCoupon().getCouponCode()).isEqualTo("VALID");
    }

    @Test
    void 이미_보유한_쿠폰인지_확인한다() {
        Coupon coupon = coupon("DUP", LocalDateTime.now().plusDays(1));
        memberCouponRepository.save(new MemberCoupon(1L, coupon));

        assertThat(memberCouponRepository.existsByMemberIdAndCoupon(1L, coupon)).isTrue();
        assertThat(memberCouponRepository.existsByMemberIdAndCoupon(2L, coupon)).isFalse();
    }
}
