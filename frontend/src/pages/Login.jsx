import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { BookOpen } from "lucide-react";
import Button from "../components/Button.jsx";
import { useToast } from "../components/Toast.jsx";
import { useAuth } from "../context/AuthContext.jsx";
import { login, mergeCart } from "../api/auth.js";
import { getGuestCartItems, clearGuestCart } from "../api/cart.js";

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();
  const { login: authLogin } = useAuth();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const redirectTo = location.state?.from?.pathname ?? "/";

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email.trim() || !password.trim()) {
      toast.error("이메일과 비밀번호를 입력해주세요.");
      return;
    }

    setIsSubmitting(true);
    try {
      const tokenResponse = await login(email, password);
      authLogin(tokenResponse);

      const guestItems = getGuestCartItems();
      try {
        await mergeCart(guestItems);
        clearGuestCart();
      } catch {
        // 백엔드 cart 모듈이 아직 없어 병합은 항상 실패할 수 있다(BOO-23 TODO).
        // 병합 실패가 로그인 자체(토큰 저장, 리다이렉트)를 막으면 안 되므로 여기서 무시한다.
      }

      navigate(redirectTo, { replace: true });
    } catch (err) {
      const message =
        err.response?.data?.error?.message ??
        err.response?.data?.message ??
        "로그인에 실패했습니다. 잠시 후 다시 시도해주세요.";
      toast.error(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="mx-auto flex max-w-4xl items-stretch px-4 py-12 sm:px-6">
      <div className="flex w-full overflow-hidden rounded-2xl bg-white shadow-[0_10px_30px_rgba(27,59,54,0.10)]">
        {/* 왼쪽 브랜드 스트립 */}
        <div className="hidden w-64 shrink-0 flex-col justify-between bg-[var(--color-forest)] p-8 text-[var(--color-paper)] sm:flex">
          <div className="flex items-center gap-2">
            <span className="flex h-9 w-9 items-center justify-center rounded-full bg-[var(--color-honey)] text-[var(--color-forest)]">
              <BookOpen size={18} strokeWidth={2.25} />
            </span>
            <span className="font-display text-lg">책 먹는 사자</span>
          </div>
          <p className="text-sm leading-relaxed text-[var(--color-paper)]/70">
            로그인하고 담아둔 책을
            <br />
            그대로 이어서 결제해보세요.
          </p>
        </div>

        {/* 로그인 폼 */}
        <div className="flex flex-1 flex-col justify-center p-8 sm:p-10">
          <h1 className="font-display mb-1 text-2xl text-[var(--color-forest)]">로그인</h1>
          <p className="mb-6 text-sm text-[var(--color-ink)] opacity-70">
            책 먹는 사자와 함께 다음 책을 골라보세요.
          </p>

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium text-[var(--color-ink)] opacity-80">이메일</span>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
                autoComplete="email"
                className="w-full rounded-xl border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
              />
            </label>

            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium text-[var(--color-ink)] opacity-80">비밀번호</span>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="비밀번호를 입력하세요"
                autoComplete="current-password"
                className="w-full rounded-xl border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
              />
            </label>

            <Button type="submit" variant="primary" size="lg" fullWidth loading={isSubmitting} className="mt-2">
              로그인
            </Button>
          </form>
        </div>
      </div>
    </div>
  );
}
