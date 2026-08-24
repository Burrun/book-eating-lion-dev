import { useEffect, useRef, useState } from "react";
import { motion, useAnimation, useMotionValue, useTransform, type PanInfo } from "framer-motion";
import type { SwipeDeckItem } from "../../types/book.ts";

const SWIPE_THRESHOLD = 120;
const EXIT_DISTANCE = 600;

type SwipeDirection = "like" | "skip";

interface SwipeCardProps {
  book: SwipeDeckItem;
  isTop: boolean;
  forcedSwipe: SwipeDirection | null;
  onSwipe: (direction: SwipeDirection) => Promise<void>;
}

function SwipeCard({ book, isTop, forcedSwipe, onSwipe }: SwipeCardProps) {
  const controls = useAnimation();
  const x = useMotionValue(0);
  const rotate = useTransform(x, [-300, 300], [-20, 20]);
  const completing = useRef(false);

  async function completeSwipe(direction: SwipeDirection) {
    if (completing.current) return;
    completing.current = true;
    await controls.start({
      x: direction === "like" ? EXIT_DISTANCE : -EXIT_DISTANCE,
      rotate: direction === "like" ? 25 : -25,
      opacity: 0,
      transition: { duration: 0.3 },
    });
    try {
      await onSwipe(direction);
    } catch {
      await controls.start({ x: 0, rotate: 0, opacity: 1, transition: { type: "spring" } });
    } finally {
      completing.current = false;
    }
  }

  useEffect(() => {
    if (isTop && forcedSwipe) {
      void completeSwipe(forcedSwipe);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [forcedSwipe, isTop]);

  async function handleDragEnd(_event: MouseEvent | TouchEvent | PointerEvent, info: PanInfo) {
    if (Math.abs(info.offset.x) > SWIPE_THRESHOLD) {
      const direction: SwipeDirection = info.offset.x > 0 ? "like" : "skip";
      await completeSwipe(direction);
    } else {
      controls.start({
        x: 0,
        rotate: 0,
        transition: { type: "spring", stiffness: 320, damping: 22 },
      });
    }
  }

  return (
    <motion.div
      className={`absolute inset-0 flex flex-col gap-3 rounded-2xl border border-forest/10 bg-white p-5 shadow-xl ${
        isTop ? "cursor-grab active:cursor-grabbing" : "scale-95 opacity-70"
      }`}
      style={{ zIndex: isTop ? 10 : 0, ...(isTop ? { x, rotate } : {}) }}
      drag={isTop ? "x" : false}
      dragElastic={0.7}
      onDragEnd={isTop ? handleDragEnd : undefined}
      animate={controls}
    >
      <div className="flex h-56 items-center justify-center overflow-hidden rounded-xl border border-forest/10 bg-paper text-5xl">
        {book.coverImageUrl ? (
          <img
            src={book.coverImageUrl}
            alt={book.title}
            className="h-full w-full object-cover"
          />
        ) : (
          "📖"
        )}
      </div>
      <p className="font-heading text-lg font-bold">{book.title}</p>
      <p className="text-sm text-[#7950f2]">{book.reason}</p>
    </motion.div>
  );
}

interface SwipeDeckProps {
  items: SwipeDeckItem[];
  onReaction?: (bookId: string, direction: SwipeDirection) => Promise<void>;
}

export default function SwipeDeck({ items, onReaction }: SwipeDeckProps) {
  const [forcedSwipe, setForcedSwipe] = useState<SwipeDirection | null>(null);

  async function handleSwipe(bookId: string, direction: SwipeDirection) {
    // 응답으로 items가 갱신되기 전에 해제해야 다음 최상단 카드가 같은 명령을 상속하지 않는다.
    setForcedSwipe(null);
    await onReaction?.(bookId, direction);
  }

  function triggerSwipe(direction: SwipeDirection) {
    if (forcedSwipe || items.length === 0) return;
    setForcedSwipe(direction);
  }

  if (items.length === 0) {
    return (
      <div className="flex h-80 w-full max-w-sm items-center justify-center rounded-2xl border border-dashed border-forest/20 bg-white text-center text-forest/60">
        오늘의 추천을 모두 확인했어요 🦁
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center gap-6">
      <div className="relative h-96 w-full max-w-sm">
        {items.slice(0, 2).map((book, i) => (
          <SwipeCard
            key={book.id}
            book={book}
            isTop={i === 0}
            forcedSwipe={i === 0 ? forcedSwipe : null}
            onSwipe={(direction) => handleSwipe(book.id, direction)}
          />
        ))}
      </div>
      <div className="flex gap-4">
        <button
          onClick={() => triggerSwipe("skip")}
          className="rounded-full bg-[#ffe3e3] px-6 py-3 font-semibold text-[#e03131] transition hover:brightness-95"
        >
          ❌ 스킵
        </button>
        <button
          onClick={() => triggerSwipe("like")}
          className="rounded-full bg-[#d3f9d8] px-6 py-3 font-semibold text-[#2b8a3e] transition hover:brightness-95"
        >
          ❤️ 좋아요
        </button>
      </div>
    </div>
  );
}
