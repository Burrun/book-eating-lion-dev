package com.bookeatinglion.book.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "faqs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Faq extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "faq_id")
    private Long faqId;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, length = 500)
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Builder
    public Faq(String category, String question, String answer, int sortOrder, boolean active) {
        this.category = category;
        this.question = question;
        this.answer = answer;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public void update(String category, String question, String answer, int sortOrder, boolean active) {
        this.category = category;
        this.question = question;
        this.answer = answer;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public void deactivate() {
        this.active = false;
    }
}
