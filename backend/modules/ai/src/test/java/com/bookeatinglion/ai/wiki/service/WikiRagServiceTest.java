package com.bookeatinglion.ai.wiki.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.ai.wiki.config.RagProperties;
import com.bookeatinglion.ai.wiki.port.VectorSearchPort;
import com.bookeatinglion.ai.wiki.port.VectorSearchPort.Match;
import com.bookeatinglion.ai.wiki.router.QueryRouter;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 계획서 §9 "조용히 틀리는 것들". 에러도 안 나고 로그도 깨끗한데 결과만 틀리는 항목이라
 * 테스트가 유일한 방어선이다.
 */
class WikiRagServiceTest {

    private static final String MEMBER_ID = "test-cognito-sub-1";

    // 쿼터 두 값은 이 테스트가 안 쓴다(DailyQuota 는 AskController 단계라 WikiRagService 밖이다).
    private static final RagProperties PROPS = new RagProperties(8, 3, 0.75, 12288, 5, 50);

    private PurchasedBookCache purchasedBookCache;
    private GuardedAiCalls ai;
    private VectorSearchPort vectorSearch;
    private WikiRagService service;

    @BeforeEach
    void setUp() {
        purchasedBookCache = mock(PurchasedBookCache.class);
        ai = mock(GuardedAiCalls.class);
        vectorSearch = mock(VectorSearchPort.class);

        when(ai.embed(anyString())).thenReturn(new float[1024]);
        when(ai.complete(anyString(), anyString())).thenReturn("생성된 답변 [1].");

        service = new WikiRagService(purchasedBookCache, ai, vectorSearch, new QueryRouter(), PROPS);
    }

    private static Match match(long bookId, int page, double distance) {
        return new Match(bookId, "책" + bookId, page, "본문 " + bookId + "-" + page, distance, null, null);
    }

    private void fed(Long... bookIds) {
        when(purchasedBookCache.purchasedBookIds(MEMBER_ID)).thenReturn(Set.of(bookIds));
    }

    private void found(Match... matches) {
        when(vectorSearch.search(any(), any(), anyInt())).thenReturn(List.of(matches));
    }

    @SuppressWarnings("unchecked")
    private Collection<Long> capturedFilter() {
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(vectorSearch).search(any(), captor.capture(), anyInt());
        return captor.getValue();
    }

    /** T1 — 안 먹인 책은 검색 필터에 아예 들어가지 않는다. 이게 뚫리면 접근 제어 사고다. */
    @Test
    void 안_먹인_책은_검색_대상에서_빠진다() {
        fed(1L);
        found(match(1L, 1, 0.2));

        service.ask(MEMBER_ID, "김첨지", AskMode.SEARCH, null, 5);

        assertThat(capturedFilter()).containsExactly(1L);
    }

    /** T2 — bookIds 는 좁히기 전용이다. 안 먹은 책을 넣어도 넓혀지지 않는다. */
    @Test
    void 요청한_bookIds_로_검색_범위를_넓힐_수_없다() {
        fed(1L, 2L);
        found(match(1L, 1, 0.2));

        service.ask(MEMBER_ID, "김첨지", AskMode.SEARCH, List.of(1L, 3L, 99L), 5);

        assertThat(capturedFilter()).containsExactly(1L);
    }

    @Test
    void 먹은_책이_없으면_검색조차_하지_않는다() {
        fed();

        WikiRagService.AskResult result = service.ask(MEMBER_ID, "김첨지", AskMode.ANSWER, null, 5);

        assertThat(result.grounded()).isFalse();
        assertThat(result.citations()).isEmpty();
        verify(vectorSearch, never()).search(any(), any(), anyInt());
        verify(ai, never()).complete(anyString(), anyString());
    }

    /** T3 — 근거가 멀면 LLM 을 부르지 않는다. 부르면 반드시 지어내고 그건 프롬프트로 못 막는다. */
    @Test
    void 근거가_임계값보다_멀면_LLM_을_호출하지_않는다() {
        fed(1L);
        found(match(1L, 1, 0.9)); // maxDistance 0.75 초과

        WikiRagService.AskResult result = service.ask(MEMBER_ID, "이진탐색", AskMode.ANSWER, null, 5);

        assertThat(result.grounded()).isFalse();
        assertThat(result.citations()).isEmpty();
        verify(ai, never()).complete(anyString(), anyString());
    }

    @Test
    void 검색_결과가_없으면_LLM_을_호출하지_않는다() {
        fed(1L);
        found();

        WikiRagService.AskResult result = service.ask(MEMBER_ID, "김첨지", AskMode.ANSWER, null, 5);

        assertThat(result.grounded()).isFalse();
        verify(ai, never()).complete(anyString(), anyString());
    }

