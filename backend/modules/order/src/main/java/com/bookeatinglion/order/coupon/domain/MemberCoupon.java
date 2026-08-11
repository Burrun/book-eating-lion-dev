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
 * member_id 는 순수 Long 이다(경계 밖, member-service 참조 없음).
 *
 * use() 는 아직 어떤 컨트롤러도 호출하지 않는다(등록/조회 두 API 만 있다). 결제 연동 시점에
 * 재사용할 도메인 불변식을 미리 박아둔 것으로, Inventory.deduct() 와 같은 이유다.
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

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Column(name = "is_used", nullable = false)
    private boolean used;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public MemberCoupon(Long memberId, Coupon coupon) {
        this.memberId = memberId;
        this.coupon = coupon;
        this.used = false;
    }

    public void use(LocalDateTime usedAt) {
        if (this.used) {
            throw new CouponAlreadyUsedException(this.id);
        }
        this.used = true;
        this.usedAt = usedAt;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }
}
