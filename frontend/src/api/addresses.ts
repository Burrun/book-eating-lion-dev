import { apiClient, unwrap } from "./client.ts";
import { toAddress } from "./mappers.ts";
import { mockDelay } from "../mocks/delay.ts";
import {
  mockCreateAddress,
  mockDeleteAddress,
  mockGetAddresses,
  mockUpdateAddress,
} from "../mocks/addresses.ts";
import type {
  AddressCreateRequest,
  AddressResponse,
  AddressUpdateRequest,
  ApiResponse,
} from "./types.ts";
import type { Address } from "../types/address.ts";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// GET /api/members/me/addresses — 내 배송지 목록
export async function getMyAddresses(): Promise<Address[]> {
  if (USE_MOCK) return mockDelay(mockGetAddresses().map(toAddress));
  const list = await unwrap(apiClient.get<ApiResponse<AddressResponse[]>>("/members/me/addresses"));
  return list.map(toAddress);
}

// POST /api/members/me/addresses — 배송지 등록
export async function createAddress(body: AddressCreateRequest): Promise<Address> {
  if (USE_MOCK) return mockDelay(toAddress(mockCreateAddress(body)));
  return toAddress(
    await unwrap(apiClient.post<ApiResponse<AddressResponse>>("/members/me/addresses", body)),
  );
}

// PATCH /api/members/me/addresses/{addressId} — 배송지 수정 (null 필드는 유지)
export async function updateAddress(
  addressId: number | string,
  body: AddressUpdateRequest,
): Promise<Address> {
  if (USE_MOCK) {
    const updated = mockUpdateAddress(addressId, body);
    if (!updated) throw new Error("배송지를 찾을 수 없습니다.");
    return mockDelay(toAddress(updated));
  }
  return toAddress(
    await unwrap(
      apiClient.patch<ApiResponse<AddressResponse>>(`/members/me/addresses/${addressId}`, body),
    ),
  );
}

// DELETE /api/members/me/addresses/{addressId} — 배송지 삭제 (204, 본문 없음)
export async function deleteAddress(addressId: number | string): Promise<void> {
  if (USE_MOCK) {
    mockDeleteAddress(addressId);
    await mockDelay(undefined);
    return;
  }
  await apiClient.delete(`/members/me/addresses/${addressId}`);
}
