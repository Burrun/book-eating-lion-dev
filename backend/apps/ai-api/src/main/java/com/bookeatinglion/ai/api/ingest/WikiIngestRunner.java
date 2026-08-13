package com.bookeatinglion.ai.api.ingest;

import com.bookeatinglion.ai.ingest.BookIngestService;
import com.bookeatinglion.ai.ingest.BookIngestService.IngestResult;
import com.bookeatinglion.ai.ingest.BookIngestService.PageChunk;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 위키 인제스트 배치 — JSONL 코퍼스를 임베딩해 {@code wiki-v1} 에 적재한다.
 *
 * <p>서빙 이미지와 같은 이미지에서 돈다. 기동 인자만 다르다:
 *
 * <pre>
 * --app.ai.ingest.enabled=true \
 * --app.ai.ingest.corpus=/corpus/wiki.jsonl \
 * --app.ai.clients=bedrock \
 * --server.port=0
 * </pre>
 *
 * <p><b>{@code web-application-type=none} 을 쓰지 않는다.</b> 그러면 Boot 의 {@code JwtDecoder}
 * 자동설정이 {@code @ConditionalOnWebApplication(SERVLET)} 이라 사라지는데 {@code SecurityConfig}
 * 는 그대로 로드돼 기동이 깨진다. 서버는 그냥 뜨게 두고 임의 포트를 준다 — Job 을 끝내는 건
 * 서버 부재가 아니라 아래의 명시적 {@code System.exit} 다.
 *
 * <p>적재·검증·등록은 {@link BookIngestService} 가 한다. EPUB 자동 인제스트와 같은 경로를
 * 쓰는 것이 중요하다 — delete-then-put 규칙이나 키 형식이 두 벌로 갈라지면 한쪽만 고친
 * 버그가 다른 쪽에 남는다. 이 클래스는 <b>코퍼스를 읽어 넘기는 일만</b> 한다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.ingest.enabled", havingValue = "true")
public class WikiIngestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WikiIngestRunner.class);

    private final ApplicationContext context;
    private final BookIngestService ingestService;
    private final ObjectMapper mapper;
    private final Path corpus;

    public WikiIngestRunner(
            ApplicationContext context,
            BookIngestService ingestService,
            ObjectMapper mapper,
            @Value("${app.ai.ingest.corpus}") String corpus) {
        this.context = context;
        this.ingestService = ingestService;
        this.mapper = mapper;
        this.corpus = Path.of(corpus);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<Chunk> chunks = readCorpus();
        Map<Long, List<Chunk>> byBook =
                chunks.stream().collect(Collectors.groupingBy(Chunk::bookId, LinkedHashMap::new, Collectors.toList()));

        log.info("인제스트 시작 corpus={} 책={} 청크={}", corpus, byBook.size(), chunks.size());

        int totalChunks = 0;
        byBook.forEach((bookId, bookChunks) -> verifyKeys(bookChunks));
        for (Map.Entry<Long, List<Chunk>> entry : byBook.entrySet()) {
            IngestResult result = ingest(entry.getValue());
            totalChunks += result.chunks();
        }

        log.info("인제스트 완료. 책 {}권 / 청크 {}건", byBook.size(), totalChunks);

        // 웹 서버가 떠 있으면 Job 이 끝나지 않는다. 컨텍스트를 닫고 0으로 나간다.
        System.exit(SpringApplication.exit(context, () -> 0));
    }

    private IngestResult ingest(List<Chunk> bookChunks) {
        Chunk first = bookChunks.getFirst();
        List<PageChunk> pages = bookChunks.stream()
                .map(c -> new PageChunk(c.page(), c.chunkSeq(), c.text()))
                .toList();
        return ingestService.ingestPreSplit(first.bookId(), first.bookTitle(), first.category(), pages);
    }

    private List<Chunk> readCorpus() throws Exception {
        List<String> lines = Files.readAllLines(corpus, StandardCharsets.UTF_8);
        List<Chunk> chunks = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (!line.isBlank()) {
                chunks.add(mapper.readValue(line, Chunk.class));
            }
        }
        if (chunks.isEmpty()) {
            throw new IllegalStateException("코퍼스가 비었다: " + corpus);
        }
        return chunks;
    }

    /**
     * 코퍼스가 준 키를 그대로 믿지 않는다. 키 규칙이 깨진 항목이 섞이면 그 책의 옛 벡터가
     * 영원히 안 지워지고 조용히 고아가 된다.
     */
    private static void verifyKeys(List<Chunk> chunks) {
        for (Chunk c : chunks) {
            String expected = c.bookId() + "#" + c.page() + "#" + c.chunkSeq();
            if (!expected.equals(c.key())) {
                throw new IllegalStateException("키 규칙 위반: 기대 %s, 실제 %s".formatted(expected, c.key()));
            }
        }
    }

    /** 코퍼스 한 줄. {@code author} {@code license} 는 적재하지 않아 매핑하지 않는다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Chunk(long bookId, String bookTitle, String category, int page, int chunkSeq, String key, String text) {}
}
