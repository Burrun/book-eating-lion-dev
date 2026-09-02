package com.bookeatinglion.order.inventory.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * books.stock 에서 이관된 재고. 계획서 Phase 0-1 의 산출물이다.
 *
 * 왜 catalog 가 아니라 order 가 소유하는가 — 라이프사이클만 보면 재고는 상품
 * 애그리게잇에 가깝지만(도서가 삭제되면 재고도 사라진다), 애그리게잇의 결정
 * 기준은 라이프사이클이 아니라 트랜잭션 경계다. KPI 가 "오버셀링 0건"이면
 * 재고의 일관성 경계는 주문 쪽이다(안내서[R1] §8.1.2).
 *
 * 이 한 줄이 Saga 와 보상 트랜잭션을 통째로 없앤다.
 */
@Entity
@Table(name = "inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory extends BaseEntity {

    /** catalog_db.books.book_id 의 값이지만 FK 는 아니다 — 경계 밖이다. */
    @Id
    @Column(name = "book_id")
    private Long bookId;

    @Column(nullable = false)
    private int stock;

    /**
     * 분산 락(ElastiCache Redlock)이 1차 방어선이고 이건 2차 방어선이다.
     * 락 획득에 실패한 경로가 있어도 DB 레벨에서 잃어버린 갱신을 막는다.
     */
    @Version
    private Long version;

    public Inventory(Long bookId, int stock) {
        this.bookId = bookId;
        this.stock = stock;
    }

    /** 관리자 입고. catalog-service 가 /internal/inventory/{bookId}/restock 로 호출한다. */
    public void restock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("입고 수량은 1 이상이어야 합니다: " + quantity);
        }
        this.stock += quantity;
    }

    /** 주문 차감. 같은 서비스 안이므로 로컬 트랜잭션이다. */
    public void deduct(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("차감 수량은 1 이상이어야 합니다: " + quantity);
        }
        if (this.stock < quantity) {
            throw new InsufficientStockException(bookId, this.stock, quantity);
        }
        this.stock -= quantity;
    }
}
