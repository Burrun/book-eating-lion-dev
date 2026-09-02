package com.bookeatinglion.ai.epub;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 본문 → 페이지 → 청크. {@code scripts/build-corpus-jsonl.py} 의 [2][3][4] 단계를 그대로 옮긴 것이다.
 *
 * <p>규칙을 바꾸면 페이지 번호가 전부 바뀌고, 인용의 {@code page} 가 어긋난다. 재인제스트가
 * delete-then-put 이어야 하는 이유가 이것이다.
 *
 * <p>🔴 <b>청크는 페이지 경계를 절대 넘지 않는다.</b> 한 벡터가 두 페이지에 걸치면 인용에
 * 페이지 번호를 쓸 수 없다.
 *
 * <p>참고: 900자 페이지는 UTF-8 로 최대 2.7KB(한글 기준)라 {@link #MAX_CHUNK_BYTES} 를 넘지
 * 않는다. 즉 {@link #chunkPage}는 현재 설정에서 항상 1개를 돌려주고 {@code chunkSeq} 는 0 이다.
 * 페이지 크기를 3,000자 이상으로 올릴 때를 위한 안전망이다.
 */
@Component
public class TextSplitter {

    /** 페이지 목표 길이. 바꾸면 전건 재인제스트가 필요하다. */
    public static final int DEFAULT_PAGE_SIZE = 900;

    /** 문장 끝을 찾느라 목표에서 벗어나도 되는 범위. */
    private static final int PAGE_TOLERANCE = 150;

    /** 청크 본문 상한. S3 Vectors 한도이자 컨텍스트 토큰 폭발 방지선. */
    private static final int MAX_CHUNK_BYTES = 4096;

    /** 최대 줄 길이가 이보다 짧으면 하드랩으로 본다. */
    private static final int HARDWRAP_MAX_LINE = 60;

    private static final String SENTENCE_END = ".?!”\"…」";

    /** 문장부호 뒤에 붙어 있으면 같은 페이지에 넣는다. 안 그러면 다음 페이지가 닫는 따옴표로 시작한다. */
    private static final String TRAILING_CLOSERS = "”\"’'」』)）";

    /** [2] 정규화. 하드랩 파일이면 문단 내 개행을 없앤다. */
    public List<String> normalize(String text) {
        String nfc = Normalizer.normalize(text.replace("\r\n", "\n").replace("\r", "\n"), Normalizer.Form.NFC);
        List<String> lines = List.of(nfc.split("\n", -1));

        List<String> paragraphs = isHardwrapped(lines) ? joinHardwrap(lines) : plainParagraphs(lines);

        List<String> cleaned = new ArrayList<>(paragraphs.size());
        for (String p : paragraphs) {
            String c = p.replaceAll("[ \t]+", " ").strip();
            if (!c.isEmpty()) {
                cleaned.add(c);
            }
        }
        return cleaned;
    }

    /**
     * [3] 페이지 분할. 900자 목표, 문장 끝에서 자른다.
     *
     * <p>문단 중간에서 잘려도 된다 — 실제 책도 그렇다. 다만 문장 중간에서 자르면 인용
     * 스니펫이 "...김첨지는 취한 채 집으" 처럼 끊겨 보인다.
     */
    public List<String> splitPages(List<String> paragraphs, int pageSize) {
        String body = String.join("\n", paragraphs);
        List<String> pages = new ArrayList<>();

        int pos = 0;
        while (pos < body.length()) {
            if (body.length() - pos <= pageSize + PAGE_TOLERANCE) {
                pages.add(body.substring(pos).strip());
                break;
            }
            int cut = findSentenceCut(body, pos, pageSize);
            pages.add(body.substring(pos, cut).strip());
            pos = cut;
        }

        pages.removeIf(String::isEmpty);
        return pages;
    }

    /** [4] 청킹. 페이지가 상한보다 짧으면 통째로 1청크다. 다음 페이지를 절대 끌어오지 않는다. */
    public List<String> chunkPage(String pageText) {
        if (utf8Length(pageText) <= MAX_CHUNK_BYTES) {
            return List.of(pageText);
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String sentence : pageText.split("(?<=[.!?…”」])\\s*")) {
            if (sentence.isEmpty()) {
                continue;
            }
            if (!cur.isEmpty() && utf8Length(cur + sentence) > MAX_CHUNK_BYTES) {
                chunks.add(cur.toString().strip());
                cur.setLength(0);
            }
            cur.append(sentence);
        }
        if (!cur.toString().isBlank()) {
            chunks.add(cur.toString().strip());
        }
        return chunks;
    }

    private static int findSentenceCut(String body, int pos, int pageSize) {
        int target = pos + pageSize;
        int lo = Math.max(pos + 1, target - PAGE_TOLERANCE);
        int hi = Math.min(body.length(), target + PAGE_TOLERANCE);

        int best = -1;
        for (int i = lo; i < hi; i++) {
            if (SENTENCE_END.indexOf(body.charAt(i)) >= 0
                    && (best < 0 || Math.abs(i - target) < Math.abs(best - target))) {
                best = i;
            }
        }
        if (best >= 0) {
            int cut = best + 1;
            while (cut < body.length() && TRAILING_CLOSERS.indexOf(body.charAt(cut)) >= 0) {
                cut++;
            }
            return cut;
        }

        // 문장 끝을 못 찾으면 공백에서, 그것도 없으면 목표 지점에서 자른다.
        int space = body.lastIndexOf(' ', hi - 1);
        return space >= lo && space > pos ? space + 1 : target;
    }

    /** 실측상 하드랩 45~52자 vs 자연문단 214~494자로 명확히 갈린다. */
    private static boolean isHardwrapped(List<String> lines) {
        int max = 0;
        for (String ln : lines) {
            if (!ln.isBlank()) {
                max = Math.max(max, ln.length());
            }
        }
        return max > 0 && max <= HARDWRAP_MAX_LINE;
    }

    private static List<String> plainParagraphs(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String ln : lines) {
            if (!ln.isBlank()) {
                out.add(ln.strip());
            }
        }
        return out;
    }

    /**
     * 문단 내 개행 제거. 한국어 하드랩은 단어 중간을 자르므로 공백 없이 붙인다. 다만 양쪽이
     * 라틴 문자/숫자면 원래 공백이 있던 자리이므로 공백을 넣는다.
     *
     * <p>문단 경계 신호 3가지: (a) 빈 줄 (b) 줄이 공백으로 시작 (c) 직전 줄이 최대폭보다 뚜렷이 짧다.
     */
    private static List<String> joinHardwrap(List<String> lines) {
        int width = 0;
        for (String ln : lines) {
            if (!ln.isBlank()) {
                width = Math.max(width, ln.length());
            }
        }
        double shortLine = width * 0.8;

        List<String> paragraphs = new ArrayList<>();
        List<String> buf = new ArrayList<>();

        for (String raw : lines) {
            if (raw.isBlank()) {
                flush(buf, paragraphs);
                continue;
            }
            boolean startsNew = Character.isWhitespace(raw.charAt(0))
                    || (!buf.isEmpty() && buf.getLast().length() < shortLine);
            if (startsNew) {
                flush(buf, paragraphs);
            }
            String piece = raw.strip();
            if (!buf.isEmpty() && needsSpace(buf.getLast(), piece)) {
                buf.add(" ");
            }
            buf.add(piece);
        }
        flush(buf, paragraphs);
        return paragraphs;
    }

    private static void flush(List<String> buf, List<String> paragraphs) {
        if (!buf.isEmpty()) {
            paragraphs.add(String.join("", buf));
            buf.clear();
        }
    }

    private static boolean needsSpace(String prev, String next) {
        if (prev.isEmpty() || next.isEmpty()) {
            return false;
        }
        char a = prev.charAt(prev.length() - 1);
        char b = next.charAt(0);
        return isAsciiAlnum(a) && isAsciiAlnum(b);
    }

    private static boolean isAsciiAlnum(char c) {
        return c < 128 && Character.isLetterOrDigit(c);
    }

    private static int utf8Length(CharSequence s) {
        return s.toString().getBytes(StandardCharsets.UTF_8).length;
    }
}
