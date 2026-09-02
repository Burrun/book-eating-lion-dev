package com.bookeatinglion.ai.wiki.port;

import java.util.List;

/**
 * 벡터 인덱스 쓰기. 읽기는 {@link VectorSearchPort} 가 맡는다 — 서빙 경로에 쓰기 API 가
 * 딸려 들어가지 않도록 나눠 둔다.
 *
 * <p>🔴 <b>재적재는 delete-then-put 이다.</b> PutVectors 는 같은 키만 덮어쓴다. 페이지 크기를
 * 900자→600자로 바꾸면 12→18페이지가 되고 옛 벡터가 남아 같은 문장이 두 페이지 번호로
 * 인용된다. 그래서 책 단위로 먼저 지우고 넣는다.
 */
public interface VectorIndexPort {

    /** 책 본문 청크(관리자 배치 인제스트)의 기본 sourceType. */
    String SOURCE_BOOK_CONTENT = "book_content";

    /** 완독 후 사용자가 쓴 요약 메모의 sourceType. */
    String SOURCE_USER_SUMMARY = "user_summary";

    /** 키 규칙. 이 형식이 깨지면 {@link #deleteByBook} 가 옛 벡터를 못 지우고 조용히 고아가 된다. */
    static String key(long bookId, int page, int chunkSeq) {
        return bookId + "#" + page + "#" + chunkSeq;
    }

    /**
     * 메모 벡터 키. 책 본문 키({@code {bookId}#{page}#{chunkSeq}}, 전부 숫자)와 구분되도록
     * {@code memo#} 접두사를 붙인다. 회원×도서당 메모가 1개뿐이라 결정적이고, 재작성해도
     * 같은 키로 그대로 덮어써진다(PutVectors는 같은 키를 덮어쓴다) — 책 본문처럼
     * delete-then-put이 필요 없다.
     */
    static String memoKey(long bookId, String memberId) {
        return "memo#" + bookId + "#" + memberId;
    }

    /**
     * 주어진 키를 지운다. 없는 키가 섞여 있어도 무해하다.
     *
     * <p>🔴 <b>"그 책의 벡터를 전부"가 아니라 "이 키들"이다.</b> {@code ListVectors} 에는 prefix
     * 파라미터가 없어서 책 단위 삭제를 구현하려면 인덱스를 전수 스캔해야 하고, 그러면 책
     * 1권 인제스트가 인덱스 크기에 선형으로 느려진다. 삭제 대상은 호출자가
     * {@code wiki_book_chunks} 에서 가져온다.
     */
    void delete(List<String> keys);

    /** 적재. 구현체가 배치 크기를 알아서 나눈다. */
    void put(List<VectorRecord> vectors);

    /**
     * 주어진 키 중 실제로 인덱스에 있는 개수. 부분 실패가 성공으로 끝나지 않게 대조하는 데 쓴다.
     * 전수 스캔이 아니라 우리가 넣은 키만 조회한다.
     */
    long countExisting(List<String> keys);

    /**
     * @param page 인용에 그대로 나가는 값이다. 청크는 페이지 경계를 절대 넘지 않는다.
     * @param sourceType {@link #SOURCE_BOOK_CONTENT} 또는 {@link #SOURCE_USER_SUMMARY}.
     * @param memberId {@link #SOURCE_USER_SUMMARY}일 때만 값이 있다(작성자). 책 본문은
     *     누가 봐도 같은 벡터라 null이다 — 검색 시 이 필드로 "내가 쓴 메모만" 가려낸다
     *     (WikiRagService, 접근 제어가 bookId만으로는 안 되는 유일한 케이스).
     */
    record VectorRecord(
            String key,
            float[] embedding,
            long bookId,
            String bookTitle,
            String category,
            int page,
            String text,
            String sourceType,
            String memberId) {

        /** 책 본문 청크(관리자 배치 인제스트) 전용 편의 생성자 — 기존 호출부를 그대로 둔다. */
        public VectorRecord(
                String key, float[] embedding, long bookId, String bookTitle, String category, int page, String text) {
            this(key, embedding, bookId, bookTitle, category, page, text, SOURCE_BOOK_CONTENT, null);
        }
    }
}
