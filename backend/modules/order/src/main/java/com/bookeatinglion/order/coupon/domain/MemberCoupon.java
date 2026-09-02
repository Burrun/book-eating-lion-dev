package com.bookeatinglion.order.coupon.domain;

import com.bookeatinglion.order.coupon.exception.CouponAlreadyUsedException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * order_db.member_coupons. Coupon 은 단방향 @ManyToOne — Coupon 은 MemberCoupon 을 모른다.
 * member_id 는 Cognito sub(UUID 문자열)를 그대로 쓴다(경계 밖, member-service 참조 없음).
 *
 * usedOrderId 는 Coupon 기능 단계에는 없던 컬럼이다 — 주문 취소 시 "이 쿠폰이 어느 주문에
 * 쓰였는지"를 역추적할 방법이 스키마에 없어서 추가했다(취소하려면 원복할 대상을 알아야 한다).
 * use()/cancelUse() 는 OrderService 가 호출한다.
 */
@Entity
@Table(name = "member_coupons", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "coupon_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_coupon_id")
    private Long id;

    @Column(name = "member_id", nullable = false, length = 255)
    private String memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Column(name = "is_used", nullable = false)
    private boolean used;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "used_order_id")
    private Long usedOrderId;

    public MemberCoupon(String memberId, Coupon coupon) {
        this.memberId = memberId;
        this.coupon = coupon;
        this.used = false;
    }

    public void use(LocalDateTime usedAt, Long orderId) {
        if (this.used) {
            throw new CouponAlreadyUsedException(this.id);
        }
        this.used = true;
        this.usedAt = usedAt;
        this.usedOrderId = orderId;
    }

    /** 주문 취소 시 원복. OrderService 가 usedOrderId 로 역추적해 호출한다. */
    public void cancelUse() {
        this.used = false;
        this.usedAt = null;
        this.usedOrderId = null;
    }

    public boolean isOwnedBy(String memberId) {
        return this.memberId.equals(memberId);
    }
}
