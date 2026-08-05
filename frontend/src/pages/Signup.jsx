import { Link } from "react-router-dom";
import { BookOpen } from "lucide-react";
import Button from "../components/Button.jsx";

// TODO(BOO-23): 회원가입 화면은 별도 티켓 범위 — 지금은 진입점(라우트)만 확보해둔 placeholder.
export default function Signup() {
  return (
    <div className="mx-auto flex max-w-4xl items-stretch px-4 py-12 sm:px-6">
      <div className="flex w-full flex-col items-center gap-4 overflow-hidden rounded-2xl bg-white p-10 text-center shadow-[0_10px_30px_rgba(27,59,54,0.10)]">
        <span className="flex h-12 w-12 items-center justify-center rounded-full bg-[var(--color-honey)] text-[var(--color-forest)]">
          <BookOpen size={22} strokeWidth={2.25} />
        </span>
        <h1 className="font-display text-2xl text-[var(--color-forest)]">회원가입</h1>
        <p className="text-sm text-[var(--color-ink)] opacity-70">
          회원가입 화면은 준비 중이에요. 조금만 기다려주세요!
        </p>
        <Link to="/login">
          <Button variant="secondary" size="md" shimmer={false} className="mt-2">
            로그인으로 돌아가기
          </Button>
        </Link>
      </div>
    </div>
  );
}
