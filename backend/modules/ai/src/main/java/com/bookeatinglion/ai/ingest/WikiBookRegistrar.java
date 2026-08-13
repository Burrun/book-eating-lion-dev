package com.bookeatinglion.ai.ingest;

import com.bookeatinglion.ai.wiki.domain.WikiBook;
import com.bookeatinglion.ai.wiki.domain.WikiBookChunk;
import com.bookeatinglion.ai.wiki.port.VectorIndexPort.VectorRecord;
import com.bookeatinglion.ai.wiki.repository.WikiBookChunkRepository;
import com.bookeatinglion.ai.wiki.repository.WikiBookRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code wiki_books} + {@code wiki_book_chunks} 를 한 트랜잭션에 쓴다.
 *
 * <p>🔴 <b>{@link BookIngestService} 안으로 인라인하지 말 것.</b> {@code @Transactional} 은
 * Spring AOP 프록시를 통해야 걸린다. 같은 클래스에서 부르면 자기 호출이 되어 프록시를
 * 거치지 않고, 애너테이션은 그대로인데 트랜잭션만 조용히 사라진다.
 *
 * <p>둘이 같이 커밋돼야 하는 이유: 청크 목록만 남고 책 행이 없으면 다음 재적재가 삭제
 * 대상을 못 찾아 옛 벡터가 인덱스에 고아로 남는다.
 */
@Component
@RequiredArgsConstructor
public class WikiBookRegistrar {

    private final WikiBookRepository wikiBooks;
    private final WikiBookChunkRepository wikiBookChunks;

    @Transactional
    public void register(long bookId, String title, int pages, List<VectorRecord> records, String sourceHash) {
        wikiBooks.save(new WikiBook(bookId, title, pages, records.size(), sourceHash, LocalDateTime.now()));

        wikiBookChunks.deleteByBookId(bookId);
        wikiBookChunks.saveAll(records.stream()
                .map(r -> new WikiBookChunk(r.bookId(), r.page(), seqOf(r.key())))
                .toList());
    }

    private static int seqOf(String key) {
        return Integer.parseInt(key.substring(key.lastIndexOf('#') + 1));
    }
}
