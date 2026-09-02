// 도서 목업. VITE_USE_MOCK=true 일 때 api/books.ts 가 사용한다.
//
// 다른 목업(wishlist/cards)과 동일하게 백엔드 DTO 형태로 데이터를 두고, UI 타입 변환은
// api/books.ts 가 매퍼(toBookSummary/toBook/toWebtoonCuts)로 처리한다.
// 목업 모드에서도 실 API와 완전히 같은 경로를 타므로 매퍼 버그가 여기서 드러난다.
//
// 그 결과 백엔드에 없는 필드(shippingNote 등)는 목업에서도 매퍼의 임시 기본값을 따른다.
// averageRating/reviewCount는 실제로는 리뷰 작성/수정/삭제 때마다 서버가 재계산해 주는
// 값이지만, 이 씨드 데이터엔 리뷰가 연결되어 있지 않아 0으로 고정한다(mock 모드 한계 —
// 실API 모드에서는 ProductDetailPage.tsx에서 실제로 갱신되는 값이 표시된다).
import type {
  BookDetailResponse,
  BookSummaryResponse,
  BookSynopsisDetailResponse,
  Page,
} from "../api/types.ts";
import type { SwipeDeckItem } from "../types/book.ts";

export const CATEGORIES = ["소설", "IT/개발", "인문", "자기계발", "에세이"];

/** DTO 를 매번 다 적지 않기 위한 압축 원본. 아래 toDetail/toSummary 가 실제 DTO로 부풀린다. */
interface BookSeed {
  id: number;
  title: string;
  author: string;
  publisher: string;
  isbn: string;
  category: string;
  price: number;
  publishedDate: string;
  description: string;
  /** 전자책(EPUB) 지원 여부. 미지정이면 false(ebook 준비중)로 취급한다. */
  ebookUrl?: string;
}

