package com.bookeatinglion.order.coupon.repository;

import com.bookeatinglion.order.coupon.domain.Coupon;
import com.bookeatinglion.order.coupon.domain.MemberCoupon;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberCouponRepository extends JpaRepository<MemberCoupon, Long> {

    boolean existsByMemberIdAndCoupon(String memberId, Coupon coupon);

    /**
     * 주문에 쿠폰을 적용하기 직전에 이 메서드로 읽는다 — {@code SELECT ... FOR UPDATE} 로 행을
     * 잠가, 같은 쿠폰을 쓰는 두 주문이 동시에 들어와도 뒤엣놈이 앞엣놈의 커밋을 기다렸다가
     * {@code used = true} 를 보고 결제 전에 거절되게 한다. 낙관적 락(@Version)만으로는 충돌이
     * 커밋 시점에야 드러나 카드/카카오 승인이 이미 끝난 뒤라 되돌릴 수 없다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MemberCoupon> findWithLockById(Long id);

    /** 미사용 + 미만료 쿠폰만. coupon 을 함께 FETCH 해 목록 조회에서 N+1 을 막는다. */
    @Query("SELECT mc FROM MemberCoupon mc JOIN FETCH mc.coupon c "
            + "WHERE mc.memberId = :memberId AND mc.used = false AND c.expiresAt > :now")
    List<MemberCoupon> findAvailableByMemberId(@Param("memberId") String memberId, @Param("now") LocalDateTime now);

    /** 주문 취소 시 원복할 쿠폰을 역추적한다. */
    Optional<MemberCoupon> findByUsedOrderId(Long orderId);
}
