// UI 전용 배송지 타입. 백엔드 DTO(src/api/types.ts)와 분리한다.

export interface Address {
  id: string;
  recipientName: string;
  phoneNumber: string;
  zipcode: string;
  address: string;
  detailAddress: string | null;
  isDefault: boolean;
}
