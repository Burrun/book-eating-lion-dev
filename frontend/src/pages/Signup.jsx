import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { BookOpen } from "lucide-react";
import Button from "../components/Button.jsx";
import { useToast } from "../components/Toast.jsx";
import { signup } from "../api/auth.js";

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MIN_PASSWORD_LENGTH = 8;

export default function Signup() {
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();

  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!name.trim() || !email.trim() || !password.trim() || !passwordConfirm.trim()) {
      toast.error("모든 항목을 입력해주세요.");
      return;
    }
    if (!EMAIL_PATTERN.test(email.trim())) {
      toast.error("이메일 형식이 올바르지 않습니다.");
      return;
    }
    if (password.length < MIN_PASSWORD_LENGTH) {
      toast.error(`비밀번호는 ${MIN_PASSWORD_LENGTH}자 이상이어야 합니다.`);
      return;
    }
    if (password !== passwordConfirm) {
      toast.error("비밀번호가 일치하지 않습니다.");
      return;
    }

    setIsSubmitting(true);
    try {
      await signup(email, password, name);
      toast.success("회원가입이 완료되었습니다. 로그인해주세요.");
      // 가입 API가 토큰을 돌려주지 않아(백엔드 명세 확인 결과) 자동 로그인은 하지 않고
      // 로그인 페이지로 보낸다. 원래 진입 경로(from)가 있었다면 그대로 이어서 전달한다.
      navigate("/login", { state: { from: location.state?.from, prefillEmail: email }, replace: true });
    } catch (err) {
      const message =
        err.response?.data?.error?.message ??
        err.response?.data?.message ??
        "회원가입에 실패했습니다. 잠시 후 다시 시도해주세요.";
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
            가입하고 나만의 책장을
            <br />
            사자와 함께 채워보세요.
          </p>
        </div>

        {/* 회원가입 폼 */}
        <div className="flex flex-1 flex-col justify-center p-8 sm:p-10">
          <h1 className="font-display mb-1 text-2xl text-[var(--color-forest)]">회원가입</h1>
          <p className="mb-6 text-sm text-[var(--color-ink)] opacity-70">
            책 먹는 사자의 새 식구가 되어보세요.
          </p>

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium text-[var(--color-ink)] opacity-80">이름</span>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="이름을 입력하세요"
                autoComplete="name"
                className="w-full rounded-xl border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
              />
            </label>

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
                placeholder="8자 이상 입력하세요"
                autoComplete="new-password"
                className="w-full rounded-xl border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
              />
            </label>

            <label className="flex flex-col gap-1.5">
              <span className="text-sm font-medium text-[var(--color-ink)] opacity-80">비밀번호 확인</span>
              <input
                type="password"
                value={passwordConfirm}
                onChange={(e) => setPasswordConfirm(e.target.value)}
                placeholder="비밀번호를 다시 입력하세요"
                autoComplete="new-password"
                className="w-full rounded-xl border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
              />
            </label>

            <Button type="submit" variant="primary" size="lg" fullWidth loading={isSubmitting} className="mt-2">
              회원가입
            </Button>
          </form>
        </div>
      </div>
    </div>
  );
}
