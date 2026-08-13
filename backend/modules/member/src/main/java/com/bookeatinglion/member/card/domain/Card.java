package com.bookeatinglion.member.card.domain;

import com.bookeatinglion.member.card.exception.InvalidCardStatusChangeException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * member_db.cards. 가상카드 — 실제 카드망이 아니라 이 서비스가 발급하는 목(mock) 결제수단이다.
 *
 * 레거시(book-eating-lion-bata)는 MyBatis 로 "UPDATE ... WHERE current_usage + ? <= monthly_limit"
 * 를 직접 쓰고 영향받은 행 수로 한도초과를 판별했다. 이 저장소는 JPA 라 같은 효과를
 * @Version(Inventory 와 동일 패턴) + 도메인 메서드로 낸다.
 *
 * deduct() 가 예외 대신 boolean 을 반환하는 이유: order-service 의 /internal/cards/deduct 계약이
 * "한도 부족은 오류가 아니라 approved=false 인 정상 응답"이라고 못박았다(order-v1.yaml, CardClient).
 * Inventory.deduct() 가 예외를 던지는 것과 반대 방향인데, 호출자가 트랜잭션을 롤백시키고 싶은지
 * (order 의 재고) 아니면 200 으로 거절 응답을 만들고 싶은지(card 의 한도)가 다르기 때문이다.
 */
@Entity
@Table(name = "cards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "card_id")
    private Long id;

    @Column(name = "member_sub", nullable = false)
    private String memberSub;

    @Column(name = "card_token", nullable = false, unique = true)
    private String cardToken;

    @Column(name = "masked_card_number", nullable = false)
    private String maskedCardNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_status", nullable = false)
    private CardStatus cardStatus;

    @Column(name = "monthly_limit", nullable = false)
    private long monthlyLimit;

    @Column(name = "current_usage", nullable = false)
    private long currentUsage;

    @Column(name = "issued_date", nullable = false)
    private LocalDate issuedDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Version
    private Long version;

    public Card(String memberSub, String cardToken, String maskedCardNumber, long monthlyLimit) {
        this.memberSub = memberSub;
        this.cardToken = cardToken;
        this.maskedCardNumber = maskedCardNumber;
        this.cardStatus = CardStatus.ACTIVE;
        this.monthlyLimit = monthlyLimit;
        this.currentUsage = 0L;
        this.issuedDate = LocalDate.now();
        this.expiryDate = LocalDate.now().plusYears(3);
    }

    /** 승인 가능하면 currentUsage 를 늘리고 true, 상태가 ACTIVE 가 아니거나 한도를 넘으면 false. */
    public boolean deduct(long amount) {
        if (cardStatus != CardStatus.ACTIVE) {
            return false;
        }
        if (currentUsage + amount > monthlyLimit) {
            return false;
        }
        currentUsage += amount;
        return true;
    }

    /** 취소/환불 복구. 0 밑으로 내려가지 않게 방어한다. */
    public void restore(long amount) {
        currentUsage = Math.max(0, currentUsage - amount);
    }

    public void changeStatus(CardStatus newStatus) {
        if (this.cardStatus == CardStatus.CLOSED) {
            throw new InvalidCardStatusChangeException(id);
        }
        this.cardStatus = newStatus;
    }

    public boolean isOwnedBy(String memberSub) {
        return this.memberSub.equals(memberSub);
    }
}
