package com.bookeatinglion.book.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "books")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Book extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_id")
    private Long bookId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false)
    private String publisher;

    @Column(nullable = false, unique = true, length = 13)
    private String isbn;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false)
    private int price;

    // stockQuantity 는 여기 없다. 재고는 order-service 가 소유한다(계획서 Phase 0-1 / 판단 ③).
    // 재고에 쓰기를 하는 주체는 ①관리자 입고 ②주문 차감 둘뿐이고 둘 다 order 안에 있으므로,
    // 재고를 order_db.inventory 로 옮기면 결제 트랜잭션이 서비스 경계를 넘지 않는다.
    // 화면 표시용 재고 수량은 InventoryPort 로 조회해 조합한다(API 조합 패턴).

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String detailedSynopsis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SaleStatus saleStatus;

    @Column
    private LocalDate publishedDate;

    @Column(nullable = false)
    private int salesCount;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating;

    @Column(nullable = false)
    private int reviewCount;

    @Column(nullable = false)
    private boolean isDeleted;

    private LocalDateTime deletedAt;

    @Builder
    public Book(String title, String author, String publisher, String isbn, String category,
                int price, String coverImageUrl, String description,
                String detailedSynopsis, SaleStatus saleStatus, LocalDate publishedDate, int salesCount) {
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.isbn = isbn;
        this.category = category;
        this.price = price;
        this.coverImageUrl = coverImageUrl;
        this.description = description;
        this.detailedSynopsis = detailedSynopsis;
        this.saleStatus = saleStatus != null ? saleStatus : SaleStatus.ON_SALE;
        this.publishedDate = publishedDate;
        this.salesCount = salesCount;
        this.averageRating = BigDecimal.ZERO;
        this.reviewCount = 0;
        this.isDeleted = false;
    }

    public void update(String title, String author, String publisher, String isbn, String category,
                       int price, String coverImageUrl, String description, String detailedSynopsis,
                       SaleStatus saleStatus, LocalDate publishedDate) {
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.isbn = isbn;
        this.category = category;
        this.price = price;
        this.coverImageUrl = coverImageUrl;
        this.description = description;
        this.detailedSynopsis = detailedSynopsis;
        this.saleStatus = saleStatus;
        this.publishedDate = publishedDate;
    }

    public void delete(LocalDateTime deletedAt) {
        this.isDeleted = true;
        this.deletedAt = deletedAt;
        this.saleStatus = SaleStatus.STOPPED;
    }

    public void updateReviewStatistics(BigDecimal averageRating, int reviewCount) {
        this.averageRating = averageRating;
        this.reviewCount = reviewCount;
    }
}
