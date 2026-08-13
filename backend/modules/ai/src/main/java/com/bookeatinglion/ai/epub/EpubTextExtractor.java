package com.bookeatinglion.ai.epub;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * EPUB 에서 본문만 순서대로 뽑는다.
 *
 * <p>🔴 <b>파일명 정렬로 읽으면 안 된다.</b> 읽기 순서는 OPF 의 {@code <spine>} 이 정한다.
 * 파일명 순으로 읽으면 챕터가 뒤섞이고 페이지 번호가 전부 틀리는데, 인용의 {@code page} 는
 * 이 프로젝트의 KPI 다. 그래서 container.xml → OPF → spine 을 따라간다.
 *
 * <p>🔴 <b>표지·목차·라이선스를 걸러야 한다.</b> Gutenberg EPUB 은 그것들이 본문과 같은
 * xhtml 로 들어 있어서, 그대로 넣으면 앞쪽 수십 페이지가 라이선스 전문이 되고 "이 책 내용"
 * 질의에 라이선스가 인용된다.
 */
@Component
public class EpubTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(EpubTextExtractor.class);

    private static final String CONTAINER_PATH = "META-INF/container.xml";

    /** Gutenberg 본문 경계 마커. 이 바깥은 헤더/라이선스다. */
    private static final String GUTENBERG_START = "*** START OF THE PROJECT GUTENBERG EBOOK";

    private static final String GUTENBERG_END = "*** END OF THE PROJECT GUTENBERG EBOOK";

    /**
     * 파일명에 이게 들어 있으면 본문이 아니다.
     *
     * <p>보조 수단일 뿐이다 — Gutenberg EPUB 은 파일명이 해시라 하나도 걸리지 않는다.
     * 실제 방어는 {@link #trimGutenberg}와 {@link #dropTableOfContents}가 한다.
     */
    private static final List<String> NON_CONTENT_HINTS =
            List.of("cover", "title", "toc", "nav", "contents", "copyright", "license", "colophon");

    /** 목차 heading 을 찾을 범위. 본문 한가운데의 "Contents" 를 잘못 잡지 않도록 앞쪽만 본다. */
    private static final int TOC_SCAN_LINES = 200;

    /** 이 길이를 넘으면 산문 문단으로 본다. 목차 항목은 이보다 훨씬 짧다. */
    private static final int PROSE_LINE_CHARS = 200;

    private static final Set<String> TOC_HEADINGS = Set.of("contents", "table of contents", "목차", "차례");

    /**
     * @return spine 순서대로의 본문 텍스트. 페이지 분할은 {@link TextSplitter} 가 한다.
     */
    public String extract(byte[] epub) {
        Map<String, byte[]> entries = readAll(epub);

        String opfPath = opfPath(entries);
        Document opf = parseXml(entries.get(opfPath), opfPath);
        String baseDir = parentOf(opfPath);

        List<String> spineHrefs = spineHrefs(opf, baseDir);
        if (spineHrefs.isEmpty()) {
            throw new IllegalStateException("EPUB spine 이 비었다: " + opfPath);
        }

        StringBuilder body = new StringBuilder();
        int skipped = 0;
        for (String href : spineHrefs) {
            byte[] raw = entries.get(href);
            if (raw == null) {
                log.warn("spine 이 가리키는 항목이 없다 — 건너뛴다. href={}", href);
                continue;
            }
            if (isNonContent(href)) {
                skipped++;
                continue;
            }
            String text = htmlToText(new String(raw, StandardCharsets.UTF_8));
            if (!text.isBlank()) {
                body.append(text).append('\n');
            }
        }

        log.info("EPUB 본문 추출: spine {}건 중 {}건 제외", spineHrefs.size(), skipped);
        return dropTableOfContents(trimGutenberg(body.toString()));
    }

    /**
     * 목차 블록을 잘라낸다.
     *
     * <p>🔴 파일명으로는 못 거른다. Gutenberg EPUB 은 파일명이
     * {@code 6955210388878302455_84-h-0.htm.html} 같은 해시이고, 목차와 본문이 <b>같은 파일</b>에
     * 앵커로 들어 있어 spine 단위로 자를 수도 없다. 그래서 텍스트에서 자른다.
     *
     * <p>규칙: 앞부분에 "Contents" 단독 줄이 있으면, 거기서부터 <b>첫 산문 문단</b>이 나올 때까지
     * 버린다. 목차 항목은 짧은 줄의 연속이고 본문은 긴 문단으로 시작하기 때문이다. 목차 heading
     * 이 없으면 아무것도 건드리지 않는다 — 잘못 자르면 1장이 통째로 사라진다.
     */
    private static String dropTableOfContents(String text) {
        String[] lines = text.split("\n", -1);
        int scanLimit = Math.min(lines.length, TOC_SCAN_LINES);

        int tocAt = -1;
        for (int i = 0; i < scanLimit; i++) {
            if (isTocHeading(lines[i])) {
                tocAt = i;
                break;
            }
        }
        if (tocAt < 0) {
            return text;
        }

        for (int i = tocAt + 1; i < lines.length; i++) {
            if (lines[i].strip().length() >= PROSE_LINE_CHARS) {
                log.info("목차 블록 제거: {}줄 (본문 시작 {}행)", i, i);
                return String.join("\n", List.of(lines).subList(i, lines.length));
            }
        }
        return text;
    }

    /**
     * 완전일치로는 부족하다. Frankenstein 은 머리글과 목차 heading 이 한 블록에 들어 있어
     * {@code "Frankenstein | Project Gutenberg    CONTENTS"} 한 줄로 나온다. 줄 끝으로 본다.
     */
    private static boolean isTocHeading(String line) {
        String norm = line.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
        return TOC_HEADINGS.stream().anyMatch(h -> norm.equals(h) || norm.endsWith(" " + h));
    }

    /**
     * xhtml → 줄 단위 텍스트.
     *
     * <p>🔴 {@code wholeText()} 만 쓰면 안 된다. 블록 요소를 줄바꿈으로 바꿔주지 않아서
     * 문단·목차 항목이 한 줄에 다 붙어 나오고, 그러면 목차 판별도 문단 분리도 되지 않는다.
     * 그렇다고 {@code text()} 를 쓰면 공백을 전부 하나로 접어버려 더 나쁘다.
     */
    private static String htmlToText(String html) {
        Document doc = Jsoup.parse(html);

        // 🔴 원본 xhtml 은 <p> 안에서 하드랩돼 있다. 그 개행을 공백으로 먼저 없애야
        // "블록 하나 = 한 줄" 이 성립한다. 안 그러면 산문 문단도 40자짜리 줄들로 쪼개져
        // 목차(짧은 줄)와 본문(긴 줄)을 길이로 구분할 수 없다.
        for (Element el : doc.getAllElements()) {
            for (TextNode tn : el.textNodes()) {
                tn.text(tn.getWholeText().replaceAll("[\\r\\n\\t]+", " "));
            }
        }

        for (Element br : doc.select("br")) {
            br.after(new TextNode("\n"));
        }
        for (Element block : doc.select("p, div, h1, h2, h3, h4, h5, h6, li, tr, blockquote, section")) {
            block.appendText("\n");
        }
        // &nbsp;(U+00A0)를 일반 공백으로 바꾼다. String.strip() 은 이걸 공백으로 보지 않아서,
        // 그대로 두면 "Contents" 한 줄이 " Contents " 로 남아 목차 판별이 빗나간다.
        return doc.wholeText().replace('\u00A0', ' ');
    }

    /** zip 은 스트림으로만 열린다. 항목이 수십 개라 통째로 메모리에 올려도 무리가 없다. */
    private static Map<String, byte[]> readAll(byte[] epub) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(epub), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("EPUB 을 열 수 없다 (zip 이 아니거나 손상)", e);
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException("EPUB 이 비었다");
        }
        return entries;
    }

    private static String opfPath(Map<String, byte[]> entries) {
        byte[] container = entries.get(CONTAINER_PATH);
        if (container == null) {
            throw new IllegalStateException("EPUB 에 " + CONTAINER_PATH + " 가 없다");
        }
        Element rootfile = parseXml(container, CONTAINER_PATH).selectFirst("rootfile[full-path]");
        if (rootfile == null) {
            throw new IllegalStateException(CONTAINER_PATH + " 에 rootfile 이 없다");
        }
        return normalize(rootfile.attr("full-path"));
    }

    /**
     * manifest 로 id→href 를 만든 뒤 spine 의 idref 순서대로 늘어놓는다.
     *
     * <p>{@code properties="nav"} 인 항목은 목차 문서이므로 여기서 제외한다.
     */
    private static List<String> spineHrefs(Document opf, String baseDir) {
        Map<String, String> hrefById = new LinkedHashMap<>();
        for (Element item : opf.select("manifest > item")) {
            String mediaType = item.attr("media-type");
            boolean isNav = item.attr("properties").contains("nav");
            if (!isNav && mediaType.contains("xhtml")) {
                hrefById.put(item.attr("id"), resolve(baseDir, item.attr("href")));
            }
        }

        List<String> ordered = new ArrayList<>();
        for (Element ref : opf.select("spine > itemref")) {
            // linear="no" 는 본문 흐름 밖(부록·표지)이라는 뜻이다.
            if ("no".equalsIgnoreCase(ref.attr("linear"))) {
                continue;
            }
            String href = hrefById.get(ref.attr("idref"));
            if (href != null) {
                ordered.add(href);
            }
        }
        return ordered;
    }

    private static boolean isNonContent(String href) {
        String name = href.substring(href.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        return NON_CONTENT_HINTS.stream().anyMatch(name::contains);
    }

    /**
     * Gutenberg 헤더/라이선스를 잘라낸다. 마커가 없으면 원문을 그대로 둔다 —
     * Gutenberg 가 아닌 EPUB 도 들어올 수 있다.
     */
    private static String trimGutenberg(String text) {
        int start = text.indexOf(GUTENBERG_START);
        if (start >= 0) {
            int lineEnd = text.indexOf('\n', start);
            text = lineEnd >= 0 ? text.substring(lineEnd + 1) : text.substring(start + GUTENBERG_START.length());
        }
        int end = text.indexOf(GUTENBERG_END);
        if (end >= 0) {
            text = text.substring(0, end);
        }
        return text;
    }

    private static Document parseXml(byte[] xml, String what) {
        if (xml == null) {
            throw new IllegalStateException("EPUB 에 " + what + " 가 없다");
        }
        return Jsoup.parse(new String(xml, StandardCharsets.UTF_8), "", Parser.xmlParser());
    }

    private static String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash + 1);
    }

    /** OPF 의 href 는 OPF 파일 기준 상대경로이고, {@code %20} 처럼 인코딩돼 있을 수 있다. */
    private static String resolve(String baseDir, String href) {
        String decoded = URLDecoder.decode(href, StandardCharsets.UTF_8);
        int anchor = decoded.indexOf('#');
        if (anchor >= 0) {
            decoded = decoded.substring(0, anchor);
        }
        return normalize(baseDir + decoded);
    }

    /** zip 항목명은 항상 {@code /} 구분이고 {@code ./} {@code ../} 가 남아 있으면 안 맞는다. */
    private static String normalize(String path) {
        String p = path.replace('\\', '/');
        List<String> out = new ArrayList<>();
        for (String seg : p.split("/")) {
            if (seg.isEmpty() || ".".equals(seg)) {
                continue;
            }
            if ("..".equals(seg)) {
                if (!out.isEmpty()) {
                    out.removeLast();
                }
                continue;
            }
            out.add(seg);
        }
        return String.join("/", out);
    }
}
