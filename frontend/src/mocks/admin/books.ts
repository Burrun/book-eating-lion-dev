import type {
  AdminBookCreateRequest,
  AdminBookResponse,
  AdminBookUpdateRequest,
  EpubUploadUrlResponse,
  Page,
} from "../../api/types.ts";

let books: AdminBookResponse[] = [
  {
    bookId: 1,
    title: "자바 ORM 표준 JPA 프로그래밍",
    author: "김영한",
    publisher: "에이콘출판",
    isbn: "9791161750914",
    category: "IT/컴퓨터",
    price: 38700,
    coverImageUrl: null,
    description: "JPA 표준 스펙 해설서",
    detailedSynopsis: null,
    ebookAvailable: true,
    epubS3Key: "epubs/seed_jpa.epub",
    saleStatus: "ON_SALE",
    publishedDate: "2015-08-15",
    salesCount: 120,
    averageRating: 4.5,
    reviewCount: 12,
    deleted: false,
    deletedAt: null,
    createdAt: "2026-07-01T09:00:00",
    updatedAt: "2026-07-01T09:00:00",
  },
];

function nextBookId(): number {
  return books.reduce((max, b) => Math.max(max, b.bookId), 0) + 1;
}

export function mockGetAdminBooks({
  includeDeleted = false,
  page = 0,
  size = 20,
}: {
  includeDeleted?: boolean;
  page?: number;
  size?: number;
}): Page<AdminBookResponse> {
  const matched = includeDeleted ? books : books.filter((b) => !b.deleted);
  const totalPages = Math.max(1, Math.ceil(matched.length / size));
  const number = Math.min(Math.max(page, 0), totalPages - 1);
  return {
    content: matched.slice(number * size, number * size + size),
    number,
    size,
    totalElements: matched.length,
    totalPages,
    first: number === 0,
    last: number === totalPages - 1,
  };
}

export function mockGetAdminBook(bookId: number | string): AdminBookResponse | undefined {
  return books.find((b) => String(b.bookId) === String(bookId));
}

export function mockCreateBook(body: AdminBookCreateRequest): AdminBookResponse {
  const now = new Date().toISOString();
  const book: AdminBookResponse = {
    bookId: nextBookId(),
    title: body.title,
    author: body.author,
    publisher: body.publisher,
    isbn: body.isbn,
    category: body.category,
    price: body.price,
    coverImageUrl: body.coverImageUrl ?? null,
    description: body.description ?? null,
    detailedSynopsis: body.detailedSynopsis ?? null,
    ebookAvailable: Boolean(body.epubS3Key),
    epubS3Key: body.epubS3Key ?? null,
    saleStatus: body.saleStatus ?? "ON_SALE",
    publishedDate: body.publishedDate ?? null,
    salesCount: 0,
    averageRating: 0,
    reviewCount: 0,
    deleted: false,
    deletedAt: null,
    createdAt: now,
    updatedAt: now,
  };
  books = [...books, book];
  return book;
}

export function mockUpdateBook(
  bookId: number | string,
  body: AdminBookUpdateRequest,
): AdminBookResponse | undefined {
  let updated: AdminBookResponse | undefined;
  books = books.map((b) => {
    if (String(b.bookId) !== String(bookId)) return b;
    updated = {
      ...b,
      ...Object.fromEntries(Object.entries(body).filter(([, v]) => v !== undefined && v !== null)),
      ebookAvailable: body.epubS3Key !== undefined ? Boolean(body.epubS3Key) : b.ebookAvailable,
      updatedAt: new Date().toISOString(),
    };
    return updated;
  });
  return updated;
}

export function mockDeleteBook(bookId: number | string): void {
  books = books.map((b) =>
    String(b.bookId) === String(bookId)
      ? { ...b, deleted: true, deletedAt: new Date().toISOString(), saleStatus: "STOPPED" }
      : b,
  );
}

export function mockIssueEpubUploadUrl(fileName: string): EpubUploadUrlResponse {
  return {
    uploadUrl: `https://mock-s3.example.com/upload?file=${encodeURIComponent(fileName)}`,
    epubS3Key: `epubs/mock_${Date.now()}_${fileName}`,
    expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
  };
}