// id 는 목록/상세/찜(mocks/wishlist.ts)이 같은 책을 가리키도록 통일했다.
// ISBN 은 대표 3권만 실제 값이고 나머지는 자리표시자다.
const SEEDS: BookSeed[] = [
  {
    id: 1,
    title: "자바 ORM 표준 JPA 프로그래밍",
    author: "김영한",
    publisher: "에이콘출판사",
    isbn: "9788960777330",
    category: "IT/개발",
    price: 38700,
    publishedDate: "2015-07-27",
    description:
      "JPA의 핵심인 영속성 컨텍스트부터 연관관계 매핑, 프록시와 지연 로딩, JPQL까지 실무에서 마주치는 문제를 단계적으로 다룬다. ORM이 왜 필요한지부터 시작해 스프링 데이터 JPA로 자연스럽게 이어진다.",
  },
  {
    id: 2,
    title: "클린 코드 (Clean Code)",
    author: "로버트 C. 마틴",
    publisher: "인사이트",
    isbn: "9788966263158",
    category: "IT/개발",
    price: 29000,
    publishedDate: "2013-12-24",
    description:
      "읽기 좋은 코드란 무엇인가를 의미 있는 이름, 작은 함수, 주석의 역할, 오류 처리 같은 구체적인 규칙으로 풀어낸다. 나쁜 코드를 단계적으로 리팩터링하는 실제 사례가 책의 절반을 차지한다.",
  },
  {
    id: 3,
    title: "해리 포터와 마법사의 돌",
    author: "J.K. 롤링",
    publisher: "문학수첩",
    isbn: "9788932917245",
    category: "소설",
    price: 15400,
    publishedDate: "2019-11-19",
    description:
      "계단 밑 벽장에서 자란 열한 살 해리는 자신이 마법사라는 사실을 알게 되고 호그와트에 입학한다. 론, 헤르미온느와 함께 학교에 숨겨진 마법사의 돌을 둘러싼 비밀에 다가간다.",
  },
  {
    id: 4,
    title: "이펙티브 자바",
    author: "조슈아 블로크",
    publisher: "인사이트",
    isbn: "9788900000004",
    category: "IT/개발",
    price: 36000,
    publishedDate: "2018-11-01",
    description:
      "자바 프로그래머가 자주 저지르는 실수를 90개의 아이템으로 정리했다. 각 항목마다 왜 그렇게 써야 하는지를 언어 명세와 실제 API 설계 사례로 설명한다.",
  },
  {
    id: 5,
    title: "가상 면접 사례로 배우는 대규모 시스템 설계",
    author: "알렉스 쉬",
    publisher: "인사이트",
    isbn: "9788900000005",
    category: "IT/개발",
    price: 27000,
    publishedDate: "2021-08-30",
    description:
      "URL 단축기, 알림 시스템, 뉴스 피드처럼 자주 나오는 설계 문제를 면접 대화 형식으로 푼다. 요구사항 정리부터 병목 지점 개선까지의 사고 과정을 따라갈 수 있다.",
  },
  {
    id: 6,
    title: "스프링 부트 실전 활용",
    author: "그렉 턴키스트",
    publisher: "한빛미디어",
    isbn: "9788900000006",
    category: "IT/개발",
    price: 32000,
    publishedDate: "2021-05-10",
    description:
      "스프링 부트로 웹 애플리케이션을 만들면서 자동 구성, 데이터 접근, 테스트, 배포까지 한 흐름으로 익힌다. 리액티브 스택을 다루는 후반부가 특징이다.",
  },
  {
    id: 7,
    title: "반지의 제왕 시리즈",
    author: "J.R.R. 톨킨",
    publisher: "아르테",
    isbn: "9788900000007",
    category: "소설",
    price: 42000,
    publishedDate: "2021-03-15",
    description:
      "절대반지를 파괴하기 위해 모인 아홉 원정대가 중간계를 가로지른다. 방대한 신화와 언어 설정 위에 세워진 현대 판타지의 원형.",
  },
  {
    id: 8,
    title: "나니아 연대기",
    author: "C.S. 루이스",
    publisher: "시공주니어",
    isbn: "9788900000008",
    category: "소설",
    price: 24000,
    publishedDate: "2019-05-20",
    description:
      "옷장 너머의 나라 나니아에서 네 남매가 겪는 일곱 편의 모험. 아슬란과 하얀 마녀를 둘러싼 이야기가 시리즈 전체를 관통한다.",
  },
  {
    id: 9,
    title: "데미안",
    author: "헤르만 헤세",
    publisher: "민음사",
    isbn: "9788900000009",
    category: "소설",
    price: 12000,
    publishedDate: "2009-01-20",
    description:
      "안온한 세계에 머물던 싱클레어가 데미안을 만나 자기 안의 어두운 면과 마주한다. 알을 깨고 나오는 새의 이미지로 요약되는 성장의 기록.",
  },
  {
    id: 10,
    title: "1984",
    author: "조지 오웰",
    publisher: "민음사",
    isbn: "9788900000010",
    category: "소설",
    price: 13500,
    publishedDate: "2007-03-10",
    description:
      "빅 브라더가 모든 것을 감시하는 오세아니아에서 윈스턴 스미스는 기록을 조작하는 일을 한다. 언어와 기억을 통제하는 방식으로 권력을 그린 디스토피아.",
  },
  {
    id: 11,
    title: "사피엔스",
    author: "유발 하라리",
    publisher: "김영사",
    isbn: "9788900000011",
    category: "인문",
    price: 22000,
    publishedDate: "2015-11-24",
    description:
      "인지혁명, 농업혁명, 과학혁명이라는 세 축으로 호모 사피엔스 7만 년의 역사를 훑는다. 허구를 믿는 능력이 대규모 협력을 만들었다는 관점이 중심이다.",
  },
  {
    id: 12,
    title: "총, 균, 쇠",
    author: "재레드 다이아몬드",
    publisher: "문학사상",
    isbn: "9788900000012",
    category: "인문",
    price: 25000,
    publishedDate: "2013-08-05",
    description:
      "왜 어떤 대륙의 문명이 다른 대륙을 정복했는가를 인종이 아니라 지리와 환경으로 설명한다. 작물화·가축화 조건과 전염병의 역할을 폭넓게 다룬다.",
  },
  {
    id: 13,
    title: "코스모스",
    author: "칼 세이건",
    publisher: "사이언스북스",
    isbn: "9788900000013",
    category: "인문",
    price: 21000,
    publishedDate: "2006-12-20",
    description:
      "우주의 탄생과 별의 일생, 생명의 기원을 과학사와 함께 엮는다. 창백한 푸른 점에서 인간의 자리를 다시 보게 만드는 고전.",
  },
  {
    id: 14,
    title: "역사의 쓸모",
    author: "최태성",
    publisher: "다산초당",
    isbn: "9788900000014",
    category: "인문",
    price: 16000,
    publishedDate: "2019-06-10",
    description:
      "한국사 속 인물들의 선택을 오늘의 고민에 겹쳐 읽는다. 역사를 지식이 아니라 삶의 참고서로 쓰는 방법을 이야기한다.",
  },
  {
    id: 15,
    title: "돈의 속성",
    author: "김승호",
    publisher: "스노우폭스북스",
    isbn: "9788900000015",
    category: "자기계발",
    price: 17800,
    publishedDate: "2020-06-15",
    description:
      "돈을 인격체로 보는 관점에서 수입의 성격, 저축과 투자의 순서, 빚을 다루는 원칙을 정리했다. 사업가로서 겪은 실패담이 함께 실려 있다.",
  },
  {
    id: 16,
    title: "역행자",
    author: "자청",
    publisher: "웅진지식하우스",
    isbn: "9788900000016",
    category: "자기계발",
    price: 18000,
    publishedDate: "2022-05-30",
    description:
      "정해진 각본대로 사는 순리자에서 벗어나기 위한 7단계를 제시한다. 자의식 해체, 정체성 만들기, 뇌 자동화로 이어지는 순서가 뼈대다.",
  },
  {
    id: 17,
    title: "아주 작은 습관의 힘",
    author: "제임스 클리어",
    publisher: "비즈니스북스",
    isbn: "9788900000017",
    category: "자기계발",
    price: 16500,
    publishedDate: "2019-02-26",
    description:
      "목표가 아니라 시스템을 바꾸라고 말한다. 습관을 분명하게, 매력적으로, 쉽게, 만족스럽게 만드는 네 가지 법칙으로 행동 설계를 다룬다.",
  },
  {
    id: 18,
    title: "트렌드 코리아 2026",
    author: "김난도",
    publisher: "미래의창",
    isbn: "9788900000018",
    category: "자기계발",
    price: 19000,
    publishedDate: "2025-10-01",
    description:
      "소비 데이터와 현장 관찰을 바탕으로 한 해의 소비 트렌드 열 가지를 뽑았다. 각 키워드마다 산업별 대응 사례가 붙는다.",
  },
  {
    id: 19,
    title: "언어의 온도",
    author: "이기주",
    publisher: "말글터",
    isbn: "9788900000019",
    category: "에세이",
    price: 13800,
    publishedDate: "2016-08-19",
    description:
      "말과 글에 담긴 온도를 일상의 장면에서 길어 올린 짧은 글 모음. 무심코 건넨 한마디가 남기는 자국을 들여다본다.",
  },
  {
    id: 20,
    title: "나는 나로 살기로 했다",
    author: "김수현",
    publisher: "마음의숲",
    isbn: "9788900000020",
    category: "에세이",
    price: 14000,
    publishedDate: "2016-11-15",
    description:
      "타인의 기준에 맞추느라 지친 사람들에게 건네는 문장과 그림. 비교를 멈추고 자기 속도를 되찾는 이야기를 담았다.",
  },
  {
    id: 21,
    title: "죽고 싶지만 떡볶이는 먹고 싶어",
    author: "백세희",
    publisher: "흔",
    isbn: "9788900000021",
    category: "에세이",
    price: 13000,
    publishedDate: "2018-06-20",
    description:
      "가벼운 우울증을 겪는 저자가 정신과 전문의와 나눈 12주간의 대화를 그대로 옮겼다. 진단명 뒤에 가려진 일상의 감정을 솔직하게 적었다.",
  },
  {
    id: 22,
    title: "곰돌이 푸, 행복한 일은 매일 있어",
    author: "A.A. 밀른",
    publisher: "RHK",
    isbn: "9788900000022",
    category: "에세이",
    price: 14500,
    publishedDate: "2018-01-30",
    description:
      "푸와 친구들이 주고받는 말에서 뽑아낸 위로의 문장들. 오늘 하루를 조금 느긋하게 지나가도 괜찮다고 말해 준다.",
  },
  // 아래 두 권은 ebook 뷰어(EbookViewer, react-reader) 검증용으로 추가했다.
  // EPUB은 저작권 만료된 구텐베르크 프로젝트 원문을 frontend/public/ebooks/ 에 받아뒀다.
  {
    id: 23,
    title: "이상한 나라의 앨리스",
    author: "루이스 캐럴",
    publisher: "Project Gutenberg (원문)",
    isbn: "9788900000023",
    category: "소설",
    price: 0,
    publishedDate: "1865-11-26",
    description:
      "토끼굴에 빠진 앨리스가 카드 왕국과 미친 다과회를 지나 여왕의 재판정에 이르는 여정. 논리와 말장난으로 뒤집힌 세계를 그린 고전 판타지.",
    ebookUrl: "/ebooks/alice-in-wonderland.epub",
  },
  {
    id: 24,
    title: "프랑켄슈타인",
    author: "메리 셸리",
    publisher: "Project Gutenberg (원문)",
    isbn: "9788900000024",
    category: "소설",
    price: 0,
    publishedDate: "1818-01-01",
    description:
      "빅터 프랑켄슈타인이 창조한 존재가 세상에서 거부당하며 벌어지는 비극. 창조자의 책임과 고독을 묻는 최초의 SF 소설로 꼽힌다.",
    ebookUrl: "/ebooks/frankenstein.epub",
  },
];

