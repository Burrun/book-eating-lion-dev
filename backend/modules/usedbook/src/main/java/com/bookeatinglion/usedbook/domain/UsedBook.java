package com.bookeatinglion.usedbook.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "used_books")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UsedBook extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "used_book_id")
    private Long id;

    @Column(nullable = false)
    private String sellerId;

    @Column(nullable = false, length = 20)
    private String isbn;

    @Column(nullable = false)
    private String title;

    private String author;

    private String publisher;

    private String coverImageUrl;

    @Column(nullable = false)
    private int price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UsedBookCondition condition;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UsedBookStatus status;

    @ElementCollection
    @CollectionTable(name = "used_book_images", joinColumns = @JoinColumn(name = "used_book_id"))
    @OrderColumn(name = "image_order")
    @Column(name = "image_url", nullable = false)
    private List<String> imageUrls = new ArrayList<>();

    @Builder
    public UsedBook(String sellerId, String isbn, String title, String author, String publisher,
                     String coverImageUrl, int price, UsedBookCondition condition, String description,
                     List<String> imageUrls) {
        this.sellerId = sellerId;
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.coverImageUrl = coverImageUrl;
        this.price = price;
        this.condition = condition;
        this.description = description;
        this.status = UsedBookStatus.ON_SALE;
        this.imageUrls = imageUrls != null ? new ArrayList<>(imageUrls) : new ArrayList<>();
    }
}
