#!/usr/bin/env python3
"""
docs/corpus/*.txt  ->  docs/corpus/wiki.jsonl

인제스트 Job 의 입력을 만든다. AWS 를 전혀 쓰지 않으므로 로컬에서 결과를 눈으로
확인할 수 있고, golden set 의 "정답 페이지"를 여기 출력을 보고 적는다.

파이프라인:

    manifest.json
        |
        v
    [1] 원문 읽기
        |
        v
    [2] 정규화 --- 하드랩 파일이면 문단 내 개행 제거
        |          ("...얼다가 만 비가 추\n적적..." -> "...추적적...")
        v
    [3] 페이지 분할 --- 900자 목표, 문장 끝에서 자름
        |
        v
    [4] 청킹 --- 페이지가 상한(4KB)보다 짧으면 1페이지 = 1청크
        |        길면 페이지 '안에서만' 나눔. 페이지 경계는 절대 안 넘는다
        v
    [5] 불변식 검증 --- 넘으면 즉시 실패
        |
        v
    wiki.jsonl

불변식 (하나라도 깨지면 exit 1):
    I1. 모든 청크는 정확히 한 페이지에만 속한다        <- 계획서 T3
    I2. 페이지 텍스트를 이어붙이면 정규화 원문과 같다   <- 유실/중복 0
    I3. text <= 4KB, 필터 메타 <= 2KB                 <- S3 Vectors 한도
"""

import json
import re
import sys
import unicodedata
from pathlib import Path

CORPUS_DIR = Path(__file__).resolve().parent.parent / "docs" / "corpus"
MANIFEST = CORPUS_DIR / "manifest.json"
OUTPUT = CORPUS_DIR / "wiki.jsonl"

# 페이지 목표 길이. manifest.pageSize 로 덮어쓸 수 있다.
# 바꾸면 페이지 번호가 전부 바뀌므로 재인제스트는 delete-then-put 이어야 한다(계획서 T4).
DEFAULT_PAGE_SIZE = 900
PAGE_TOLERANCE = 150  # 문장 끝을 찾느라 목표에서 벗어날 수 있는 범위

MAX_CHUNK_BYTES = 4096  # 청크 본문 상한 (컨텍스트 토큰 폭발 방지)
MAX_FILTERABLE_BYTES = 2048  # S3 Vectors 필터 가능 메타데이터 한도
MAX_TOTAL_META_BYTES = 40960  # S3 Vectors 벡터당 메타데이터 총 한도

HARDWRAP_MAX_LINE = 60  # 최대 줄 길이가 이보다 짧으면 하드랩으로 본다
SENTENCE_END = ('.', '?', '!', '”', '"', '…', '」')
# 문장부호 뒤에 붙어 있으면 같은 페이지에 넣는다.
# 안 그러면 '...말씀입니까.' 로 페이지가 끝나고 다음 페이지가 '”' 로 시작한다.
TRAILING_CLOSERS = ('”', '"', '’', "'", '」', '』', ')', '）')


def is_hardwrapped(lines):
    """하드랩 판별. 실측상 하드랩 45~52자 vs 자연문단 214~494자로 명확히 갈린다."""
    widths = [len(ln) for ln in lines if ln.strip()]
    return bool(widths) and max(widths) <= HARDWRAP_MAX_LINE


def join_hardwrap(lines):
    """
    문단 내 개행을 제거한다. 한국어 하드랩은 단어 중간을 자르므로 공백 없이 붙인다.
    다만 양쪽이 라틴 문자/숫자면 원래 공백이 있던 자리이므로 공백을 넣는다.

    문단 경계 신호 3가지:
        (a) 빈 줄
        (b) 줄이 공백으로 시작        <- 운수 좋은 날의 들여쓰기
        (c) 직전 줄이 최대폭보다 뚜렷이 짧다   <- 봄·봄
    """
    width = max((len(ln) for ln in lines if ln.strip()), default=0)
    short = width * 0.8

    paragraphs, buf = [], []

    def flush():
        if buf:
            paragraphs.append("".join(buf))
            buf.clear()

    for raw in lines:
        if not raw.strip():
            flush()
            continue
        starts_new = raw[:1].isspace() or (buf and len(buf[-1]) < short)
        if starts_new:
            flush()
        piece = raw.strip()
        if buf and _needs_space(buf[-1], piece):
            buf.append(" ")
        buf.append(piece)
    flush()
    return paragraphs


def _needs_space(prev, nxt):
    if not prev or not nxt:
        return False
    a, b = prev[-1], nxt[0]
    return (a.isascii() and a.isalnum()) and (b.isascii() and b.isalnum())


def normalize(text):
    """[2] 정규화. 반환값은 문단 리스트."""
    text = unicodedata.normalize("NFC", text.replace("\r\n", "\n").replace("\r", "\n"))
    lines = text.split("\n")

    if is_hardwrapped(lines):
        paragraphs = join_hardwrap(lines)
        mode = "hardwrap"
    else:
        paragraphs = [ln.strip() for ln in lines if ln.strip()]
        mode = "paragraph"

    paragraphs = [re.sub(r"[ \t]+", " ", p).strip() for p in paragraphs]
    return [p for p in paragraphs if p], mode


def split_pages(paragraphs, page_size):
    """
    [3] 페이지 분할. 900자 목표, 문장 끝에서 자른다.

    문단 중간에서 잘려도 된다 — 실제 책도 그렇다. 다만 문장 중간에서 자르면
    인용 스니펫이 "...김첨지는 취한 채 집으" 처럼 끊겨 보인다.
    """
    body = "\n".join(paragraphs)
    pages, pos = [], 0

    while pos < len(body):
        remain = len(body) - pos
        if remain <= page_size + PAGE_TOLERANCE:
            pages.append(body[pos:].strip())
            break

        cut = _find_sentence_cut(body, pos, page_size)
        pages.append(body[pos:cut].strip())
        pos = cut

    return [p for p in pages if p]


