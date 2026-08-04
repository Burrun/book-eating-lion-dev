const VARIANT_CLASSES = {
  text: "h-4 rounded-md",
  circular: "rounded-full",
  rectangular: "rounded-xl",
};

export default function Skeleton({ variant = "rectangular", className = "", style }) {
  return (
    <div
      aria-hidden="true"
      className={`skeleton-shimmer ${VARIANT_CLASSES[variant]} ${className}`}
      style={style}
    />
  );
}

// BookCard 로딩 상태용 프리셋
export function SkeletonBookCard() {
  return (
    <div className="flex w-full flex-col overflow-hidden rounded-2xl bg-white shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
      <Skeleton variant="rectangular" className="aspect-[3/4] w-full rounded-none" />
      <div className="flex flex-col gap-2 p-3.5">
        <Skeleton variant="text" className="w-full" />
        <Skeleton variant="text" className="w-2/3" />
        <Skeleton variant="text" className="mt-1 h-5 w-1/2" />
      </div>
    </div>
  );
}
