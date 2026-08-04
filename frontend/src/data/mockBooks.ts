export interface Bestseller {
  id: string
  title: string
  price: number
  rating: number
}

export interface CatalogBook {
  id: string
  title: string
  price: number
  rating: number
  category: string
}

export interface SwipeDeckItem {
  id: string
  title: string
  reason: string
}

export interface WebtoonCut {
  id: string
  caption: string
}

export interface Review {
  id: string
  author: string
  rating: number
  date: string
  text: string
}

export interface Book {
  id: string
  title: string
  author: string
  publisher: string
  isbn: string
  listPrice: number
  salePrice: number
  rating: number
  reviewCount: number
  shippingNote: string
  webtoonCuts: WebtoonCut[]
  reviews: Review[]
}

export const bestsellers: Bestseller[] = [
  { id: '1', title: '자바 ORM 표준 JPA 프로그래밍', price: 38700, rating: 4.9 },
  { id: '2', title: '클린 코드 (Clean Code)', price: 29000, rating: 4.8 },
  { id: '3', title: '해리 포터와 마법사의 돌', price: 15400, rating: 4.8 },
  { id: '4', title: '스프링 부트 실전 활용', price: 32000, rating: 4.7 },
]

export const newReleases: Bestseller[] = [
  { id: 'n1', title: '트렌드 코리아 2026', price: 19000, rating: 4.3 },
  { id: 'n2', title: '역행자', price: 18000, rating: 4.7 },
  { id: 'n3', title: '이펙티브 자바', price: 36000, rating: 4.8 },
  { id: 'n4', title: '가상 면접 사례로 배우는 대규모 시스템 설계', price: 27000, rating: 4.9 },
]

export const CATEGORIES = ['소설', 'IT/개발', '인문', '자기계발', '에세이']

export const catalogBooks: CatalogBook[] = [
  { id: 'c1', title: '자바 ORM 표준 JPA 프로그래밍', price: 38700, rating: 4.9, category: 'IT/개발' },
  { id: 'c2', title: '클린 코드 (Clean Code)', price: 29000, rating: 4.8, category: 'IT/개발' },
  { id: 'c3', title: '스프링 부트 실전 활용', price: 32000, rating: 4.7, category: 'IT/개발' },
  { id: 'c4', title: '이펙티브 자바', price: 36000, rating: 4.8, category: 'IT/개발' },
  { id: 'c5', title: '가상 면접 사례로 배우는 대규모 시스템 설계', price: 27000, rating: 4.9, category: 'IT/개발' },
  { id: 'c6', title: '해리 포터와 마법사의 돌', price: 15400, rating: 4.8, category: '소설' },
  { id: 'c7', title: '반지의 제왕 시리즈', price: 42000, rating: 4.7, category: '소설' },
  { id: 'c8', title: '나니아 연대기', price: 24000, rating: 4.6, category: '소설' },
  { id: 'c9', title: '데미안', price: 12000, rating: 4.5, category: '소설' },
  { id: 'c10', title: '1984', price: 13500, rating: 4.7, category: '소설' },
  { id: 'c11', title: '사피엔스', price: 22000, rating: 4.8, category: '인문' },
  { id: 'c12', title: '총, 균, 쇠', price: 25000, rating: 4.7, category: '인문' },
  { id: 'c13', title: '코스모스', price: 21000, rating: 4.9, category: '인문' },
  { id: 'c14', title: '역사의 쓸모', price: 16000, rating: 4.4, category: '인문' },
  { id: 'c15', title: '돈의 속성', price: 17800, rating: 4.6, category: '자기계발' },
  { id: 'c16', title: '역행자', price: 18000, rating: 4.7, category: '자기계발' },
  { id: 'c17', title: '아주 작은 습관의 힘', price: 16500, rating: 4.8, category: '자기계발' },
  { id: 'c18', title: '트렌드 코리아 2026', price: 19000, rating: 4.3, category: '자기계발' },
  { id: 'c19', title: '언어의 온도', price: 13800, rating: 4.5, category: '에세이' },
  { id: 'c20', title: '나는 나로 살기로 했다', price: 14000, rating: 4.6, category: '에세이' },
  { id: 'c21', title: '죽고 싶지만 떡볶이는 먹고 싶어', price: 13000, rating: 4.4, category: '에세이' },
  { id: 'c22', title: '곰돌이 푸, 행복한 일은 매일 있어', price: 14500, rating: 4.5, category: '에세이' },
]

