import { apiClient, unwrap } from './client.js'
import { toBook, toBookSummary, toPaged, toWebtoonCuts } from './mappers.js'
import type {
  ApiResponse,
  BookDetailResponse,
  BookSummaryResponse,
  BookSynopsisDetailResponse,
  Page,
} from './types.js'
import type { Book, BookSummary, WebtoonCut } from '../types/book.js'
import type { Paged } from '../types/common.js'

// GET /api/books — 도서 목록 (카테고리/페이징)
export async function getBooks(
  params: { category?: string; page?: number; size?: number; sort?: string } = {},
): Promise<Paged<BookSummary>> {
  const page = await unwrap(apiClient.get<ApiResponse<Page<BookSummaryResponse>>>('/books', { params }))
  return toPaged(page, toBookSummary)
}

// GET /api/books/search?q= — 도서 검색
export async function searchBooks(params: {
  q: string
  page?: number
  size?: number
}): Promise<Paged<BookSummary>> {
  const page = await unwrap(apiClient.get<ApiResponse<Page<BookSummaryResponse>>>('/books/search', { params }))
  return toPaged(page, toBookSummary)
}

// GET /api/books/bestsellers — 베스트셀러 목록
export async function getBestsellers(limit = 10): Promise<BookSummary[]> {
  const list = await unwrap(
    apiClient.get<ApiResponse<BookSummaryResponse[]>>('/books/bestsellers', { params: { limit } }),
  )
  return list.map(toBookSummary)
}

// GET /api/books/new-releases — 신간 목록
export async function getNewReleases(limit = 10): Promise<BookSummary[]> {
  const list = await unwrap(
    apiClient.get<ApiResponse<BookSummaryResponse[]>>('/books/new-releases', { params: { limit } }),
  )
  return list.map(toBookSummary)
}

// GET /api/books/{bookId} — 도서 상세 (X-Member-Id 있으면 최근 본 상품에 기록됨)
export async function getBook(bookId: number | string): Promise<Book> {
  return toBook(await unwrap(apiClient.get<ApiResponse<BookDetailResponse>>(`/books/${bookId}`)))
}

// GET /api/books/{bookId}/synopsis/detail — 구독 회원 전용 웹툰 요약 컷
export async function getWebtoonCuts(bookId: number | string): Promise<WebtoonCut[]> {
  return toWebtoonCuts(
    await unwrap(apiClient.get<ApiResponse<BookSynopsisDetailResponse>>(`/books/${bookId}/synopsis/detail`)),
  )
}
