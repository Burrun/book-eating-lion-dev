import { apiClient, unwrap } from "../client.ts";
import { mockDelay } from "../../mocks/delay.ts";
import {
  mockCreateBanner,
  mockDeactivateBanner,
  mockGetAdminBanner,
  mockGetAdminBanners,
  mockUpdateBanner,
} from "../../mocks/admin/subscriptionBanners.ts";
import type { ApiResponse, SubscriptionBannerResponse, SubscriptionBannerWriteRequest } from "../types.ts";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// GET /api/catalog/admin/subscription-banners — 관리자용 전체 배너 목록 (기간 지남/비활성 포함)
export async function getAdminBanners(): Promise<SubscriptionBannerResponse[]> {
  if (USE_MOCK) return mockDelay(mockGetAdminBanners());
  return unwrap(
    apiClient.get<ApiResponse<SubscriptionBannerResponse[]>>("/catalog/admin/subscription-banners"),
  );
}

// GET /api/catalog/admin/subscription-banners/{bannerId} — 배너 상세 조회
export async function getAdminBanner(bannerId: number | string): Promise<SubscriptionBannerResponse> {
  if (USE_MOCK) {
    const banner = mockGetAdminBanner(bannerId);
    if (!banner) throw new Error("배너를 찾을 수 없습니다.");
    return mockDelay(banner);
  }
  return unwrap(
    apiClient.get<ApiResponse<SubscriptionBannerResponse>>(`/catalog/admin/subscription-banners/${bannerId}`),
  );
}

// POST /api/catalog/admin/subscription-banners — 배너 등록
export async function createBanner(
  body: SubscriptionBannerWriteRequest,
): Promise<SubscriptionBannerResponse> {
  if (USE_MOCK) return mockDelay(mockCreateBanner(body));
  return unwrap(
    apiClient.post<ApiResponse<SubscriptionBannerResponse>>("/catalog/admin/subscription-banners", body),
  );
}

// PATCH /api/catalog/admin/subscription-banners/{bannerId} — 배너 수정
export async function updateBanner(
  bannerId: number | string,
  body: SubscriptionBannerWriteRequest,
): Promise<SubscriptionBannerResponse> {
  if (USE_MOCK) {
    const updated = mockUpdateBanner(bannerId, body);
    if (!updated) throw new Error("배너를 찾을 수 없습니다.");
    return mockDelay(updated);
  }
  return unwrap(
    apiClient.patch<ApiResponse<SubscriptionBannerResponse>>(
      `/catalog/admin/subscription-banners/${bannerId}`,
      body,
    ),
  );
}

// DELETE /api/catalog/admin/subscription-banners/{bannerId} — 배너 비활성화 (하드 삭제 아님)
export async function deactivateBanner(bannerId: number | string): Promise<void> {
  if (USE_MOCK) {
    mockDeactivateBanner(bannerId);
    await mockDelay(undefined);
    return;
  }
  await apiClient.delete(`/catalog/admin/subscription-banners/${bannerId}`);
}