const BESTSELLER_IDS = [1, 2, 3, 6];
const NEW_RELEASE_IDS = [18, 16, 4, 5];

export const swipeDeck: SwipeDeckItem[] = [
  {
    id: "3",
    title: "해리 포터와 마법사의 돌",
    reason: "AI 추천사유: 판타지 분야 선호도 98% 분석 결과",
    coverImageUrl: null,
  },
  {
    id: "2",
    title: "클린 코드 (Clean Code)",
    reason: "AI 추천사유: 최근 열람한 IT/개발서와 82% 유사",
    coverImageUrl: null,
  },
  {
    id: "15",
    title: "돈의 속성",
    reason: "AI 추천사유: 경제/재테크 관심 카테고리 1위",
    coverImageUrl: null,
  },
  {
    id: "11",
    title: "사피엔스",
    reason: "AI 추천사유: 인문/역사 도서 완독률 상위 5%",
    coverImageUrl: null,
  },
  {
    id: "6",
    title: "스프링 부트 실전 활용",
    reason: "AI 추천사유: 최근 완독한 JPA 도서와 연관 구매율 78%",
    coverImageUrl: null,
  },
];

function toSummary(seed: BookSeed): BookSummaryResponse {
  return {
    id: seed.id,
    title: seed.title,
    author: seed.author,
    price: seed.price,
    coverImageUrl: null,
    category: seed.category,
    saleStatus: "ON_SALE",
    averageRating: 0,
    reviewCount: 0,
    ebookAvailable: Boolean(seed.ebookUrl),
  };
}

