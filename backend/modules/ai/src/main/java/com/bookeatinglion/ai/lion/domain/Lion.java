package com.bookeatinglion.ai.lion.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lion_id")
    private Long lionId;

    /** member_db 경계 밖. 값만 들고 있고 조인하지 않는다. */
    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private long exp;

    @Column(nullable = false)
    private String growthStage;

    public Lion(Long memberId, String growthStage) {
        this.memberId = memberId;
        this.level = 1;
        this.exp = 0;
        this.growthStage = growthStage;
    }
}