    /** T4 — score 는 1 - distance 다. 부호를 반대로 쓰면 예외도 로그도 없이 결과만 뒤집힌다. */
    @Test
    void 가까운_근거일수록_점수가_높다() {
        fed(1L, 2L);
        found(match(1L, 1, 0.1), match(2L, 1, 0.6));

        List<WikiRagService.Citation> citations =
                service.ask(MEMBER_ID, "김첨지", AskMode.SEARCH, null, 5).citations();

        assertThat(citations.get(0).score()).isCloseTo(0.9, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(citations.get(1).score()).isCloseTo(0.4, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(citations.get(0).score()).isGreaterThan(citations.get(1).score());
    }

    /** T9 — 한 책이 상위를 독점하면 정답 책이 밀려나 LLM 이 판별할 재료조차 못 받는다. */
    @Test
    void 한_책이_인용을_독점하지_못한다() {
        fed(1L, 2L);
        found(
                match(1L, 1, 0.10),
                match(1L, 2, 0.11),
                match(1L, 3, 0.12),
                match(1L, 4, 0.13),
                match(1L, 5, 0.14),
                match(2L, 1, 0.50));

        List<WikiRagService.Citation> citations =
                service.ask(MEMBER_ID, "김첨지", AskMode.SEARCH, null, 5).citations();

        assertThat(citations).hasSize(4);
        assertThat(citations.stream().filter(c -> c.bookId() == 1L)).hasSize(3);
        assertThat(citations.stream().filter(c -> c.bookId() == 2L)).hasSize(1);
    }

    /** 같은 페이지가 여러 청크로 쪼개져 있어도 인용은 한 번만 나간다. */
    @Test
    void 같은_페이지는_한_번만_인용한다() {
        fed(1L);
        found(match(1L, 1, 0.10), match(1L, 1, 0.20), match(1L, 2, 0.30));

        List<WikiRagService.Citation> citations =
                service.ask(MEMBER_ID, "김첨지", AskMode.SEARCH, null, 5).citations();

        assertThat(citations).hasSize(2);
        assertThat(citations.get(0).page()).isEqualTo(1);
        assertThat(citations.get(1).page()).isEqualTo(2);
    }

    /** 벡터 검색 장애는 500 이 아니라 degrade 다. AI 장애가 나머지 기능으로 전이되면 안 된다. */
    @Test
    void 벡터_검색이_실패하면_500_이_아니라_grounded_false_다() {
        fed(1L);
        when(vectorSearch.search(any(), any(), anyInt())).thenThrow(new IllegalStateException("인덱스 장애"));

        WikiRagService.AskResult result = service.ask(MEMBER_ID, "김첨지", AskMode.ANSWER, null, 5);

        assertThat(result.grounded()).isFalse();
        verify(ai, never()).complete(anyString(), anyString());
    }

    /** search 모드는 비용이 0 이어야 한다. */
    @Test
    void search_모드는_LLM_을_부르지_않는다() {
        fed(1L);
        found(match(1L, 1, 0.2));

        WikiRagService.AskResult result = service.ask(MEMBER_ID, "김첨지 설렁탕", AskMode.SEARCH, null, 5);

        assertThat(result.grounded()).isTrue();
        assertThat(result.mode()).isEqualTo(AskMode.SEARCH);
        verify(ai, never()).complete(anyString(), anyString());
    }

    @Test
    void answer_모드는_근거를_프롬프트에_넣어_LLM_을_부른다() {
        fed(1L);
        found(match(1L, 7, 0.2));

        WikiRagService.AskResult result = service.ask(MEMBER_ID, "왜 그랬는지 설명해줘", null, null, 5);

        assertThat(result.mode()).isEqualTo(AskMode.ANSWER);
        assertThat(result.answer()).isEqualTo("생성된 답변 [1].");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ai).complete(prompt.capture(), anyString());
        assertThat(prompt.getValue()).contains("[1] 책1 7쪽").contains("본문 1-7");
    }

    /**
     * 사자는 책 본문만 인용한다. 내가 쓴 메모도 예외가 아니다 — 인덱스에 남아 있는 옛 메모
     * 벡터가 답변에 섞이면 "어느 책의 어느 대목인가"라는 질문의 출처가 흐려진다.
     */
    @Test
    void 메모_벡터는_내_것이든_남의_것이든_인용에서_빠진다() {
        fed(1L);
        Match others = new Match(1L, "책1", 3, "남의 메모", 0.15, "user_summary", "other-member-sub");
        Match mine = new Match(1L, "책1", 4, "내 메모", 0.16, "user_summary", MEMBER_ID);
        Match content = match(1L, 5, 0.2);
        found(others, mine, content);

        List<WikiRagService.Citation> citations =
                service.ask(MEMBER_ID, "이 책 내용 요약해줘", AskMode.SEARCH, null, 5).citations();

        assertThat(citations).hasSize(1);
        assertThat(citations.getFirst().snippet()).isEqualTo("본문 1-5");
        assertThat(citations.getFirst().sourceType()).isEqualTo("book_content");
    }

    /** 근거가 메모뿐이면 남는 게 없다 — LLM 을 부르지 않고 grounded=false 로 끝나야 한다. */
    @Test
    void 메모_벡터만_걸리면_근거_없음으로_끝난다() {
        fed(1L);
        found(new Match(1L, "책1", 3, "내 메모", 0.15, "user_summary", MEMBER_ID));

        WikiRagService.AskResult result = service.ask(MEMBER_ID, "왜 그랬는지 설명해줘", null, null, 5);

        assertThat(result.grounded()).isFalse();
        assertThat(result.citations()).isEmpty();
    }

    /** mode 를 생략하면 1층 규칙이 정한다. 설명 요구 신호가 없으면 싼 쪽(SEARCH)이다. */
    @Test
    void mode_생략_시_규칙이_모드를_정한다() {
        fed(1L);
        found(match(1L, 1, 0.2));

        assertThat(service.ask(MEMBER_ID, "김첨지 설렁탕", null, null, 5).mode()).isEqualTo(AskMode.SEARCH);
    }
}
