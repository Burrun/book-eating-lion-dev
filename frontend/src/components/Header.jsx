import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Search, Heart, ShoppingBag, User, BookOpen } from "lucide-react";
import { useAuth } from "../context/AuthContext.jsx";
import { getMyProfile } from "../api/member.ts";

const NAV_LINKS = [
  { label: "베스트셀러", to: "/best" },
  { label: "신간", to: "/new" },
  { label: "분야별", to: "/category" },
];

// 마이페이지 안에서는 같은 자리가 서점 카테고리 대신 마이페이지 하위 내비가 된다.
// 마이페이지에 들어온 사람이 찾는 건 베스트셀러가 아니라 자기 책장이다.
const MYPAGE_NAV_LINKS = [
  { label: "마이페이지", to: "/mypage" },
  { label: "이북 보관함", to: "/mypage/library" },
];

export default function Header({ cartCount = 0, wishlistCount = 0 }) {
  const [query, setQuery] = useState("");
  const { isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const isMyPageArea = pathname.startsWith("/mypage");
  const queryClient = useQueryClient();
  const { data: profile } = useQuery({
    queryKey: ["myProfile"],
    queryFn: getMyProfile,
    enabled: isAuthenticated,
  });
  const isAdmin = isAuthenticated && profile?.role === "ADMIN";

  const handleLogout = () => {
    logout();
    // 로그아웃하면 장바구니 조회 기준이 서버 카트 -> 게스트 카트로 바뀌므로 뱃지도 다시 조회한다.
    queryClient.invalidateQueries({ queryKey: ["cart"] });
    navigate("/");
  };

  // 검색 결과는 목록 화면이 ?q= 로 받아서 처리한다.
  function handleSearch(e) {
    e.preventDefault();
    const keyword = query.trim();
    if (!keyword) return;
    navigate(`/?q=${encodeURIComponent(keyword)}`);
    setQuery("");
  }

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
            onSubmit={handleSearch}
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
            {isAdmin && (
              <Link
                to="/admin"
                className="hidden text-sm font-medium text-[var(--color-ink)]/70 transition-colors hover:text-[var(--color-coral)] sm:block"
              >
                관리자
              </Link>
            )}
          </nav>
        </div>

        {/* 카테고리 내비게이션 — 마이페이지 안에서는 마이페이지 하위 내비로 바뀐다.
            서점 쪽은 sm 이상에서만 보이지만(모바일은 검색으로 간다), 마이페이지 하위 내비는
            이북 보관함으로 가는 유일한 경로라 모바일에서도 보여야 한다. */}
        {isMyPageArea ? (
          <div className="mx-auto flex max-w-6xl gap-1 px-6 sm:px-6">
            {MYPAGE_NAV_LINKS.map((link) => {
              // /mypage 는 하위 경로의 접두사이기도 해서 startsWith 로 보면 항상 켜진다.
              const isActive = pathname === link.to;
              return (
                <Link
                  key={link.to}
                  to={link.to}
                  aria-current={isActive ? "page" : undefined}
                  className={`border-b-2 px-4 py-3 text-sm font-medium transition-colors ${
                    isActive
                      ? "border-[var(--color-honey)] text-[var(--color-forest)]"
                      : "border-transparent text-[var(--color-ink)] opacity-50 hover:opacity-80"
                  }`}
                >
                  {link.label}
                </Link>
              );
            })}
          </div>
        ) : (
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
        )}
      </div>
    </header>
  );
}
