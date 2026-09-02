import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { CheckCircle } from "lucide-react";

// 결제 승인은 KakaoPayCallback.jsx에서 이미 끝난 뒤에만 이 화면으로 이동한다 —
// 여기서는 승인 API를 다시 부르지 않고 완료 결과만 보여준다.
export default function KakaoPaySuccess() {
  const navigate = useNavigate();

  useEffect(() => {
    // 주문완료 전용 페이지가 아직 없어서 임시로 마이페이지 주문내역 탭으로 이동
    const timer = setTimeout(() => navigate("/mypage?tab=orders"), 2000);
    return () => clearTimeout(timer);
  }, [navigate]);

  return (
    <div className="mx-auto flex max-w-md flex-col items-center px-4 py-24 text-center sm:px-6">
      <CheckCircle size={40} className="mb-4 text-[var(--color-forest)]" />
      <h1 className="font-display mb-2 text-xl text-[var(--color-forest)]">
        결제가 완료되었습니다
      </h1>
      <p className="text-sm text-[var(--color-ink)] opacity-60">주문내역 페이지로 이동합니다...</p>
    </div>
  );
}
