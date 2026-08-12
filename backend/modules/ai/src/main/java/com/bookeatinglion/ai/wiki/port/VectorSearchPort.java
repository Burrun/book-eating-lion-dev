package com.bookeatinglion.ai.wiki.port;

import java.util.Collection;
import java.util.List;

/**
 * 벡터 검색. 구현은 apps/ai-api 에 있다 — 도메인은 S3 Vectors 를 몰라야 인제스트 저장소를
 * 바꿔도 파이프라인이 그대로 남는다.
 *
 * <p>{@code allowedBookIds} 는 **접근 제어 필터다.** 구현체는 이 목록을 반드시 검색 필터로
 * 내려야 하고, 받아온 결과를 사후 필터링하는 방식으로 대체해서는 안 된다 — topK 안에
 * 허용되지 않은 책이 들어차면 정작 볼 수 있는 책이 밀려난다.
 */
public interface VectorSearchPort {

    /**
     * @param allowedBookIds 검색 대상 책. **비어 있으면 호출하지 않는다** — 빈 필터는
     *     "제한 없음"으로 해석되어 전건 검색이 되고, 그게 곧 접근 제어 사고다.
     * @return 거리 오름차순(가까운 것 먼저). 결과가 없으면 빈 리스트.
     */
    List<Match> search(float[] queryVector, Collection<Long> allowedBookIds, int topK);

    /**
     * @param distance 거리다. **점수가 아니다** — 작을수록 유사하다. 응답의 score 로 뒤집는
     *     지점은 한 곳뿐이어야 해서 여기서는 SDK 가 준 값을 그대로 들고 다닌다.
     */
    record Match(long bookId, String bookTitle, int page, String text, double distance) {}
}
