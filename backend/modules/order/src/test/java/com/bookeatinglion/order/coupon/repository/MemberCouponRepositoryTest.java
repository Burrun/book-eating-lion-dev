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

    private static final String MEMBER_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    private static final String OTHER_MEMBER_ID = "b2c3d4e5-f6a7-8901-bcde-f12345678901";

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

        memberCouponRepository.save(new MemberCoupon(MEMBER_ID, valid));
        memberCouponRepository.save(new MemberCoupon(MEMBER_ID, expired));
        MemberCoupon usedCoupon = new MemberCoupon(MEMBER_ID, used);
        usedCoupon.use(LocalDateTime.now(), 100L);
        memberCouponRepository.save(usedCoupon);

        List<MemberCoupon> result = memberCouponRepository.findAvailableByMemberId(MEMBER_ID, LocalDateTime.now());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCoupon().getCouponCode()).isEqualTo("VALID");
    }

    @Test
    void 이미_보유한_쿠폰인지_확인한다() {
        Coupon coupon = coupon("DUP", LocalDateTime.now().plusDays(1));
        memberCouponRepository.save(new MemberCoupon(MEMBER_ID, coupon));

        assertThat(memberCouponRepository.existsByMemberIdAndCoupon(MEMBER_ID, coupon))
                .isTrue();
        assertThat(memberCouponRepository.existsByMemberIdAndCoupon(OTHER_MEMBER_ID, coupon))
                .isFalse();
    }
}
