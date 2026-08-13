package com.bookeatinglion.ai.ingest;

import com.bookeatinglion.ai.client.EmbeddingClient;
import com.bookeatinglion.ai.epub.EpubTextExtractor;
import com.bookeatinglion.ai.epub.TextSplitter;
import com.bookeatinglion.ai.wiki.domain.WikiBook;
import com.bookeatinglion.ai.wiki.domain.WikiBookChunk;
import com.bookeatinglion.ai.wiki.port.ObjectStoragePort;
import com.bookeatinglion.ai.wiki.port.VectorIndexPort;
import com.bookeatinglion.ai.wiki.port.VectorIndexPort.VectorRecord;
import com.bookeatinglion.ai.wiki.repository.WikiBookChunkRepository;
import com.bookeatinglion.ai.wiki.repository.WikiBookRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * EPUB 한 권을 검색 가능한 상태로 만든다: 다운로드 → 파싱 → 페이지/청크 → 임베딩 →
 * 벡터 적재 → {@code wiki_books} 등록.
 *
 * <p>🔴 <b>순서가 계약이다.</b> {@code wiki_books} 에 행이 있다는 것은 "먹을 수 있는 책"이라는
 * 뜻이고, 그게 곧 {@code feedable-books} 의 정의다. 그래서 벡터 적재와 건수 검증이 끝난
 * <b>뒤에만</b> 등록한다. 반대로 하면 벡터가 반만 들어간 책이 목록에 뜬다.
 *
 * <p>🔴 <b>멱등하다.</b> 벡터 키가 {@code {bookId}#{page}#{chunkSeq}} 로 결정적이고 적재 전에
 * 그 책의 기존 벡터를 지운다. 같은 메시지를 여러 번 받아도 결과가 같다 — SQS 는
 * at-least-once 라 이 성질이 없으면 중복 인제스트가 난다.
 */
@Service
@RequiredArgsConstructor
public class BookIngestService {

    private static final Logger log = LoggerFactory.getLogger(BookIngestService.class);

    private final ObjectStoragePort objectStorage;
    private final EpubTextExtractor extractor;
    private final TextSplitter splitter;
    private final EmbeddingClient embedding;
    private final VectorIndexPort vectorIndex;
    private final WikiBookRepository wikiBooks;
    private final WikiBookChunkRepository wikiBookChunks;
    private final WikiBookRegistrar registrar;

    public IngestResult ingest(IngestCommand command) {
        long started = System.currentTimeMillis();

        byte[] epub = objectStorage.download(command.epubS3Key());
        String hash = sha256(epub);

        // 🔴 임베딩이 유일하게 비싼 단계다. 청크당 1회이고 장편은 200~500청크라, 같은 파일이
        // 다시 와도 그대로 돌리면 전액 재과금된다. 재배달·재등록이 흔한 구조라 필수다.
        Optional<WikiBook> existing = wikiBooks.findById(command.bookId());
        existing.ifPresent(book -> guardAgainstIdCollision(book, command.title()));

        if (existing.isPresent() && hash.equals(existing.get().getSourceHash())) {
            WikiBook book = existing.get();
            log.info("원본이 그대로다 — 인제스트를 건너뛴다. bookId={} hash={}", command.bookId(), hash);
            return new IngestResult(command.bookId(), book.getPages(), book.getChunkCount(), 0, true);
        }

        List<String> pages = splitPages(command, epub);
        List<VectorRecord> records = embedAll(command, pages);

        replaceVectors(command.bookId(), records);
        verify(records);
        registrar.register(command.bookId(), command.title(), pages.size(), records, hash);

        long elapsed = System.currentTimeMillis() - started;
        log.info(
                "인제스트 완료 bookId={} \"{}\" 페이지={} 청크={} {}ms",
                command.bookId(),
                command.title(),
                pages.size(),
                records.size(),
                elapsed);
        return new IngestResult(command.bookId(), pages.size(), records.size(), elapsed, false);
    }

    /**
     * 이미 페이지·청크로 나뉜 자료를 적재한다. JSONL 코퍼스 배치({@code WikiIngestRunner})가
     * 쓰는 경로다 — 분할만 다르고 적재·검증·등록은 EPUB 경로와 같아야 한다.
     *
     * <p>원본 파일 단위가 아니라 {@code sourceHash} 를 남기지 않는다. 그래서 재실행하면 항상
     * 다시 임베딩한다 — 코퍼스는 5권 46청크라 그 편이 단순하다.
     */
    public IngestResult ingestPreSplit(long bookId, String title, String category, List<PageChunk> chunks) {
        long started = System.currentTimeMillis();

        wikiBooks.findById(bookId).ifPresent(book -> guardAgainstIdCollision(book, title));

        List<VectorRecord> records = chunks.stream()
                .map(c -> new VectorRecord(
                        VectorIndexPort.key(bookId, c.page(), c.chunkSeq()),
                        embedding.embed(c.text()),
                        bookId,
                        title,
                        category == null ? "" : category,
                        c.page(),
                        c.text()))
                .toList();

        int pages = (int) chunks.stream().map(PageChunk::page).distinct().count();

        replaceVectors(bookId, records);
        verify(records);
        registrar.register(bookId, title, pages, records, null);

        long elapsed = System.currentTimeMillis() - started;
        log.info("인제스트 완료 bookId={} \"{}\" 페이지={} 청크={} {}ms", bookId, title, pages, records.size(), elapsed);
        return new IngestResult(bookId, pages, records.size(), elapsed, false);
    }

    /**
     * 🔴 같은 {@code bookId} 에 다른 책이 오면 시끄럽게 실패한다.
     *
     * <p>이게 없으면 조용히 덮어쓴다: 기존 벡터가 삭제되고, {@code wiki_books} 제목이 바뀌고,
     * 그 책을 먹었던 사용자({@code fed_books})는 먹은 적 없는 책을 먹은 상태가 된다. 에러도
     * 로그도 남지 않아 "왜 엉뚱한 책이 인용되지" 로만 드러난다.
     *
     * <p>실제로 데모 코퍼스(bookId 1~5)와 {@code catalog_db.books}(IDENTITY, 1부터)가 정면으로
     * 충돌했다. 코퍼스를 900001~ 예약 대역으로 옮겨 해결했지만, ID 재사용 같은 다른 원인으로
     * 같은 사고가 날 수 있어 방어선을 남긴다.
     *
     * <p>제목이 바뀐 정상적인 경우(오탈자 수정, 개정판)에는 이 예외가 걸린다. 그때는 해당
     * {@code wiki_books} 행을 지우고 다시 인제스트한다 — 덮어쓰기를 기본 동작으로 두는 것보다
     * 한 번 막히는 편이 낫다.
     */
    private static void guardAgainstIdCollision(WikiBook existing, String incomingTitle) {
        if (!existing.getTitle().equals(incomingTitle)) {
            throw new IllegalStateException("bookId %d 가 이미 다른 책에 쓰이고 있다: 기존 \"%s\", 요청 \"%s\". ID 배정을 확인할 것."
                    .formatted(existing.getBookId(), existing.getTitle(), incomingTitle));
        }
    }

    private List<String> splitPages(IngestCommand command, byte[] epub) {
        String body = extractor.extract(epub);
        List<String> paragraphs = splitter.normalize(body);
        List<String> pages = splitter.splitPages(paragraphs, TextSplitter.DEFAULT_PAGE_SIZE);
        if (pages.isEmpty()) {
            throw new IllegalStateException("본문을 추출하지 못했다: " + command.epubS3Key());
        }
        return pages;
    }

    /**
     * 페이지를 청크로 나누고 각 청크를 임베딩한다.
     *
     * <p>{@code chunkSeq} 는 페이지 안에서만 증가한다 — 청크가 페이지 경계를 넘지 않으므로
     * 인용의 페이지 번호가 항상 하나로 정해진다.
     */
    private List<VectorRecord> embedAll(IngestCommand command, List<String> pages) {
        List<VectorRecord> records = new ArrayList<>(pages.size());
        for (int i = 0; i < pages.size(); i++) {
            int pageNo = i + 1;
            List<String> chunks = splitter.chunkPage(pages.get(i));
            for (int seq = 0; seq < chunks.size(); seq++) {
                String text = chunks.get(seq);
                records.add(new VectorRecord(
                        VectorIndexPort.key(command.bookId(), pageNo, seq),
                        embedding.embed(text),
                        command.bookId(),
                        command.title(),
                        command.category() == null ? "" : command.category(),
                        pageNo,
                        text));
            }
        }
        return records;
    }

    /**
     * delete-then-put. 삭제 대상은 {@code wiki_book_chunks} 에서 가져온다 — 인덱스를 전수
     * 스캔하지 않기 위해서다.
     *
     * <p>페이지 규칙이 바뀌면 청크 수가 달라져 옛 키가 남는데, 그대로 두면 같은 문장이 두
     * 페이지 번호로 인용된다.
     */
    private void replaceVectors(long bookId, List<VectorRecord> records) {
        List<String> stale = wikiBookChunks.findByBookId(bookId).stream()
                .map(WikiBookChunk::vectorKey)
                .toList();
        if (!stale.isEmpty()) {
            vectorIndex.delete(stale);
            log.info("기존 벡터 삭제 bookId={} {}건", bookId, stale.size());
        }
        vectorIndex.put(records);
    }

    /**
     * 적재 후 재조회해 건수를 대조한다. 이게 없으면 부분 실패가 성공으로 끝나고, 사용자에게는
     * "그 페이지는 못 찾겠다"로만 보인다.
     */
    private void verify(List<VectorRecord> records) {
        List<String> keys = records.stream().map(VectorRecord::key).toList();
        long actual = vectorIndex.countExisting(keys);
        if (actual != keys.size()) {
            throw new IllegalStateException("적재 건수 불일치: 기대 %d, 실제 %d".formatted(keys.size(), actual));
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", e);
        }
    }

    public record IngestCommand(long bookId, String title, String category, String epubS3Key) {}

    /** 사전 분할된 청크 하나. 페이지 번호는 인용에 그대로 나간다. */
    public record PageChunk(int page, int chunkSeq, String text) {}

    public record IngestResult(long bookId, int pages, int chunks, long elapsedMillis, boolean skipped) {}
}
