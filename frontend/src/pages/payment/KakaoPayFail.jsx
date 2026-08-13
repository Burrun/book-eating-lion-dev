import { useNavigate } from "react-router-dom";
import { XCircle } from "lucide-react";
import Button from "../../components/Button.jsx";

// 카카오페이 결제 실패 콜백 페이지. 실제 콜백 경로/파라미터는 백엔드 스펙 확정 후 반영한다.
export default function KakaoPayFail() {
  const navigate = useNavigate();

  return (
    <div className="mx-auto flex max-w-md flex-col items-center px-4 py-24 text-center sm:px-6">
      <XCircle size={40} className="mb-4 text-[var(--color-coral)]" />
      <h1 className="font-display mb-2 text-xl text-[var(--color-forest)]">결제에 실패했습니다</h1>
      <p className="mb-6 text-sm text-[var(--color-ink)] opacity-60">
        결제 진행 중 문제가 발생했어요. 다시 시도해주세요.
      </p>
      <Button variant="primary" onClick={() => navigate("/checkout")}>
        결제 페이지로 돌아가기
      </Button>
    </div>
  );
}
