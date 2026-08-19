import { Link } from "react-router-dom";

const SECTIONS = [
  { to: "/admin/books", emoji: "📚", title: "신간 등록", desc: "도서 등록/수정 및 EPUB 업로드" },
  {
    to: "/admin/categories",
    emoji: "🗂️",
    title: "카테고리 관리",
    desc: "카테고리 등록/수정/비활성화",
  },
  { to: "/admin/faqs", emoji: "❓", title: "FAQ 관리", desc: "자주 묻는 질문 등록/수정/비활성화" },
  { to: "/admin/inquiries", emoji: "✉️", title: "상품문의 관리", desc: "회원 문의 조회 및 답변" },
  { to: "/admin/reviews", emoji: "⭐", title: "리뷰 조회", desc: "도서별 리뷰 열람" },
  { to: "/admin/coupons", emoji: "🎟️", title: "쿠폰 정책 관리", desc: "쿠폰 등록/조회/수정" },
  { to: "/admin/orders", emoji: "📦", title: "주문/배송 관리", desc: "전체 주문 조회 및 배송 상태 변경" },
  {
    to: "/admin/subscription-banners",
    emoji: "📣",
    title: "정기구독 배너 관리",
    desc: "홈 화면 홍보 배너 등록/수정/비활성화",
  },
];

export default function AdminPage() {
  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
      <section className="rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)]">
        <h1 className="font-display mb-1 text-xl text-[var(--color-forest)]">🛠️ 운영자 화면</h1>
        <p className="text-sm text-[var(--color-ink)] opacity-60">관리할 항목을 선택해 주세요.</p>
      </section>

      <ul className="grid gap-4 sm:grid-cols-2">
        {SECTIONS.map((section) => (
          <li key={section.to}>
            <Link
              to={section.to}
              className="flex h-full flex-col gap-2 rounded-2xl bg-white p-6 shadow-[0_1px_3px_rgba(27,59,54,0.08)] transition hover:shadow-[0_4px_12px_rgba(27,59,54,0.12)]"
            >
              <span className="text-2xl">{section.emoji}</span>
              <span className="font-display text-lg text-[var(--color-forest)]">
                {section.title}
              </span>
              <span className="text-sm text-[var(--color-ink)] opacity-60">{section.desc}</span>
            </Link>
          </li>
        ))}
      </ul>
    </main>
  );
}
