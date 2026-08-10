// 가상카드 목업. 백엔드에 card 모듈이 아직 없어(2026-08-05 기준) 목업으로만 동작한다.
// DTO 형태(CardResponse)로 두어 실 API 전환 시 매퍼/화면을 그대로 재사용한다.
import type { CardIssueRequest, CardResponse, CardUpdateRequest } from "../api/types.ts";

const INITIAL_CARDS: CardResponse[] = [
  {
    id: 1,
    cardCompany: "사자카드",
    maskedCardNumber: "4921-****-****-7734",
    cardStatus: "ACTIVE",
    monthlyLimit: 1000000,
    currentUsage: 342000,
    virtualBalance: 658000,
    isDefault: true,
    issuedDate: "2026-03-14",
    expiryDate: "2029-03-31",
  },
  {
    id: 2,
    cardCompany: "책먹은카드",
    maskedCardNumber: "5310-****-****-1082",
    cardStatus: "SUSPENDED",
    monthlyLimit: 300000,
    currentUsage: 300000,
    virtualBalance: 0,
    isDefault: false,
    issuedDate: "2026-01-05",
    expiryDate: "2028-01-31",
  },
];

let cards: CardResponse[] = [...INITIAL_CARDS];

const randomDigits = (length: number) =>
  Array.from({ length }, () => Math.floor(Math.random() * 10)).join("");

function nextCardId(): number {
  return cards.reduce((max, card) => Math.max(max, card.id), 0) + 1;
}

/** 발급일 기준 5년 뒤를 만료일로 잡는다(목업 규칙). */
function addYears(date: Date, years: number): Date {
  const next = new Date(date);
  next.setFullYear(next.getFullYear() + years);
  return next;
}

const toIsoDate = (date: Date) => date.toISOString().slice(0, 10);

export function mockGetCards(): CardResponse[] {
  return cards;
}

export function mockIssueCard({ monthlyLimit, virtualBalance, cardCompany }: CardIssueRequest): CardResponse {
  const today = new Date();
  const card: CardResponse = {
    id: nextCardId(),
    cardCompany: cardCompany ?? "사자카드",
    maskedCardNumber: `${randomDigits(4)}-****-****-${randomDigits(4)}`,
    cardStatus: "ACTIVE",
    monthlyLimit,
    currentUsage: 0,
    virtualBalance,
    // 첫 카드는 자동으로 기본 카드가 된다.
    isDefault: cards.length === 0,
    issuedDate: toIsoDate(today),
    expiryDate: toIsoDate(addYears(today, 5)),
  };
  cards = [...cards, card];
  return card;
}

export function mockUpdateCard(
  cardId: number | string,
  patch: CardUpdateRequest,
): CardResponse | undefined {
  let updated: CardResponse | undefined;
  cards = cards.map((card) => {
    if (String(card.id) !== String(cardId)) return card;
    updated = {
      ...card,
      monthlyLimit: patch.monthlyLimit ?? card.monthlyLimit,
      cardStatus: patch.cardStatus ?? card.cardStatus,
    };
    return updated;
  });
  return updated;
}
