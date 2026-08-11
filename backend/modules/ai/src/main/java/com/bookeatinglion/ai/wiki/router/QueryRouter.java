package com.bookeatinglion.ai.wiki.router;

import com.bookeatinglion.ai.wiki.service.AskMode;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * mode 를 생략했을 때 무엇을 할지 정한다.
 *
 * <p>
 * 지금은 1층(규칙)만 있다. 계획서 10단계의 2층(LLM 분류기)은 아직 없고, 규칙에 안 걸리면
 * SEARCH 로 떨어진다 — 싼 쪽이고, 프론트가 검색 응답 아래에 "더 자세히 답변받기" 버튼으로
 * {@code mode:"answer"} 재요청을 걸 수 있어서 사용자가 막히지 않는다.
 *
 * <p>
 * 판정을 로그로 남긴다. 1층 규칙을 다듬을 근거가 그것뿐이다.
 */
@Component
public class QueryRouter {

    private static final Logger log = LoggerFactory.getLogger(QueryRouter.class);

    /** 설명을 요구하는 신호. 이게 있으면 검색 결과만 돌려줘도 사용자가 원하는 답이 아니다. */
    private static final Pattern EXPLAIN = Pattern.compile("왜|이유|어째서|어떻게|무엇|때문|설명|요약|알려|말해|찾아|가르|도와");

    public AskMode route(String query) {
        AskMode mode = EXPLAIN.matcher(query).find() ? AskMode.ANSWER : AskMode.SEARCH;
        log.info("질의 라우팅: mode={} (1층 규칙)", mode);
        return mode;
    }
}
