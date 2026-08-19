import type {
  CategoryCreateRequest,
  CategoryResponse,
  CategoryUpdateRequest,
} from "../../api/types.ts";

let categories: CategoryResponse[] = [
  { categoryId: 1, categoryName: "IT/컴퓨터", parentId: null, sortOrder: 0, active: true },
  { categoryId: 2, categoryName: "소설", parentId: null, sortOrder: 1, active: true },
  { categoryId: 3, categoryName: "자기계발", parentId: null, sortOrder: 2, active: true },
];

function nextCategoryId(): number {
  return categories.reduce((max, c) => Math.max(max, c.categoryId), 0) + 1;
}

export function mockGetAdminCategories(): CategoryResponse[] {
  return categories;
}

export function mockCreateCategory(body: CategoryCreateRequest): CategoryResponse {
  const category: CategoryResponse = {
    categoryId: nextCategoryId(),
    categoryName: body.categoryName,
    parentId: body.parentId ?? null,
    sortOrder: body.sortOrder,
    active: true,
  };
  categories = [...categories, category];
  return category;
}

export function mockUpdateCategory(
  categoryId: number | string,
  body: CategoryUpdateRequest,
): CategoryResponse | undefined {
  let updated: CategoryResponse | undefined;
  categories = categories.map((c) => {
    if (String(c.categoryId) !== String(categoryId)) return c;
    updated = {
      ...c,
      categoryName: body.categoryName,
      parentId: body.parentId ?? null,
      sortOrder: body.sortOrder,
    };
    return updated;
  });
  return updated;
}

export function mockDeactivateCategory(categoryId: number | string): void {
  categories = categories.map((c) =>
    String(c.categoryId) === String(categoryId) ? { ...c, active: false } : c,
  );
}
