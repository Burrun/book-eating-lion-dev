import { useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { motion } from "framer-motion";

// 카카오페이 결제 승인 콜백 페이지.
// 실제 리다이렉트 URL 경로와 쿼리파라미터명(예: pg_token)은 백엔드 스펙 확정 후 카카오 문서 기준으로 다시 맞춘다.
export default function KakaoPaySuccess() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  useEffect(() => {
    // 나중에 실제 파라미터명을 확인하기 쉽도록 쿼리스트링 전체를 로그로 남겨둔다.
    console.log("[KakaoPaySuccess] query params:", Object.fromEntries(searchParams.entries()));

    // TODO: 여기서 백엔드 승인 API 호출 예정 (엔드포인트 확정 후 구현)
    // 예상 흐름: pg_token 등을 백엔드로 전달 → 백엔드가 카카오페이 승인 API 호출 → 결제 완료 처리

    // 백엔드 연동 전까지는 흐름만 시뮬레이션 — 3초 뒤 주문내역으로 이동.
    // 주문완료 전용 페이지가 아직 없어서 Checkout.jsx와 동일하게 마이페이지 주문내역 탭으로 보낸다.
    const timer = setTimeout(() => navigate("/mypage?tab=orders"), 3000);
    return () => clearTimeout(timer);
  }, [navigate, searchParams]);

  return (
    <div className="mx-auto flex max-w-md flex-col items-center px-4 py-24 text-center sm:px-6">
      <motion.div
        animate={{ rotate: 360 }}
        transition={{ duration: 1.2, repeat: Infinity, ease: "linear" }}
        className="mb-6 h-10 w-10 rounded-full border-4 border-[var(--color-honey)]/30 border-t-[var(--color-honey)]"
      />
      <h1 className="font-display mb-2 text-xl text-[var(--color-forest)]">결제 처리 중입니다...</h1>
      <p className="text-sm text-[var(--color-ink)] opacity-60">
        카카오페이 결제를 확인하고 있어요. 잠시만 기다려주세요.
      </p>
    </div>
  );
}
