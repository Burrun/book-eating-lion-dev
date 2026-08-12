// 가상카드 목업. mock/실API 응답 구조를 동일하게 맞춰(CardResponse) 전환 시 매퍼/화면을 그대로 재사용한다.
import type { CardIssueRequest, CardResponse, CardStatus } from "../api/types.ts";

const DEFAULT_MONTHLY_LIMIT = 1_000_000;

const INITIAL_CARDS: CardResponse[] = [
  {
    cardId: 1,
    maskedCardNumber: "4921-****-****-7734",
    cardStatus: "ACTIVE",
    monthlyLimit: 1000000,
    currentUsage: 342000,
    issuedDate: "2026-03-14",
    expiryDate: "2029-03-14",
  },
  {
    cardId: 2,
    maskedCardNumber: "5310-****-****-1082",
    cardStatus: "SUSPENDED",
    monthlyLimit: 300000,
    currentUsage: 300000,
    issuedDate: "2026-01-05",
    expiryDate: "2029-01-05",
  },
];

let cards: CardResponse[] = [...INITIAL_CARDS];

const randomDigits = (length: number) =>
  Array.from({ length }, () => Math.floor(Math.random() * 10)).join("");

function nextCardId(): number {
  return cards.reduce((max, card) => Math.max(max, card.cardId), 0) + 1;
}

/** 발급일 기준 3년 뒤를 만료일로 잡는다 (backend Card.java: LocalDate.now().plusYears(3)). */
function addYears(date: Date, years: number): Date {
  const next = new Date(date);
  next.setFullYear(next.getFullYear() + years);
  return next;
}

const toIsoDate = (date: Date) => date.toISOString().slice(0, 10);

export function mockGetCards(): CardResponse[] {
  return cards;
}

export function mockIssueCard({ monthlyLimit }: CardIssueRequest = {}): CardResponse {
  const today = new Date();
  const card: CardResponse = {
    cardId: nextCardId(),
    maskedCardNumber: `${randomDigits(4)}-****-****-${randomDigits(4)}`,
    cardStatus: "ACTIVE",
    monthlyLimit: monthlyLimit ?? DEFAULT_MONTHLY_LIMIT,
    currentUsage: 0,
    issuedDate: toIsoDate(today),
    expiryDate: toIsoDate(addYears(today, 3)),
  };
  cards = [...cards, card];
  return card;
}

export function mockUpdateCardStatus(
  cardId: number | string,
  cardStatus: CardStatus,
): CardResponse | undefined {
  let updated: CardResponse | undefined;
  cards = cards.map((card) => {
    if (String(card.cardId) !== String(cardId)) return card;
    updated = { ...card, cardStatus };
    return updated;
  });
  return updated;
}
