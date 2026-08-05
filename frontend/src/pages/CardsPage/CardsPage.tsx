import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getMyCards, issueCard, updateCard } from "../../api/cards.ts";
import type { Card, CardStatus } from "../../types/card.ts";

const CARDS_QUERY_KEY = ["cards"];

/** 금액은 원 단위로 주고받고, 화면과 입력에서는 만원 단위로 다룬다. */
const MAN = 10000;

const STATUS_LABEL: Record<CardStatus, string> = {
  ACTIVE: "사용중",
  SUSPENDED: "정지됨",
  TERMINATED: "해지됨",
};

// 카드 앞면(어두운 배경) 위에 올라가는 뱃지 색상
const STATUS_CLASS: Record<CardStatus, string> = {
  ACTIVE: "bg-paper/20 text-paper",
  SUSPENDED: "bg-honey text-forest",
  TERMINATED: "bg-coral text-paper",
};

/** 1000000 -> "100만" */
const toMan = (won: number) => (won / MAN).toLocaleString();

/** "2029-03-31" -> "03/29" (실제 카드 유효기간 표기) */
function formatExpiry(isoDate: string): string {
  const [year, month] = isoDate.split("-");
  return `${month}/${year.slice(2)}`;
}

export default function CardsPage() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: CARDS_QUERY_KEY });

  const {
    data: cards,
    isPending,
    isError,
  } = useQuery({
    queryKey: CARDS_QUERY_KEY,
    queryFn: getMyCards,
  });

  const issue = useMutation({ mutationFn: issueCard, onSuccess: invalidate });
  const update = useMutation({
    mutationFn: ({ id, monthlyLimit, cardStatus }: UpdateArgs) =>
      updateCard(id, { monthlyLimit, cardStatus }),
    onSuccess: invalidate,
  });

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
      <section className="flex flex-col gap-4 rounded-2xl border border-forest/10 bg-white p-6">
        <h1 className="text-xl font-bold">💳 가상카드 관리</h1>
        <p className="text-sm text-forest/60">
          모의 결제용 가상카드입니다. 실제 결제나 실물 카드와는 무관합니다.
        </p>
        <IssueCardForm onSubmit={issue.mutate} isSubmitting={issue.isPending} />
        {issue.isError ? (
          <p className="text-sm text-coral">
            카드 발급에 실패했습니다. 잠시 후 다시 시도해 주세요.
          </p>
        ) : null}
      </section>

      <section className="flex flex-col gap-4 rounded-2xl border border-forest/10 bg-white p-6">
        <h2 className="text-lg font-bold">보유 카드</h2>

        {isPending ? (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {[0, 1].map((i) => (
              <div key={i} className="skeleton-shimmer aspect-[8/5] rounded-2xl" />
            ))}
          </div>
        ) : isError ? (
          <p className="py-10 text-center text-sm text-coral">카드 목록을 불러오지 못했습니다.</p>
        ) : !cards || cards.length === 0 ? (
          <p className="py-10 text-center text-sm text-forest/60">
            발급된 카드가 없습니다. 위에서 새 카드를 발급해 보세요.
          </p>
        ) : (
          <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {cards.map((card) => (
              <CardItem
                key={card.id}
                card={card}
                onUpdate={update.mutate}
                isUpdating={update.isPending && update.variables?.id === card.id}
              />
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}

interface UpdateArgs {
  id: string;
  monthlyLimit?: number;
  cardStatus?: CardStatus;
}

/** 만원 단위 입력값을 검증하고 원 단위로 변환한다. */
function useManInput(initialWon: number) {
  const [text, setText] = useState(String(initialWon / MAN));
  const parsed = Number(text);
  const isValid = Number.isFinite(parsed) && parsed > 0;
  return { text, setText, isValid, won: parsed * MAN };
}

interface IssueCardFormProps {
  onSubmit: (body: { monthlyLimit: number }) => void;
  isSubmitting: boolean;
}

function IssueCardForm({ onSubmit, isSubmitting }: IssueCardFormProps) {
  const limit = useManInput(1000000);

  return (
    <form
      className="flex flex-col gap-3 rounded-xl border border-forest/10 bg-paper p-4 sm:flex-row sm:items-end"
      onSubmit={(e) => {
        e.preventDefault();
        if (limit.isValid) onSubmit({ monthlyLimit: limit.won });
      }}
    >
      <label className="flex flex-1 flex-col gap-1.5">
        <span className="text-sm font-semibold">월 한도 (만원)</span>
        <input
          type="number"
          min={1}
          step={1}
          value={limit.text}
          onChange={(e) => limit.setText(e.target.value)}
          className="rounded-lg border border-forest/20 px-4 py-2.5 outline-none focus:border-forest"
        />
      </label>
      <button
        type="submit"
        disabled={!limit.isValid || isSubmitting}
        className="shrink-0 rounded-lg bg-forest px-6 py-2.5 font-semibold text-paper transition hover:bg-forest-light disabled:opacity-40"
      >
        {isSubmitting ? "발급 중..." : "새 카드 발급"}
      </button>
    </form>
  );
}

interface CardItemProps {
  card: Card;
  onUpdate: (args: UpdateArgs) => void;
  isUpdating: boolean;
}

function CardItem({ card, onUpdate, isUpdating }: CardItemProps) {
  const [isEditing, setIsEditing] = useState(false);
  const limit = useManInput(card.monthlyLimit);

  const isActive = card.status === "ACTIVE";
  const isTerminated = card.status === "TERMINATED";

  return (
    <li className="flex flex-col gap-2.5">
      {/* 실제 신용카드 비율(85.6 x 54mm ≈ 8:5) */}
      <div
        className={`flex aspect-[8/5] flex-col justify-between rounded-2xl p-4 shadow-sm ${
          isActive
            ? "bg-gradient-to-br from-forest to-forest-light text-paper"
            : "bg-gradient-to-br from-forest/50 to-forest/35 text-paper"
        }`}
      >
        <div className="flex items-start justify-between gap-2">
          <span className="text-sm font-semibold">{card.company ?? "카드사 미지정"}</span>
          <div className="flex shrink-0 gap-1">
            {card.isDefault ? (
              <span className="rounded-full bg-paper px-2 py-0.5 text-[11px] font-semibold text-forest">
                기본
              </span>
            ) : null}
            <span
              className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${STATUS_CLASS[card.status]}`}
            >
              {STATUS_LABEL[card.status]}
            </span>
          </div>
        </div>

        <p className="font-mono text-base tracking-[0.12em] sm:text-lg">{card.maskedNumber}</p>

        <div className="flex flex-col gap-1">
          <div className="flex items-baseline justify-between text-xs">
            <span className="opacity-80">
              {toMan(card.currentUsage)}만 / {toMan(card.monthlyLimit)}만원
            </span>
            <span className="font-semibold">{Math.round(card.usageRatio * 100)}%</span>
          </div>
          <div className="h-1.5 overflow-hidden rounded-full bg-paper/25">
            <div
              className={`h-full rounded-full ${card.usageRatio >= 1 ? "bg-coral" : "bg-honey"}`}
              style={{ width: `${card.usageRatio * 100}%` }}
            />
          </div>
          <div className="flex justify-between text-[11px] opacity-70">
            <span>잔액 {toMan(card.virtualBalance)}만원</span>
            <span>VALID THRU {formatExpiry(card.expiryDate)}</span>
          </div>
        </div>
      </div>

      {isEditing ? (
        <div className="flex gap-2">
          <input
            type="number"
            min={1}
            step={1}
            value={limit.text}
            onChange={(e) => limit.setText(e.target.value)}
            aria-label="월 한도 (만원)"
            className="w-full min-w-0 rounded-lg border border-forest/20 px-3 py-2 text-sm outline-none focus:border-forest"
          />
          <button
            type="button"
            disabled={!limit.isValid || isUpdating}
            onClick={() => {
              onUpdate({ id: card.id, monthlyLimit: limit.won });
              setIsEditing(false);
            }}
            className="shrink-0 rounded-lg bg-forest px-3 py-2 text-sm font-semibold text-paper transition hover:bg-forest-light disabled:opacity-40"
          >
            저장
          </button>
          <button
            type="button"
            onClick={() => {
              limit.setText(String(card.monthlyLimit / MAN));
              setIsEditing(false);
            }}
            className="shrink-0 rounded-lg border border-forest/20 px-3 py-2 text-sm font-semibold transition hover:bg-paper"
          >
            취소
          </button>
        </div>
      ) : (
        <div className="flex gap-2">
          <button
            type="button"
            disabled={isTerminated || isUpdating}
            onClick={() => setIsEditing(true)}
            className="flex-1 rounded-lg border border-forest/20 px-3 py-2 text-sm font-semibold transition hover:bg-paper disabled:opacity-40"
          >
            한도 변경
          </button>
          <button
            type="button"
            disabled={isTerminated || isUpdating}
            onClick={() => onUpdate({ id: card.id, cardStatus: isActive ? "SUSPENDED" : "ACTIVE" })}
            className="flex-1 rounded-lg border border-forest/20 px-3 py-2 text-sm font-semibold transition hover:bg-paper disabled:opacity-40"
          >
            {isActive ? "일시 정지" : "정지 해제"}
          </button>
        </div>
      )}
    </li>
  );
}
