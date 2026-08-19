import { Navigate, useLocation } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import ProtectedRoute from "./ProtectedRoute.jsx";
import { getMyProfile } from "../api/member.ts";

// 진짜 인가 경계는 백엔드 SecurityConfig(/api/catalog/admin/** → hasRole("ADMIN"))다.
// 여기서는 관리자가 아닌 회원이 화면 자체를 보지 못하게 막는 UX 차원의 방어 게이트만 둔다.
function AdminGate({ children }) {
  const location = useLocation();
  const { data: profile, isPending } = useQuery({
    queryKey: ["myProfile"],
    queryFn: getMyProfile,
  });

  if (isPending) {
    return <div className="skeleton-shimmer mx-auto mt-10 h-40 max-w-6xl rounded-2xl" />;
  }

  if (profile?.role !== "ADMIN") {
    return <Navigate to="/" state={{ from: location }} replace />;
  }

  return children;
}

export default function AdminRoute({ children }) {
  return (
    <ProtectedRoute>
      <AdminGate>{children}</AdminGate>
    </ProtectedRoute>
  );
}
