package com.bookeatinglion.book.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recommendation_exposures")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationExposure extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exposure_id")
    private Long exposureId;

    @Column(name = "queue_id", nullable = false)
    private UUID queueId;

    @Column(name = "member_id", nullable = false)
    private String memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "position", nullable = false)
    private int position;

    public RecommendationExposure(UUID queueId, String memberId, Book book, int position) {
        this.queueId = queueId;
        this.memberId = memberId;
        this.book = book;
        this.position = position;
    }
}
