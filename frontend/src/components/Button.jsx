import { forwardRef } from "react";
import { Loader2 } from "lucide-react";

const VARIANT_CLASSES = {
  primary:
    "bg-[var(--color-honey)] text-[var(--color-forest)] shadow-sm hover:shadow-md hover:brightness-105 active:scale-[0.98]",
  secondary:
    "border-2 border-[var(--color-forest)] text-[var(--color-forest)] bg-transparent hover:bg-[var(--color-forest)] hover:text-[var(--color-paper)] active:scale-[0.98]",
  coral:
    "bg-[var(--color-coral)] text-white shadow-sm hover:shadow-md hover:brightness-105 active:scale-[0.98]",
  ghost:
    "bg-transparent text-[var(--color-forest)] hover:bg-[var(--color-forest)]/10 active:scale-[0.98]",
};

const SIZE_CLASSES = {
  sm: "px-3 py-1.5 text-sm gap-1.5",
  md: "px-5 py-2.5 text-sm gap-2",
  lg: "font-display px-6 py-3.5 text-base gap-2",
};

const Button = forwardRef(function Button(
  {
    variant = "primary",
    size = "md",
    fullWidth = false,
    loading = false,
    shimmer,
    disabled = false,
    leftIcon = null,
    rightIcon = null,
    className = "",
    children,
    ...rest
  },
  ref
) {
  // primary 버튼은 기본적으로 hover 시 사자 갈기 느낌의 shimmer가 쓸고 지나감
  const showShimmer = shimmer ?? variant === "primary";

  return (
    <button
      ref={ref}
      disabled={disabled || loading}
      className={`group relative inline-flex items-center justify-center overflow-hidden rounded-xl font-medium transition-all duration-200 disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100 ${VARIANT_CLASSES[variant]} ${SIZE_CLASSES[size]} ${fullWidth ? "w-full" : ""} ${className}`}
      {...rest}
    >
      {showShimmer && (
        <span
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 -translate-x-full bg-gradient-to-r from-transparent via-white/40 to-transparent transition-transform duration-700 ease-out group-hover:translate-x-full"
        />
      )}
      <span className="relative flex items-center justify-center gap-2">
        {loading ? (
          <Loader2 size={16} className="animate-spin" />
        ) : (
          leftIcon
        )}
        {children}
        {!loading && rightIcon}
      </span>
    </button>
  );
});

export default Button;
