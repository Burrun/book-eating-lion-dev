import { Link } from "react-router-dom";
import { Heart, ShoppingBag, BookOpen } from "lucide-react";

const BADGE_STYLES = {
  new: "bg-[var(--color-honey)] text-[var(--color-forest)]",
  best: "bg-[var(--color-coral)] text-white",
};

export default function BookCard({
  book,
  wishlisted = false,
  onToggleWishlist,
  onAddToCart,
}) {
  const { id, title, author, coverUrl, price, originalPrice, badge } = book;
  const discountRate =
    originalPrice && originalPrice > price
      ? Math.round((1 - price / originalPrice) * 100)
      : null;

  return (
    <div className="group relative flex w-full flex-col overflow-hidden rounded-2xl bg-white shadow-[0_1px_3px_rgba(27,59,54,0.08)] transition-all duration-200 hover:-translate-y-1 hover:shadow-[0_12px_24px_rgba(27,59,54,0.12)]">
      {/* 표지 */}
      <Link to={`/books/${id}`} className="relative block aspect-[3/4] overflow-hidden bg-[var(--color-forest)]/5">
        {coverUrl ? (
          <img
            src={coverUrl}
            alt={title}
            className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
            loading="lazy"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center text-[var(--color-forest)]/25">
            <BookOpen size={40} strokeWidth={1.5} />
          </div>
        )}

        {badge && (
          <span
            className={`absolute top-2 left-2 rounded-full px-2 py-0.5 text-[11px] font-bold ${BADGE_STYLES[badge] ?? BADGE_STYLES.new}`}
          >
            {badge === "best" ? "BEST" : "NEW"}
          </span>
        )}

        {/* 빠른 담기 버튼: hover 시 표지 위로 떠오름 */}
        <button
          type="button"
          aria-label="장바구니에 담기"
          onClick={(e) => {
            e.preventDefault();
            onAddToCart?.(book);
          }}
          className="absolute right-2 bottom-2 flex h-9 w-9 translate-y-2 items-center justify-center rounded-full bg-[var(--color-honey)] text-[var(--color-forest)] opacity-0 shadow-md transition-all duration-200 hover:brightness-105 group-hover:translate-y-0 group-hover:opacity-100"
        >
          <ShoppingBag size={16} />
        </button>
      </Link>

      {/* 찜 버튼 */}
      <button
        type="button"
        aria-label={wishlisted ? "찜 해제" : "찜하기"}
        onClick={() => onToggleWishlist?.(book)}
        className="absolute top-2 right-2 flex h-8 w-8 items-center justify-center rounded-full bg-white/90 text-[var(--color-coral)] shadow-sm transition-transform hover:scale-110"
      >
        <Heart size={16} fill={wishlisted ? "var(--color-coral)" : "none"} />
      </button>

      {/* 정보 */}
      <div className="flex flex-1 flex-col gap-1 p-3.5">
        <Link to={`/books/${id}`} className="line-clamp-2 text-sm font-medium leading-snug text-[var(--color-ink)] hover:text-[var(--color-coral)]">
          {title}
        </Link>
        <p className="text-sm text-[var(--color-ink)] opacity-70">{author}</p>

        <div className="mt-1.5 flex items-baseline gap-1.5">
          {discountRate && (
            <span className="font-display text-sm text-[var(--color-coral)]">
              {discountRate}%
            </span>
          )}
          <span className="font-display text-lg text-[var(--color-ink)]">
            {price.toLocaleString()}원
          </span>
        </div>
        {originalPrice && discountRate && (
          <span className="text-sm text-[var(--color-ink)] opacity-40 line-through">
            {originalPrice.toLocaleString()}원
          </span>
        )}
      </div>
    </div>
  );
}
