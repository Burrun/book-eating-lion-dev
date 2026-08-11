package com.bookeatinglion.order.coupon.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookeatinglion.order.OrderModuleTestApplication;
import com.bookeatinglion.order.coupon.domain.Coupon;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = OrderModuleTestApplication.class)
class CouponRepositoryTest {

    @Autowired
    private CouponRepository couponRepository;

    @Test
    void 쿠폰코드로_쿠폰을_조회한다() {
        couponRepository.save(new Coupon(
                "WELCOME3000", "신규 가입 할인", 3000, 10000, LocalDateTime.now().plusDays(30)));

        Optional<Coupon> result = couponRepository.findByCouponCode("WELCOME3000");

        assertThat(result).isPresent();
        assertThat(result.get().getCouponName()).isEqualTo("신규 가입 할인");
    }

    @Test
    void 존재하지_않는_코드는_빈값을_반환한다() {
        Optional<Coupon> result = couponRepository.findByCouponCode("NO-SUCH-CODE");

        assertThat(result).isEmpty();
    }
}