def _find_sentence_cut(body, pos, page_size):
    target = pos + page_size
    lo, hi = max(pos + 1, target - PAGE_TOLERANCE), min(len(body), target + PAGE_TOLERANCE)

    best = None
    for i in range(lo, hi):
        if body[i] in SENTENCE_END:
            if best is None or abs(i - target) < abs(best - target):
                best = i
    if best is not None:
        cut = best + 1
        while cut < len(body) and body[cut] in TRAILING_CLOSERS:
            cut += 1
        return cut

    # 문장 끝을 못 찾으면 공백에서, 그것도 없으면 목표 지점에서 자른다.
    space = body.rfind(" ", lo, hi)
    return space + 1 if space > pos else target


def chunk_page(page_text):
    """
    [4] 청킹. 페이지가 상한보다 짧으면 통째로 1청크다.

    핵심: 여기서 만드는 청크는 '이 페이지 안'에서만 나온다. 다음 페이지를 절대
    끌어오지 않는다. 한 벡터가 두 페이지에 걸치면 인용 페이지 번호를 쓸 수 없다.
    """
    if len(page_text.encode("utf-8")) <= MAX_CHUNK_BYTES:
        return [page_text]

    chunks, cur = [], ""
    for sentence in re.split(r"(?<=[.!?…”」])\s*", page_text):
        if not sentence:
            continue
        if len((cur + sentence).encode("utf-8")) > MAX_CHUNK_BYTES and cur:
            chunks.append(cur.strip())
            cur = ""
        cur += sentence
    if cur.strip():
        chunks.append(cur.strip())
    return chunks


def build_record(book, page_no, chunk_seq, text):
    return {
        "bookId": book["bookId"],
        "bookTitle": book["title"],
        "author": book["author"],
        "category": book["category"],
        "license": book["license"],
        "page": page_no,
        "chunkSeq": chunk_seq,
        "key": f'{book["bookId"]}#{page_no}#{chunk_seq}',
        "text": text,
    }


def verify(record, page_text, failures):
    """[5] 불변식 검증."""
    key = record["key"]

    # I1: 청크는 자기 페이지 안에 완전히 들어있다
    if record["text"] not in page_text:
        failures.append(f"{key}: 청크가 페이지 범위를 벗어남 (T3 위반)")

    # I3: S3 Vectors 한도
    text_bytes = len(record["text"].encode("utf-8"))
    if text_bytes > MAX_CHUNK_BYTES:
        failures.append(f"{key}: text {text_bytes}B > {MAX_CHUNK_BYTES}B")

    filterable = {k: record[k] for k in ("bookId", "page", "bookTitle", "category")}
    fb = len(json.dumps(filterable, ensure_ascii=False).encode("utf-8"))
    if fb > MAX_FILTERABLE_BYTES:
        failures.append(f"{key}: 필터 메타 {fb}B > {MAX_FILTERABLE_BYTES}B")

    total = fb + text_bytes
    if total > MAX_TOTAL_META_BYTES:
        failures.append(f"{key}: 메타 총합 {total}B > {MAX_TOTAL_META_BYTES}B")


def main():
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    page_size = manifest.get("pageSize", DEFAULT_PAGE_SIZE)

    records, failures = [], []
    print(f"페이지 크기: {page_size}자\n")
    print(f"{'작품':<16}{'모드':<11}{'문단':>5}{'페이지':>7}{'청크':>6}{'글자':>8}")
    print("-" * 56)

    for book in manifest["books"]:
        path = CORPUS_DIR / book["file"]
        if not path.exists():
            failures.append(f'{book["file"]}: 파일 없음')
            continue

        paragraphs, mode = normalize(path.read_text(encoding="utf-8"))
        pages = split_pages(paragraphs, page_size)

        n_chunks = 0
        for page_no, page_text in enumerate(pages, start=1):
            for seq, chunk in enumerate(chunk_page(page_text)):
                rec = build_record(book, page_no, seq, chunk)
                verify(rec, page_text, failures)
                records.append(rec)
                n_chunks += 1

        # I2: 유실/중복 0 — 페이지를 이어붙이면 원문과 같아야 한다
        rejoined = "".join(p.replace("\n", "") for p in pages)
        original = "".join(paragraphs).replace("\n", "")
        if rejoined.replace(" ", "") != original.replace(" ", ""):
            failures.append(f'{book["title"]}: 페이지 분할에서 본문 유실/중복 발생')

        print(f'{book["title"]:<16}{mode:<11}{len(paragraphs):>5}{len(pages):>7}'
              f'{n_chunks:>6}{sum(len(p) for p in pages):>8}')

    print("-" * 56)
    pages_total = len({(r["bookId"], r["page"]) for r in records})
    print(f'{"합계":<16}{"":<11}{"":>5}{pages_total:>7}{len(records):>6}'
          f'{sum(len(r["text"]) for r in records):>8}\n')

    if failures:
        print("불변식 위반:", file=sys.stderr)
        for f in failures:
            print(f"  - {f}", file=sys.stderr)
        return 1

    with OUTPUT.open("w", encoding="utf-8") as fp:
        for rec in records:
            fp.write(json.dumps(rec, ensure_ascii=False) + "\n")

    print(f"OK  {OUTPUT.relative_to(CORPUS_DIR.parent.parent)}  ({len(records)} 청크)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
