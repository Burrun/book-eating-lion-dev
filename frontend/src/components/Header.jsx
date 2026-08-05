import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Search, Heart, ShoppingBag, User, BookOpen } from "lucide-react";
import { useAuth } from "../context/AuthContext.jsx";

const NAV_LINKS = [
  { label: "베스트셀러", to: "/best" },
  { label: "신간", to: "/new" },
  { label: "분야별", to: "/category" },
  { label: "중고서점", to: "/used" },
];

export default function Header({ cartCount = 0, wishlistCount = 0 }) {
  const [query, setQuery] = useState("");
  const { isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate("/");
  };

  return (
    <header className="sticky top-0 z-50 bg-[var(--color-paper)]">
      {/* 사자 갈기를 연상시키는 얇은 honey 액센트 라인 */}
      <div className="h-1.5 bg-[repeating-linear-gradient(90deg,var(--color-honey)_0px,var(--color-honey)_18px,transparent_18px,transparent_26px)]" />

      <div className="border-b border-[var(--color-forest)]/15">
        <div className="mx-auto flex max-w-6xl items-center gap-4 px-4 py-3 sm:gap-6 sm:px-6">
          {/* 로고 */}
          <Link
            to="/"
            className="group flex shrink-0 items-center gap-2"
            aria-label="책 먹는 사자 홈으로 이동"
          >
            <span className="relative flex h-9 w-9 items-center justify-center rounded-full bg-[var(--color-forest)] text-[var(--color-honey)] transition-transform group-hover:-rotate-6">
              <BookOpen size={18} strokeWidth={2.25} />
            </span>
            <span className="font-display hidden text-lg leading-none tracking-tight text-[var(--color-forest)] sm:block">
              책 먹는 사자
            </span>
          </Link>

          {/* 검색창 */}
          <form
            role="search"
            className="flex flex-1 items-center gap-2 rounded-full border-2 border-[var(--color-forest)]/20 bg-white px-4 py-2 transition-colors focus-within:border-[var(--color-honey)]"
            onSubmit={(e) => e.preventDefault()}
          >
            <Search size={18} className="shrink-0 text-[var(--color-forest)]/50" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="책 제목, 작가, ISBN으로 검색"
              className="w-full min-w-0 bg-transparent text-sm text-[var(--color-ink)] placeholder:text-[var(--color-ink)]/40 focus:outline-none"
            />
          </form>

          {/* 우측 아이콘 */}
          <nav className="flex shrink-0 items-center gap-1 sm:gap-2">
            <Link
              to="/wishlist"
              aria-label="찜 목록"
              className="relative flex h-10 w-10 items-center justify-center rounded-full text-[var(--color-forest)] transition-colors hover:bg-[var(--color-coral)]/10 hover:text-[var(--color-coral)]"
            >
              <Heart size={20} />
              {wishlistCount > 0 && (
                <span className="absolute -top-0.5 -right-0.5 flex h-4.5 min-w-4.5 items-center justify-center rounded-full bg-[var(--color-coral)] px-1 text-[10px] font-bold text-white">
                  {wishlistCount}
                </span>
              )}
            </Link>
            <Link
              to="/cart"
              aria-label="장바구니"
              className="relative flex h-10 w-10 items-center justify-center rounded-full text-[var(--color-forest)] transition-colors hover:bg-[var(--color-honey)]/15"
            >
              <ShoppingBag size={20} />
              {cartCount > 0 && (
                <span className="absolute -top-0.5 -right-0.5 flex h-4.5 min-w-4.5 items-center justify-center rounded-full bg-[var(--color-honey)] px-1 text-[10px] font-bold text-[var(--color-forest)]">
                  {cartCount}
                </span>
              )}
            </Link>
            {isAuthenticated ? (
              <button
                type="button"
                onClick={handleLogout}
                className="hidden text-sm font-medium text-[var(--color-ink)]/70 transition-colors hover:text-[var(--color-coral)] sm:block"
              >
                로그아웃
              </button>
            ) : (
              <Link
                to="/login"
                className="hidden text-sm font-medium text-[var(--color-ink)]/70 transition-colors hover:text-[var(--color-coral)] sm:block"
              >
                로그인
              </Link>
            )}
            <Link
              to="/mypage"
              aria-label="마이페이지"
              className="flex h-10 w-10 items-center justify-center rounded-full text-[var(--color-forest)] transition-colors hover:bg-[var(--color-forest)]/10"
            >
              <User size={20} />
            </Link>
          </nav>
        </div>

        {/* 카테고리 내비게이션 */}
        <div className="mx-auto hidden max-w-6xl gap-6 px-6 pb-3 sm:flex">
          {NAV_LINKS.map((link) => (
            <Link
              key={link.to}
              to={link.to}
              className="text-sm font-medium text-[var(--color-ink)]/70 transition-colors hover:text-[var(--color-coral)]"
            >
              {link.label}
            </Link>
          ))}
        </div>
      </div>
    </header>
  );
}
