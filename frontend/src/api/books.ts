import { apiClient, unwrap } from "./client.ts";
import { toBook, toBookSummary, toPaged, toWebtoonCuts } from "./mappers.ts";
import { mockDelay } from "../mocks/delay.ts";
import {
  mockGetBestsellers,
  mockGetBook,
  mockGetEbookAccess,
  mockGetBooks,
  mockGetNewReleases,
  mockGetSynopsisDetail,
  mockSearchBooks,
} from "../mocks/books.ts";
import type {
  ApiResponse,
  BookDetailResponse,
  EbookAccessResponse,
  BookSummaryResponse,
  BookSynopsisDetailResponse,
  Page,
} from "./types.ts";
import type { Book, BookSummary, WebtoonCut } from "../types/book.ts";
import type { Paged } from "../types/common.ts";

// 백엔드/DB 연동 전까지는 목업으로 화면을 확인한다.
// 목업도 실 API와 똑같이 DTO -> 매퍼 경로를 태운다. 따라서 백엔드에 없는 필드
// (rating, reviewCount 등)는 목업 모드에서도 매퍼의 임시 기본값이 그대로 나온다.
const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// GET /api/catalog/books — 도서 목록 (카테고리/전자책 여부/페이징)
export async function getBooks(
  params: {
    category?: string;
    hasEbook?: boolean;
    page?: number;
    size?: number;
    sort?: string;
  } = {},
): Promise<Paged<BookSummary>> {
  const page = USE_MOCK
    ? await mockDelay(mockGetBooks(params))
    : await unwrap(
        apiClient.get<ApiResponse<Page<BookSummaryResponse>>>("/catalog/books", { params }),
      );
  return toPaged(page, toBookSummary);
}

// GET /api/catalog/books/search?q= — 도서 검색
export async function searchBooks(params: {
  q: string;
  page?: number;
  size?: number;
}): Promise<Paged<BookSummary>> {
  const page = USE_MOCK
    ? await mockDelay(mockSearchBooks(params))
    : await unwrap(
        apiClient.get<ApiResponse<Page<BookSummaryResponse>>>("/catalog/books/search", { params }),
      );
  return toPaged(page, toBookSummary);
}

// GET /api/catalog/books/bestsellers — 베스트셀러 목록
export async function getBestsellers(limit = 10): Promise<BookSummary[]> {
  const list = USE_MOCK
    ? await mockDelay(mockGetBestsellers(limit))
    : await unwrap(
        apiClient.get<ApiResponse<BookSummaryResponse[]>>("/catalog/books/bestsellers", {
          params: { limit },
        }),
      );
  return list.map(toBookSummary);
}

// GET /api/catalog/books/new-releases — 신간 목록
export async function getNewReleases(limit = 10): Promise<BookSummary[]> {
  const list = USE_MOCK
    ? await mockDelay(mockGetNewReleases(limit))
    : await unwrap(
        apiClient.get<ApiResponse<BookSummaryResponse[]>>("/catalog/books/new-releases", {
          params: { limit },
        }),
      );
  return list.map(toBookSummary);
}

// GET /api/catalog/books/{bookId} — 도서 상세
// 비로그인도 조회된다. 토큰이 있으면 서버가 sub 로 최근 본 상품에 기록한다.
export async function getBook(bookId: number | string): Promise<Book> {
  const dto = USE_MOCK
    ? await mockDelay(mockGetBook(bookId))
    : await unwrap(apiClient.get<ApiResponse<BookDetailResponse>>(`/catalog/books/${bookId}`));
  return toBook(dto);
}

// GET /api/catalog/books/{bookId}/ebook — 버튼을 누를 때 호출해 만료 시간이 짧은 URL을 받는다.
export async function getEbookAccess(bookId: number | string): Promise<EbookAccessResponse> {
  return USE_MOCK
    ? await mockDelay(mockGetEbookAccess(bookId))
    : await unwrap(
        apiClient.get<ApiResponse<EbookAccessResponse>>(`/catalog/books/${bookId}/ebook`),
      );
}

// GET /api/catalog/books/{bookId}/synopsis/detail — 현재 구현 범위는 상세 줄거리 텍스트 조회다.
export async function getSynopsisDetail(
  bookId: number | string,
): Promise<BookSynopsisDetailResponse> {
  return USE_MOCK
    ? await mockDelay(mockGetSynopsisDetail(bookId))
    : await unwrap(
        apiClient.get<ApiResponse<BookSynopsisDetailResponse>>(
          `/catalog/books/${bookId}/synopsis/detail`,
        ),
      );
}

// 기존 웹툰 컷 UI의 문단 변환 호환 함수. 백엔드는 현재 이미지가 아니라 줄거리 텍스트를 반환한다.
export async function getWebtoonCuts(bookId: number | string): Promise<WebtoonCut[]> {
  return toWebtoonCuts(await getSynopsisDetail(bookId));
}
