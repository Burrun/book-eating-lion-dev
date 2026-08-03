package com.bookeatinglion.book.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "books")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Book extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false)
    private String publisher;

    @Column(nullable = false, unique = true, length = 20)
    private String isbn;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int stockQuantity;

    private String coverImageUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String detailedSynopsis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SaleStatus saleStatus;

    @Column(nullable = false)
    private LocalDate publishedDate;

    @Column(nullable = false)
    private int salesCount;

    @Builder
    public Book(String title, String author, String publisher, String isbn, String category,
                int price, int stockQuantity, String coverImageUrl, String description,
                String detailedSynopsis, SaleStatus saleStatus, LocalDate publishedDate, int salesCount) {
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.isbn = isbn;
        this.category = category;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.coverImageUrl = coverImageUrl;
        this.description = description;
        this.detailedSynopsis = detailedSynopsis;
        this.saleStatus = saleStatus != null ? saleStatus : SaleStatus.ON_SALE;
        this.publishedDate = publishedDate;
        this.salesCount = salesCount;
    }
}
