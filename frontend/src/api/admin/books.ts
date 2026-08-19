import { apiClient, unwrap } from "../client.ts";
import { mockDelay } from "../../mocks/delay.ts";
import {
  mockCreateBook,
  mockDeleteBook,
  mockGetAdminBook,
  mockGetAdminBooks,
  mockIssueEpubUploadUrl,
  mockUpdateBook,
} from "../../mocks/admin/books.ts";
import type {
  AdminBookCreateRequest,
  AdminBookResponse,
  AdminBookUpdateRequest,
  ApiResponse,
  EpubUploadUrlResponse,
  Page,
} from "../types.ts";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// GET /api/catalog/admin/books — 관리자용 도서 목록 (페이징, 기본은 삭제 제외)
export async function getAdminBooks(
  params: { includeDeleted?: boolean; page?: number; size?: number } = {},
): Promise<Page<AdminBookResponse>> {
  if (USE_MOCK) return mockDelay(mockGetAdminBooks(params));
  return unwrap(apiClient.get<ApiResponse<Page<AdminBookResponse>>>("/catalog/admin/books", { params }));
}

// GET /api/catalog/admin/books/{bookId} — 도서 상세
export async function getAdminBook(bookId: number | string): Promise<AdminBookResponse> {
  if (USE_MOCK) {
    const book = mockGetAdminBook(bookId);
    if (!book) throw new Error("도서를 찾을 수 없습니다.");
    return mockDelay(book);
  }
  return unwrap(apiClient.get<ApiResponse<AdminBookResponse>>(`/catalog/admin/books/${bookId}`));
}

// POST /api/catalog/admin/books — 신간 등록. epubS3Key는 issueEpubUploadUrl + uploadEpubFile로
// 먼저 업로드를 끝낸 뒤 그 결과를 넣는다(선택 — 없으면 종이책만 등록).
export async function createBook(body: AdminBookCreateRequest): Promise<AdminBookResponse> {
  if (USE_MOCK) return mockDelay(mockCreateBook(body));
  return unwrap(apiClient.post<ApiResponse<AdminBookResponse>>("/catalog/admin/books", body));
}

// PATCH /api/catalog/admin/books/{bookId} — 전달한 필드만 수정
export async function updateBook(
  bookId: number | string,
  body: AdminBookUpdateRequest,
): Promise<AdminBookResponse> {
  if (USE_MOCK) {
    const updated = mockUpdateBook(bookId, body);
    if (!updated) throw new Error("도서를 찾을 수 없습니다.");
    return mockDelay(updated);
  }
  return unwrap(apiClient.patch<ApiResponse<AdminBookResponse>>(`/catalog/admin/books/${bookId}`, body));
}

// DELETE /api/catalog/admin/books/{bookId} — 소프트 삭제
export async function deleteBook(bookId: number | string): Promise<void> {
  if (USE_MOCK) {
    mockDeleteBook(bookId);
    await mockDelay(undefined);
    return;
  }
  await apiClient.delete(`/catalog/admin/books/${bookId}`);
}

// POST /api/catalog/admin/books/epub-upload-url — EPUB 업로드용 presigned URL 발급.
// EBOOK_STORAGE=local(로컬 기본값)에서는 503 — mock 모드에서만 실제로 값을 준다.
export async function issueEpubUploadUrl(fileName: string): Promise<EpubUploadUrlResponse> {
  if (USE_MOCK) return mockDelay(mockIssueEpubUploadUrl(fileName));
  return unwrap(
    apiClient.post<ApiResponse<EpubUploadUrlResponse>>("/catalog/admin/books/epub-upload-url", {
      fileName,
    }),
  );
}

// uploadUrl로 파일 바이트를 직접 PUT한다 — S3로 바로 나가는 요청이라 apiClient(baseURL/인증
// 헤더 인터셉터)를 거치지 않고 순수 fetch를 쓴다. presigned URL 자체가 인가 수단이라
// Authorization 헤더를 실어 보내면 안 된다.
export async function uploadEpubFile(uploadUrl: string, file: File): Promise<void> {
  if (USE_MOCK) {
    await mockDelay(undefined, 600);
    return;
  }
  const res = await fetch(uploadUrl, {
    method: "PUT",
    headers: { "Content-Type": "application/epub+zip" },
    body: file,
  });
  if (!res.ok) {
    throw new Error(`EPUB 업로드에 실패했습니다 (${res.status})`);
  }
}
