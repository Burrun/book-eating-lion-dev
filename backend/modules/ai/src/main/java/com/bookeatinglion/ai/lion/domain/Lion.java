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
    private String memberId;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private long exp;

    @Column(nullable = false)
    private String growthStage;

    public Lion(String memberId, String growthStage) {
        this.memberId = memberId;
        this.level = 1;
        this.exp = 0;
        this.growthStage = growthStage;
    }

    /** 책 1권을 먹였을 때 오르는 경험치. 100 당 1레벨이라 3권이면 레벨 2다. */
    public static final long EXP_PER_FEED = 40;

    private static final long EXP_PER_LEVEL = 100;

    /**
     * 먹이기 성공 시에만 부른다. 이미 먹인 책은 호출자가 먼저 걸러야 한다 —
     * 여기서는 중복 여부를 알 수 없고, fed_books 의 PK 가 그 판단의 유일한 근거다.
     */
    public void gainExp(long amount) {
        this.exp += amount;
        this.level = (int) (1 + this.exp / EXP_PER_LEVEL);
    }
}