function toDetail(seed: BookSeed): BookDetailResponse {
  return {
    id: seed.id,
    title: seed.title,
    author: seed.author,
    publisher: seed.publisher,
    isbn: seed.isbn,
    category: seed.category,
    price: seed.price,
    stockQuantity: 100,
    coverImageUrl: null,
    description: seed.description,
    saleStatus: "ON_SALE",
    publishedDate: seed.publishedDate,
    averageRating: 0,
    reviewCount: 0,
    createdAt: `${seed.publishedDate}T00:00:00`,
    updatedAt: `${seed.publishedDate}T00:00:00`,
    ebookAvailable: Boolean(seed.ebookUrl),
  };
}

export function mockGetEbookAccess(bookId: number | string) {
  const id = Number(bookId);
  const seed = SEEDS.find((item) => item.id === id);
  if (!seed) throw new Error("도서를 찾을 수 없습니다.");
  return {
    bookId: id,
    ebookAvailable: Boolean(seed.ebookUrl),
    // mock 은 구매자 시나리오로 둔다 — 뷰어의 사자 진입점을 개발 중에 볼 수 있어야 한다.
    purchased: true,
    presignedUrl: seed.ebookUrl ?? null,
    expiresAt: null,
  };
}

export function mockGetMyEbooks(): BookSummaryResponse[] {
  return SEEDS.filter((seed) => Boolean(seed.ebookUrl)).map(toSummary);
}

