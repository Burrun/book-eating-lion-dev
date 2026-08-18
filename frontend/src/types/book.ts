// UI 전용 도메인 타입.
// 백엔드 응답(DTO)은 src/api/types.ts 에 별도로 있고,
// 둘 사이 변환은 src/api/mappers.ts 가 담당한다.
// 백엔드 필드가 바뀌어도 매퍼만 고치면 컴포넌트는 손대지 않는다.

export interface BookSummary {
  id: string;
  title: string;
  price: number;
  rating: number;
  category?: string;
}

export interface WebtoonCut {
  id: string;
  caption: string;
}

export interface Review {
  id: string;
  author: string;
  rating: number;
  date: string;
  text: string;
}

export interface Book {
  id: string;
  title: string;
  author: string;
  publisher: string;
  isbn: string;
  price: number;
  rating: number;
  reviewCount: number;
  shippingNote: string;
  /** 무료 회원에게 보여주는 줄거리 텍스트 */
  synopsis: string;
  /** 구독 회원 전용 웹툰 요약 컷 */
  webtoonCuts: WebtoonCut[];
  reviews: Review[];
  ebookAvailable: boolean;
}

export interface SwipeDeckItem {
  id: string;
  title: string;
  reason: string;
}
