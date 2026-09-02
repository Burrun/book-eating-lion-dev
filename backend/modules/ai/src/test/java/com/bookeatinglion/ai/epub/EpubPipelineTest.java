package com.bookeatinglion.ai.epub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 샘플 EPUB 로 추출→분할→청킹 불변식을 검증한다.
 *
 * <p>샘플은 {@code docs/ingest/samples} 에 있고 이 경로는 {@code .git/info/exclude} 로 제외돼
 * 있어 클론한 사람에게는 없다. 그래서 파일이 없으면 테스트를 건너뛴다.
 */
class EpubPipelineTest {

    private static final int MAX_CHUNK_BYTES = 4096;

    private final EpubTextExtractor extractor = new EpubTextExtractor();
    private final TextSplitter splitter = new TextSplitter();

    @ParameterizedTest
    @ValueSource(strings = {"frankenstein.epub", "alice-in-wonderland.epub"})
    void 추출과_분할이_불변식을_지킨다(String fileName) throws Exception {
        Path sample = samplesDir().resolve(fileName);
        assumeTrue(Files.exists(sample), "샘플 없음: " + sample);

        String body = extractor.extract(Files.readAllBytes(sample));

        List<String> paragraphs = splitter.normalize(body);
        List<String> pages = splitter.splitPages(paragraphs, TextSplitter.DEFAULT_PAGE_SIZE);

        assertThat(pages).isNotEmpty();

        int chunkCount = 0;
        for (String page : pages) {
            List<String> chunks = splitter.chunkPage(page);
            for (String chunk : chunks) {
                // I1: 청크는 자기 페이지 안에 완전히 들어있다 — 페이지 경계를 넘지 않는다
                assertThat(page).contains(chunk);
                // I3: S3 Vectors 한도
                assertThat(chunk.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(MAX_CHUNK_BYTES);
                chunkCount++;
            }
        }

        // I2: 페이지를 이어붙이면 원문과 같다 — 유실/중복 0
        String rejoined = String.join("", pages).replace("\n", "").replace(" ", "");
        String original = String.join("", paragraphs).replace("\n", "").replace(" ", "");
        assertThat(rejoined).isEqualTo(original);

        // front matter 가 걸러졌는지 — 걸러지지 않으면 라이선스가 본문으로 인용된다
        assertThat(body).doesNotContain("START OF THE PROJECT GUTENBERG EBOOK");
        assertThat(body).doesNotContain("END OF THE PROJECT GUTENBERG EBOOK");

        // 1페이지가 목차면 "이 책 내용" 질의에 목차가 인용된다
        assertThat(pages.getFirst().toUpperCase()).doesNotContain("CONTENTS");

        System.out.printf(
                "%-28s 문단 %5d  페이지 %4d  청크 %4d  글자 %,8d%n",
                fileName,
                paragraphs.size(),
                pages.size(),
                chunkCount,
                paragraphs.stream().mapToInt(String::length).sum());
    }

    /** 테스트 작업 디렉터리(모듈 루트)에서 위로 올라가며 리포 루트를 찾는다. */
    private static Path samplesDir() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("docs/ingest/samples"))) {
            dir = dir.getParent();
        }
        return dir == null ? Path.of("docs/ingest/samples") : dir.resolve("docs/ingest/samples");
    }
}
