package com.bookeatinglion.book.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "search_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "search_history_id")
    private Long searchHistoryId;

    @Column(name = "member_id", nullable = false)
    private String memberId;

    @Column(name = "query_text", nullable = false, length = 200)
    private String queryText;

    public SearchHistory(String memberId, String queryText) {
        this.memberId = memberId;
        this.queryText = queryText;
    }
}
