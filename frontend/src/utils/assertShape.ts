// 개발 모드 전용 런타임 계약 가드.
//
// mappers.ts는 백엔드 DTO를 그대로 프론트 도메인 타입에 옮긴다 — 타입 단언만 있을 뿐
// 런타임 검증은 없다. 그래서 계약(openapi yaml)의 required 필드가 실제 응답에서
// null/undefined로 오면(예: Checkout.jsx의 recipient.postalCode/address가 조용히
// null로 샜던 사고), 값이 몇 단계 뒤(서버 400, 결제 실패 토스트)에서야 드러나고
// 원인을 역추적하기 어렵다.
//
// assertRequiredFields는 매퍼가 DTO를 받는 시점에 계약상 필수 필드가 비어 있는지
// 바로 콘솔에 경고한다. 프로덕션 빌드(import.meta.env.DEV === false)에서는 아무
// 것도 하지 않으므로 런타임 비용이나 사용자 노출이 없다 — 타입스크립트 컴파일 타임
// 검사를 못 잡는 "서버가 계약과 다른 값을 보낸" 케이스를 개발 중에만 잡기 위한
// 보조 안전망이다.
export function assertRequiredFields<T extends Record<string, unknown>>(
  label: string,
  dto: T,
  requiredKeys: readonly (keyof T)[],
): T {
  if (import.meta.env.DEV) {
    const missing = requiredKeys.filter((key) => {
      const value = dto[key];
      return value === null || value === undefined || value === "";
    });
    if (missing.length > 0) {
      console.error(
        `[contract mismatch] ${label}: 계약상 필수 필드가 비어 있습니다 — ${missing.join(", ")}`,
        dto,
      );
    }
  }
  return dto;
}