/** Spring Page 직렬화 형태로 감싼다 (매퍼 toPaged 가 그대로 받는다). */
function toPage<T>(items: T[], page: number, size: number): Page<T> {
  const totalPages = Math.max(1, Math.ceil(items.length / size));
  const number = Math.min(Math.max(page, 0), totalPages - 1);
  return {
    content: items.slice(number * size, number * size + size),
    number,
    size,
    totalElements: items.length,
    totalPages,
    first: number === 0,
    last: number === totalPages - 1,
  };
}

function findSeed(bookId: number | string): BookSeed {
  const seed = SEEDS.find((s) => String(s.id) === String(bookId));
  // 실제 API 는 없는 id 에 404(BookNotFoundException)를 주므로 목업도 실패로 처리한다.
  // (이전 getBookById 는 못 찾으면 1번 책으로 대체해서 어떤 책을 눌러도 같은 상세가 떴다)
  if (!seed) throw new Error(`존재하지 않는 도서입니다 (id: ${bookId})`);
  return seed;
}

export function mockGetBooks(params: {
  category?: string;
  hasEbook?: boolean;
  page?: number;
  size?: number;
}): Page<BookSummaryResponse> {
  const { category, hasEbook, page = 0, size = 20 } = params;
  // 실 백엔드(BookService.getBooks)와 같은 규칙: hasEbook=true면 category는 무시한다.
  const filtered = hasEbook
    ? SEEDS.filter((seed) => Boolean(seed.ebookUrl))
    : category
      ? SEEDS.filter((seed) => seed.category === category)
      : SEEDS;
  return toPage(filtered.map(toSummary), page, size);
}

// 백엔드 BookRepository.search 와 같은 규칙: 제목 또는 저자 부분 일치(대소문자 무시).
export function mockSearchBooks(params: {
  q: string;
  page?: number;
  size?: number;
}): Page<BookSummaryResponse> {
  const { q, page = 0, size = 20 } = params;
  const keyword = q.trim().toLowerCase();
  const matched = keyword
    ? SEEDS.filter(
        (seed) =>
          seed.title.toLowerCase().includes(keyword) || seed.author.toLowerCase().includes(keyword),
      )
    : [];
  return toPage(matched.map(toSummary), page, size);
}

export function mockGetBook(bookId: number | string): BookDetailResponse {
  return toDetail(findSeed(bookId));
}

export function mockGetBestsellers(limit: number): BookSummaryResponse[] {
  return BESTSELLER_IDS.slice(0, limit).map((id) => toSummary(findSeed(id)));
}

export function mockGetNewReleases(limit: number): BookSummaryResponse[] {
  return NEW_RELEASE_IDS.slice(0, limit).map((id) => toSummary(findSeed(id)));
}

// 백엔드는 웹툰 컷이 아니라 줄거리 텍스트 하나를 준다. 매퍼(toWebtoonCuts)가 줄 단위로 컷을 나누므로
// 목업도 여러 줄짜리 텍스트로 돌려준다.
export function mockGetSynopsisDetail(bookId: number | string): BookSynopsisDetailResponse {
  const seed = findSeed(bookId);
  return {
    bookId: seed.id,
    title: seed.title,
    detailedSynopsis: [1, 2, 3].map((n) => `${seed.title} 핵심 장면 ${n}컷 요약`).join("\n"),
  };
}
