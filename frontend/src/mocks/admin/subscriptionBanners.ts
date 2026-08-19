import type {
  SubscriptionBannerResponse,
  SubscriptionBannerWriteRequest,
} from "../../api/types.ts";

let banners: SubscriptionBannerResponse[] = [
  {
    bannerId: 1,
    imageUrl: "https://cdn.example.com/banners/subscribe-launch.png",
    title: "정기구독 첫 달 무료",
    linkUrl: "/mypage?tab=addresses",
    startAt: "2026-01-01T00:00:00",
    endAt: "2026-12-31T23:59:00",
    sortOrder: 0,
    active: true,
    createdAt: "2026-07-01T09:00:00",
    updatedAt: "2026-07-01T09:00:00",
  },
];

function nextBannerId(): number {
  return banners.reduce((max, b) => Math.max(max, b.bannerId), 0) + 1;
}

export function mockGetAdminBanners(): SubscriptionBannerResponse[] {
  return [...banners].sort((a, b) => a.sortOrder - b.sortOrder || a.bannerId - b.bannerId);
}

export function mockGetAdminBanner(
  bannerId: number | string,
): SubscriptionBannerResponse | undefined {
  return banners.find((b) => String(b.bannerId) === String(bannerId));
}

export function mockCreateBanner(body: SubscriptionBannerWriteRequest): SubscriptionBannerResponse {
  const now = new Date().toISOString();
  const banner: SubscriptionBannerResponse = {
    bannerId: nextBannerId(),
    ...body,
    linkUrl: body.linkUrl ?? null,
    createdAt: now,
    updatedAt: now,
  };
  banners = [...banners, banner];
  return banner;
}

export function mockUpdateBanner(
  bannerId: number | string,
  body: SubscriptionBannerWriteRequest,
): SubscriptionBannerResponse | undefined {
  let updated: SubscriptionBannerResponse | undefined;
  banners = banners.map((b) => {
    if (String(b.bannerId) !== String(bannerId)) return b;
    updated = { ...b, ...body, linkUrl: body.linkUrl ?? null, updatedAt: new Date().toISOString() };
    return updated;
  });
  return updated;
}

export function mockDeactivateBanner(bannerId: number | string): void {
  banners = banners.map((b) =>
    String(b.bannerId) === String(bannerId) ? { ...b, active: false } : b,
  );
}
