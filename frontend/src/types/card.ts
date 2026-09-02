// UI 전용 가상카드 타입. 백엔드 DTO(src/api/types.ts)와 분리한다.

/** 카드 상태. ACTIVE 만 결제에 사용할 수 있다. CLOSED는 영구 해지(재활성 불가). */
export type CardStatus = "ACTIVE" | "SUSPENDED" | "CLOSED";

export interface Card {
  id: string;
  maskedNumber: string;
  status: CardStatus;
  monthlyLimit: number;
  currentUsage: number;
  /** 이번 달 남은 결제 가능 금액. monthlyLimit - currentUsage 에서 파생한다(0 미만 방어). */
  availableLimit: number;
  /** 이번 달 한도 소진율(0~1). currentUsage/monthlyLimit 에서 파생한다. */
  usageRatio: number;
  issuedDate: string;
  expiryDate: string;
}
