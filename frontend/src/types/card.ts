// UI 전용 가상카드 타입. 백엔드 DTO(src/api/types.ts)와 분리한다.

/** 카드 상태. ACTIVE 만 결제에 사용할 수 있다. */
export type CardStatus = "ACTIVE" | "SUSPENDED" | "TERMINATED";

export interface Card {
  id: string;
  /** 카드사명. 미지정일 수 있다. */
  company: string | null;
  maskedNumber: string;
  status: CardStatus;
  monthlyLimit: number;
  currentUsage: number;
  virtualBalance: number;
  isDefault: boolean;
  issuedDate: string;
  expiryDate: string;
  /** 이번 달 한도 소진율(0~1). currentUsage/monthlyLimit 에서 파생한다. */
  usageRatio: number;
}