export const swipeDeck: SwipeDeckItem[] = [
  {
    id: '3',
    title: '해리 포터와 마법사의 돌',
    reason: 'AI 추천사유: 판타지 분야 선호도 98% 분석 결과',
  },
  {
    id: '2',
    title: '클린 코드 (Clean Code)',
    reason: 'AI 추천사유: 최근 열람한 IT/개발서와 82% 유사',
  },
  {
    id: '5',
    title: '돈의 속성',
    reason: 'AI 추천사유: 경제/재테크 관심 카테고리 1위',
  },
  {
    id: '6',
    title: '사피엔스',
    reason: 'AI 추천사유: 인문/역사 도서 완독률 상위 5%',
  },
  {
    id: '4',
    title: '스프링 부트 실전 활용',
    reason: 'AI 추천사유: 최근 완독한 JPA 도서와 연관 구매율 78%',
  },
]

export const books: Record<string, Book> = {
  1: {
    id: '1',
    title: '자바 ORM 표준 JPA 프로그래밍',
    author: '김영한',
    publisher: '에이콘출판사',
    isbn: '9788960777330',
    listPrice: 43000,
    salePrice: 38700,
    rating: 4.9,
    reviewCount: 128,
    shippingNote: '배송비 3,000원 (3만원 이상 구매시 무료배송)',
    webtoonCuts: [
      { id: 'w1', caption: '핵심 개념 및 캐릭터 대화형 인포그래픽 컷 요약 (구매자 전용)' },
      { id: 'w2', caption: '핵심 개념 및 캐릭터 대화형 인포그래픽 컷 요약 (구매자 전용)' },
      { id: 'w3', caption: '핵심 개념 및 캐릭터 대화형 인포그래픽 컷 요약 (구매자 전용)' },
    ],
    reviews: [
      {
        id: 'r1',
        author: 'user_102',
        rating: 5,
        date: '2026-07-29',
        text: 'JPA 입문에 최고의 책입니다. 웹툰 요약 컷도 쉽게 잘 정리되어 있어서 대만족!',
      },
    ],
  },
  2: {
    id: '2',
    title: '클린 코드 (Clean Code)',
    author: '로버트 C. 마틴',
    publisher: '인사이트',
    isbn: '9788966263158',
    listPrice: 33000,
    salePrice: 29000,
    rating: 4.8,
    reviewCount: 96,
    shippingNote: '배송비 3,000원 (3만원 이상 구매시 무료배송)',
    webtoonCuts: [
      { id: 'w1', caption: '핵심 개념 요약 인포그래픽 컷 (구매자 전용)' },
      { id: 'w2', caption: '핵심 개념 요약 인포그래픽 컷 (구매자 전용)' },
      { id: 'w3', caption: '핵심 개념 요약 인포그래픽 컷 (구매자 전용)' },
    ],
    reviews: [
      {
        id: 'r1',
        author: 'user_88',
        rating: 4,
        date: '2026-07-25',
        text: '개발자 필독서! 명구절들 정리해두기 좋았습니다.',
      },
    ],
  },
  3: {
    id: '3',
    title: '해리 포터와 마법사의 돌',
    author: 'J.K. 롤링',
    publisher: '문학수첩',
    isbn: '9788932917245',
    listPrice: 16500,
    salePrice: 15400,
    rating: 4.8,
    reviewCount: 214,
    shippingNote: '배송비 3,000원 (3만원 이상 구매시 무료배송)',
    webtoonCuts: [
      { id: 'w1', caption: '주요 장면 인포그래픽 컷 (구매자 전용)' },
      { id: 'w2', caption: '주요 장면 인포그래픽 컷 (구매자 전용)' },
      { id: 'w3', caption: '주요 장면 인포그래픽 컷 (구매자 전용)' },
    ],
    reviews: [
      {
        id: 'r1',
        author: 'user_45',
        rating: 5,
        date: '2026-07-18',
        text: '중고매물로 저렴하게 잘 구해서 읽었습니다. 채팅 직거래도 안전하게 완료!',
      },
    ],
  },
}

export function getBookById(id: string | undefined): Book {
  return books[id ?? ''] ?? books['1']
}
