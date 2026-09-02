package com.bookeatinglion.ai.wiki.service;

import com.bookeatinglion.ai.client.MemberSubscriptionClient;
import com.bookeatinglion.ai.wiki.config.RagProperties;
import com.bookeatinglion.ai.wiki.port.VectorIndexPort;
import com.bookeatinglion.ai.wiki.port.VectorSearchPort;
import com.bookeatinglion.ai.wiki.port.VectorSearchPort.Match;
import com.bookeatinglion.ai.wiki.repository.WikiBookRepository;
import com.bookeatinglion.ai.wiki.router.QueryRouter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 읽을 수 있는 책 RAG. 계획서 §4 파이프라인의 구현이다.
 *
 * <p>
 * 🔴 <b>검색 권한의 근거는 "열람 권한"이다</b> — 구매했거나 구독 중이면 된다({@link
 * #allowedBooks}). 라이언에게 먹였는지(게이미피케이션)와는 별개다 — 사자를 안 키워도 볼 수
 * 있는 책은 물어볼 수 있어야 하고, 반대로 먹였다고 볼 권한이 생기지는 않는다. 그래서 필터는
 * {@code purchased_books}(+구독 시 {@code wiki_books}) 이지 {@code fed_books} 가 아니다.
 *
 * <p>
 * 문 두 개를 통과해야 LLM 을 부른다 — <b>(3) 볼 권한이 있는가</b>(열람 가능한 책과의 교집합),
 * <b>(7) 근거가 충분한가</b>(거리 가드). 근거 없이 LLM 을 부르면 반드시 지어내고,
 * 그건 프롬프트로 막을 수 없다.
 */
@Service
@RequiredArgsConstructor
public class WikiRagService {

    private static final Logger log = LoggerFactory.getLogger(WikiRagService.class);

    /**
     * grounded=false 일 때의 고정 문구. 여기서 LLM 을 부르지 않는 것이 핵심이다.
     *
     * <p>"구매한 책"이 아니라 "읽을 수 있는 책"이다 — 구독 회원은 구매하지 않은 책도 검색
     * 대상이므로(allowedBooks) 구매를 언급하면 사실과 다르다.
     */
    private static final String NOT_GROUNDED_ANSWER = "읽을 수 있는 책에서 근거를 찾지 못했습니다.";

    /** 응답에 싣는 인용문 길이. 청크 원문은 최대 4KB 라 그대로 실으면 응답이 비대해진다. */
    private static final int SNIPPET_MAX_CHARS = 200;

    private final PurchasedBookCache purchasedBookCache;
    private final MemberSubscriptionClient memberSubscriptionClient;
    private final WikiBookRepository wikiBookRepository;
    private final GuardedAiCalls ai;
    private final VectorSearchPort vectorSearch;
    private final QueryRouter queryRouter;
    private final RagProperties props;

    public record Citation(
            int ref, long bookId, String bookTitle, int page, String snippet, double score, String sourceType) {}

    public record AskResult(AskMode mode, boolean grounded, String answer, List<Citation> citations) {}

    public AskResult ask(String memberId, String query, AskMode requestedMode, List<Long> requestedBookIds, int topK) {
        AskMode mode = requestedMode != null ? requestedMode : queryRouter.route(query);

        // (3) 접근 제어. 여기가 비면 검색 자체를 하지 않는다.
        Set<Long> allowed = allowedBooks(memberId, requestedBookIds);
        if (allowed.isEmpty()) {
            log.info(
                    "검색 대상 없음 — 볼 수 있는 책이 없거나 요청 bookIds 와 교집합이 비었다. memberId={} 요청bookIds={}",
                    memberId,
                    requestedBookIds);
            return notGrounded(mode);
        }

        // (5)(6) 임베딩 → 검색. 둘 다 외부 API 라 실패를 degrade 로 흡수한다 —
        // AI 장애가 고객상담·주문으로 전이되면 안 되므로 503 이 아니라 grounded=false 다.
        List<Match> matches;
        try {
            matches = vectorSearch.search(ai.embed(query), allowed, props.searchTopK());
        } catch (RuntimeException e) {
            log.warn("임베딩/벡터 검색 실패 — grounded=false 로 낮춘다. memberId={}", memberId, e);
            return notGrounded(mode);
        }

        // 🔴 사자는 책 본문만 근거로 삼는다. 개인 메모(user_summary)는 인덱스에 남아 있어도
        // 인용하지 않는다 — "무슨 책이야?", "어느 책에서 ~부분?" 처럼 책을 묻는 질문에 남의
        // 문장도 내 문장도 아닌 내 감상이 섞여 나오면 출처가 흐려진다. 옛 벡터(필드 추가 전
        // 적재분)는 sourceType 이 null 이라 책 본문으로 남는다.
        //
        // 이 필터가 사라지면 예전에 적재된 메모 벡터가 다시 인용에 올라온다(개인 글이 같은
        // 책을 산 다른 회원에게 새는 경로이기도 하다) — 지우지 말 것.
        List<Match> bookContentOnly = matches.stream()
                .filter(match -> !VectorIndexPort.SOURCE_USER_SUMMARY.equals(match.sourceType()))
                .toList();

        // (7) 거리 가드. 임계값보다 먼 근거는 전부 버린다
        List<Match> near = bookContentOnly.stream()
                .filter(match -> match.distance() <= props.maxDistance())
                .toList();
        if (near.isEmpty()) {
            log.info(
                    "근거 부족 — LLM 을 호출하지 않는다. memberId={} 후보={} top1거리={}",
                    memberId,
                    matches.size(),
                    matches.isEmpty() ? null : matches.getFirst().distance());
            return notGrounded(mode);
        }

        // (8) 중복 병합 → 책당 상한 → topK → 컨텍스트 예산
        List<Match> grounds = select(near, topK);
        if (grounds.isEmpty()) {
            return notGrounded(mode);
        }
        List<Citation> citations = toCitations(grounds);

        // (9) answer 모드에서만 LLM 을 부른다.
        String answer = mode == AskMode.ANSWER ? ai.complete(systemPrompt(grounds), query) : summary(citations);

        return new AskResult(mode, true, answer, citations);
    }

    /**
     * 검색해도 되는 책. <b>구매한 책 ∪ (구독 중이면) 인제스트된 책 전체</b>다.
     *
     * <p>
     * 구독을 포함하는 이유는 열람 권한과 맞추기 위해서다 — {@code EbookService} 가 구독
     * 회원에게 eBook 보유 도서 <i>전체</i>의 열람을 허용한다. 전문을 읽을 수 있는 책의
     * 200자 인용을 막는 것은 더 좁은 권리가 아니라 설명할 수 없는 예외였다. 인제스트는
     * 도서 등록 시점에 이미 끝나 있으므로(AdminBookService#publishIngestEvent) 추가 비용도
     * 없다 — 질의 비용은 {@code DailyQuota} 가 통제한다.
     *
     * <p>
     * 🔴 {@code requestedBookIds} 는 <b>좁히기 전용</b>이다. 허용 집합과 교집합만 남긴다.
     * 합집합으로 구현하면 클라이언트가 아무 bookId 나 넣어 권한 없는 책의 본문을 그대로
     * 읽어갈 수 있다. 에러도 로그도 없이 정상 응답처럼 보이는 접근 제어 사고다.
     */
    private Set<Long> allowedBooks(String memberId, Collection<Long> requestedBookIds) {
        Set<Long> allowed = new LinkedHashSet<>(purchasedBookCache.purchasedBookIds(memberId));

        // 구매분만으로 요청 범위가 이미 채워지면 member-service 를 부르지 않는다 —
        // 뷰어에서 오는 질의는 대부분 자기가 산 책 하나로 좁혀져 있다(DailyQuota 가
        // 무료 상한 아래에서 구독 조회를 건너뛰는 것과 같은 이유).
        boolean coveredByPurchase =
                requestedBookIds != null && !requestedBookIds.isEmpty() && allowed.containsAll(requestedBookIds);
        if (!coveredByPurchase
                && memberSubscriptionClient.getSubscriptionStatus(memberId).subscribed()) {
            allowed.addAll(wikiBookRepository.findAllBookIds());
        }

        if (requestedBookIds == null || requestedBookIds.isEmpty()) {
            return allowed;
        }
        Set<Long> narrowed = new LinkedHashSet<>(requestedBookIds);
        narrowed.retainAll(allowed);
        return narrowed;
    }

    /**
     * 같은 (bookId, page) 는 하나로 병합하고, 한 책이 상위를 독점하지 못하게 책당 상한을 건다.
     *
     * <p>
     * 독점이 문제인 이유는 중복 자체가 아니라 <b>정답 책이 밀려나는 것</b>이다. 예컨대
     * "김첨지"는 「운수 좋은 날」과 「정규직 되는 날」 양쪽에 나오는데, 한쪽이 상위를 다 먹으면
     * LLM 은 판별할 재료조차 받지 못한다. 컨텍스트에 없는 것은 프롬프트로 판별시킬 수 없다.
     */
    private List<Match> select(List<Match> matches, int topK) {
        // 입력이 거리 오름차순이므로 putIfAbsent 가 곧 "페이지당 가장 가까운 청크"다.
        Map<PageKey, Match> nearestPerPage = new LinkedHashMap<>();
        for (Match match : matches) {
            nearestPerPage.putIfAbsent(new PageKey(match.bookId(), match.page()), match);
        }

        Map<Long, Integer> perBook = new HashMap<>();
        List<Match> capped = new ArrayList<>();
        for (Match match : nearestPerPage.values()) {
            if (perBook.merge(match.bookId(), 1, Integer::sum) <= props.maxCitationsPerBook()) {
                capped.add(match);
            }
        }

        // 컨텍스트 예산을 넘는 인용은 목록에서도 버린다. 프롬프트에 없는 근거에
        // [n] 을 달아 둘 수는 없다 — 인용 번호와 실제 컨텍스트는 항상 같아야 한다.
        //
        // 예산을 넘는 청크는 건너뛰기만 하고 멈추지는 않는다. 중단하면 큰 청크 하나
        // 뒤에 있는 작은 근거를 전부 잃는다. 인용 번호는 toCitations 가 다시 매기므로
        // 중간을 건너뛰어도 번호와 컨텍스트는 그대로 일치한다.
        List<Match> selected = new ArrayList<>();
        int budget = props.maxContextBytes();
        for (Match match : capped.subList(0, Math.min(topK, capped.size()))) {
            int cost = match.text().getBytes(StandardCharsets.UTF_8).length;
            if (cost > budget) {
                continue;
            }
            budget -= cost;
            selected.add(match);
        }
        return selected;
    }

    private static List<Citation> toCitations(List<Match> grounds) {
        List<Citation> citations = new ArrayList<>(grounds.size());
        for (int i = 0; i < grounds.size(); i++) {
            Match match = grounds.get(i);
            citations.add(new Citation(
                    i + 1,
                    match.bookId(),
                    match.bookTitle(),
                    match.page(),
                    snippet(match.text()),
                    score(match.distance()),
                    normalizedSourceType(match)));
        }
        return citations;
    }

    /** 옛 벡터(필드 추가 전 적재분)는 메타데이터에 sourceType이 없다 — 책 본문으로 취급한다. */
    private static String normalizedSourceType(Match match) {
        return match.sourceType() == null ? VectorIndexPort.SOURCE_BOOK_CONTENT : match.sourceType();
    }

    /**
     * 🔴 부호 반전 지점. S3 Vectors 는 거리(작을수록 유사)를 주고 계약은 점수(클수록 유사)를
     * 요구한다. 이 한 줄을 반대로 쓰면 관련 질문엔 "모르겠다"고 하고 무관한 질문엔 자신 있게
     * 답하는, 예외도 로그도 남지 않는 버그가 된다.
     */
    private static double score(double distance) {
        return Math.max(0.0, Math.min(1.0, 1.0 - distance));
    }

    /** 말줄임표까지 포함해 {@code SNIPPET_MAX_CHARS} 를 넘지 않는다. 계약이 "최대 200자"다. */
    private static String snippet(String text) {
        return text.length() <= SNIPPET_MAX_CHARS ? text : text.substring(0, SNIPPET_MAX_CHARS - 1) + "…";
    }

    private static String systemPrompt(List<Match> grounds) {
        StringBuilder prompt = new StringBuilder(
                """
                        아래 근거만 사용해 한국어로 답한다. 근거에 없는 내용은 만들지 않는다.
                        근거는 전부 책 본문 원문이다.
                        인용한 문장 끝에 [1], [2] 처럼 근거 번호를 붙인다.
                        어느 책의 몇 쪽인지 묻는 질문에는 근거의 책 제목과 쪽수를 그대로 답한다.
                        질문이 여러 책에 걸치면 되묻지 말고 책별로 나누어 한 번에 답한다.

                        """);
        for (int i = 0; i < grounds.size(); i++) {
            Match match = grounds.get(i);
            prompt.append("[%d] %s %d쪽%n%s%n%n".formatted(i + 1, match.bookTitle(), match.page(), match.text()));
        }
        return prompt.toString();
    }

    /** search 모드의 answer. LLM 을 부르지 않으므로 생성문이 아니다. */
    private static String summary(List<Citation> citations) {
        return "읽을 수 있는 책에서 근거 %d곳을 찾았습니다.".formatted(citations.size());
    }

    private static AskResult notGrounded(AskMode mode) {
        return new AskResult(mode, false, NOT_GROUNDED_ANSWER, List.of());
    }

    private record PageKey(long bookId, int page) {}
}
