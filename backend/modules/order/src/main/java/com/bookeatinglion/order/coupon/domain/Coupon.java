package com.bookeatinglion.order.coupon.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** order_db.coupons. 발급 원장(카탈로그)이며, 회원별 보유/사용 상태는 MemberCoupon 이 갖는다. */
@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_id")
    private Long id;

    @Column(name = "coupon_code", nullable = false, unique = true)
    private String couponCode;

    @Column(name = "coupon_name", nullable = false)
    private String couponName;

    @Column(name = "discount_amount", nullable = false)
    private int discountAmount;

    @Column(name = "minimum_order_amount", nullable = false)
    private int minimumOrderAmount;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public Coupon(
            String couponCode, String couponName, int discountAmount, int minimumOrderAmount, LocalDateTime expiresAt) {
        this.couponCode = couponCode;
        this.couponName = couponName;
        this.discountAmount = discountAmount;
        this.minimumOrderAmount = minimumOrderAmount;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    // couponCode 는 발급 식별자라 여기서 바꾸지 않는다. 종료는 expiresAt 을 과거로 넣는 것으로
    // 처리한다(별도 active 컬럼이 없고, member_coupons FK가 ON DELETE RESTRICT라 하드 삭제도 못 한다).
    public void update(String couponName, int discountAmount, int minimumOrderAmount, LocalDateTime expiresAt) {
        this.couponName = couponName;
        this.discountAmount = discountAmount;
        this.minimumOrderAmount = minimumOrderAmount;
        this.expiresAt = expiresAt;
    }
}
