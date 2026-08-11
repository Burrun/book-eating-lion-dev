package com.bookeatinglion.order.coupon.repository;

import com.bookeatinglion.order.coupon.domain.Coupon;
import com.bookeatinglion.order.coupon.domain.MemberCoupon;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberCouponRepository extends JpaRepository<MemberCoupon, Long> {

    boolean existsByMemberIdAndCoupon(Long memberId, Coupon coupon);

    /** 미사용 + 미만료 쿠폰만. coupon 을 함께 FETCH 해 목록 조회에서 N+1 을 막는다. */
    @Query("SELECT mc FROM MemberCoupon mc JOIN FETCH mc.coupon c "
            + "WHERE mc.memberId = :memberId AND mc.used = false AND c.expiresAt > :now")
    List<MemberCoupon> findAvailableByMemberId(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);

    /** 주문 취소 시 원복할 쿠폰을 역추적한다. */
    Optional<MemberCoupon> findByUsedOrderId(Long orderId);
}
