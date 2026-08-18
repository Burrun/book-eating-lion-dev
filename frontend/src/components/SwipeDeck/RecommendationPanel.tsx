import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getRecommendationQueue, reactRecommendation } from "../../api/recommendations.ts";
import type { RecommendationQueueResponse } from "../../api/types.ts";
import type { SwipeDeckItem } from "../../types/book.ts";
import SwipeDeck from "./SwipeDeck.tsx";

const QUERY_KEY = ["recommendation-queue"];

export default function RecommendationPanel() {
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const queue = useQuery({
    queryKey: QUERY_KEY,
    queryFn: () => getRecommendationQueue(false),
    retry: false,
  });
  const reaction = useMutation({ mutationFn: reactRecommendation });

  async function handleReaction(bookId: string, direction: "like" | "skip") {
    if (!queue.data) return;
    setError(null);
    try {
      await reaction.mutateAsync({
        queueId: queue.data.queueId,
        bookId: Number(bookId),
        action: direction === "like" ? "LIKE" : "SKIP",
      });
      queryClient.setQueryData<RecommendationQueueResponse>(QUERY_KEY, (current) =>
        current
          ? { ...current, cards: current.cards.filter((card) => card.bookId !== Number(bookId)) }
          : current,
      );
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "반응을 저장하지 못했습니다.");
      throw cause;
    }
  }

  if (queue.isPending) {
    return <div className="skeleton-shimmer h-96 w-full max-w-sm rounded-2xl" />;
  }

  if (queue.isError) {
    return (
      <div className="w-full max-w-sm rounded-2xl border border-forest/10 bg-white p-5 text-center text-sm text-forest/60 shadow-lg">
        로그인 후 맞춤 추천을 이용할 수 있어요.
      </div>
    );
  }

  const items: SwipeDeckItem[] = queue.data.cards.map((card) => ({
    id: String(card.bookId),
    title: card.title,
    reason: card.recommendationReason,
  }));

  return (
    <div className="flex flex-col items-center gap-2">
      <SwipeDeck items={items} onReaction={handleReaction} />
      {error ? <p className="text-center text-xs text-coral">{error}</p> : null}
      {items.length === 0 ? (
        <button
          type="button"
          onClick={() => void queue.refetch()}
          className="rounded-full border border-forest/15 bg-white px-4 py-2 text-sm font-semibold text-forest"
        >
          새로운 추천 받기
        </button>
      ) : null}
    </div>
  );
}
