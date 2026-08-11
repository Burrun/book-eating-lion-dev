package com.bookeatinglion.order.cart.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import com.bookeatinglion.order.cart.exception.InvalidCartQuantityException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * order_db.cart_items. book_id 는 catalog_db.books.book_id 값이지만 FK 는 아니다 — 경계 밖이다.
 * UNIQUE (member_id, book_id) 가 동시 담기 요청에서의 중복 행을 DB 레벨에서 막는다.
 */
@Entity
@Table(name = "cart_items", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "book_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_item_id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(nullable = false)
    private int quantity;

    public CartItem(Long memberId, Long bookId, int quantity) {
        validateQuantity(quantity);
        this.memberId = memberId;
        this.bookId = bookId;
        this.quantity = quantity;
    }

    public void increaseQuantity(int delta) {
        validateQuantity(delta);
        this.quantity += delta;
    }

    public void changeQuantity(int quantity) {
        validateQuantity(quantity);
        this.quantity = quantity;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    private void validateQuantity(int quantity) {
        if (quantity < 1) {
            throw new InvalidCartQuantityException(quantity);
        }
    }
}
