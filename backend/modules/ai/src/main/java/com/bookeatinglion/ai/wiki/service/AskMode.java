package com.bookeatinglion.ai.wiki.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** 계약(ai-v1.yaml)의 {@code mode} 값. JSON 표기는 소문자다. */
public enum AskMode {

    /** 검색만. LLM 을 부르지 않으므로 비용 0. */
    SEARCH,

    /** 검색 결과를 LLM 이 종합해 문장으로 답한다. */
    ANSWER;

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static AskMode from(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
