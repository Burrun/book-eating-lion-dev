// 배송지 목업. mock/실API 응답 구조를 동일하게 맞춰(AddressResponse) 전환 시 매퍼/화면을 그대로 재사용한다.
import type { AddressCreateRequest, AddressResponse, AddressUpdateRequest } from "../api/types.ts";

const INITIAL_ADDRESSES: AddressResponse[] = [
  {
    id: 1,
    recipientName: "홍길동",
    phoneNumber: "010-1234-5678",
    zipcode: "06236",
    address: "서울시 강남구 테헤란로 152",
    detailAddress: "역삼빌딩 10층",
    isDefault: true,
  },
];

let addresses: AddressResponse[] = [...INITIAL_ADDRESSES];

function nextAddressId(): number {
  return addresses.reduce((max, address) => Math.max(max, address.id ?? 0), 0) + 1;
}

export function mockGetAddresses(): AddressResponse[] {
  return addresses;
}

export function mockCreateAddress(body: AddressCreateRequest): AddressResponse {
  if (body.isDefault) {
    addresses = addresses.map((a) => ({ ...a, isDefault: false }));
  }
  const address: AddressResponse = { id: nextAddressId(), ...body };
  addresses = [...addresses, address];
  return address;
}

export function mockUpdateAddress(
  addressId: number | string,
  body: AddressUpdateRequest,
): AddressResponse | undefined {
  if (body.isDefault === true) {
    addresses = addresses.map((a) => ({ ...a, isDefault: false }));
  }
  let updated: AddressResponse | undefined;
  addresses = addresses.map((a) => {
    if (String(a.id) !== String(addressId)) return a;
    updated = {
      ...a,
      ...(body.recipientName != null && { recipientName: body.recipientName }),
      ...(body.phoneNumber != null && { phoneNumber: body.phoneNumber }),
      ...(body.zipcode != null && { zipcode: body.zipcode }),
      ...(body.address != null && { address: body.address }),
      ...(body.detailAddress != null && { detailAddress: body.detailAddress }),
      ...(body.isDefault != null && { isDefault: body.isDefault }),
    };
    return updated;
  });
  return updated;
}

export function mockDeleteAddress(addressId: number | string): void {
  addresses = addresses.filter((a) => String(a.id) !== String(addressId));
}
