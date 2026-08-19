package com.bookeatinglion.ai.lion.domain;

public enum GrowthStage {
    BABY,
    CUB,
    ADULT;

    /** 프론트 LionCharacter.jsx의 getLionTier(level)와 동일한 구간이어야 한다. */
    public static GrowthStage fromLevel(int level) {
        if (level <= 2) return BABY;
        if (level <= 4) return CUB;
        return ADULT;
    }
}
