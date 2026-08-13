package com.bookeatinglion.book.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_inquiries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductInquiry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inquiry_id")
    private Long inquiryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "member_id", nullable = false, length = 255)
    private String memberId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_private", nullable = false)
    private boolean privateInquiry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryStatus status;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(name = "answered_by", length = 255)
    private String answeredBy;

    private LocalDateTime answeredAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    private LocalDateTime deletedAt;

    @Builder
    public ProductInquiry(Book book, String memberId, String title, String content, boolean privateInquiry) {
        this.book = book;
        this.memberId = memberId;
        this.title = title;
        this.content = content;
        this.privateInquiry = privateInquiry;
        this.status = InquiryStatus.WAITING;
    }

    public void update(String title, String content, boolean privateInquiry) {
        this.title = title;
        this.content = content;
        this.privateInquiry = privateInquiry;
    }

    public void answer(String answer, String answeredBy, LocalDateTime answeredAt) {
        this.answer = answer;
        this.answeredBy = answeredBy;
        this.answeredAt = answeredAt;
        this.status = InquiryStatus.ANSWERED;
    }

    public void delete(LocalDateTime deletedAt) {
        this.deleted = true;
        this.deletedAt = deletedAt;
    }
}
