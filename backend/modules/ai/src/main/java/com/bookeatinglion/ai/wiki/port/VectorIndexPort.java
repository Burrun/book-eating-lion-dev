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

    /** 키 규칙. 이 형식이 깨지면 {@link #deleteByBook} 가 옛 벡터를 못 지우고 조용히 고아가 된다. */
    static String key(long bookId, int page, int chunkSeq) {
        return bookId + "#" + page + "#" + chunkSeq;
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
     */
    record VectorRecord(
            String key, float[] embedding, long bookId, String bookTitle, String category, int page, String text) {}
}
