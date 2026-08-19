import { useEffect, useRef } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { approveKakaoPayment } from "../../api/orders.js";

// 카카오페이가 실제로 리다이렉트하는 유일한 지점(backend RealKakaoPayClient.ready()가
// approval/cancel/fail URL을 전부 이 경로 하나 + status 쿼리파라미터로 통일해 만든다).
// status=cancel/fail 은 승인 시도 없이 결과 화면으로 보내고, 그 외(성공 후보)는 여기서
// POST /api/payments/kakao/approve 를 호출해 실제로 결제를 확정한 뒤 결과 화면으로 이동한다.
export default function KakaoPayCallback() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const queryClient = useQueryClient();
  // 승인은 멱등하지 않다(이미 처리된 주문은 409) — StrictMode 이중 마운트/재렌더로
  // approve가 두 번 불리지 않게 막는다.
  const hasRun = useRef(false);

  useEffect(() => {
    if (hasRun.current) return;
    hasRun.current = true;

    const orderId = searchParams.get("orderId");
    const status = searchParams.get("status");
    const pgToken = searchParams.get("pg_token");

    if (status === "cancel") {
      navigate("/payment/kakao/cancel", { replace: true });
      return;
    }
    if (status === "fail" || !orderId || !pgToken) {
      navigate("/payment/kakao/fail", { replace: true });
      return;
    }

    approveKakaoPayment(Number(orderId), pgToken)
      .then(() => {
        // 승인 성공 시 서버가 장바구니를 이미 비웠으므로 캐시만 무효화한다.
        queryClient.invalidateQueries({ queryKey: ["cart"] });
        navigate("/payment/kakao/success", { replace: true });
      })
      .catch(() => {
        navigate("/payment/kakao/fail", { replace: true });
      });
  }, [navigate, searchParams, queryClient]);

  return (
    <div className="mx-auto flex max-w-md flex-col items-center px-4 py-24 text-center sm:px-6">
      <motion.div
        animate={{ rotate: 360 }}
        transition={{ duration: 1.2, repeat: Infinity, ease: "linear" }}
        className="mb-6 h-10 w-10 rounded-full border-4 border-[var(--color-honey)]/30 border-t-[var(--color-honey)]"
      />
      <h1 className="font-display mb-2 text-xl text-[var(--color-forest)]">
        결제를 확인하고 있습니다...
      </h1>
      <p className="text-sm text-[var(--color-ink)] opacity-60">
        카카오페이 결제를 승인하고 있어요. 잠시만 기다려주세요.
      </p>
    </div>
  );
}
