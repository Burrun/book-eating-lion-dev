// EPUB 뷰어에서 한 번에 긁을 수 있는 원문 길이(자).
//
// 요구사항은 "한 번에 1페이지까지"였지만 epub.js 의 화면 페이지는 CSS multi-column 으로
// 잘리는 가상 페이지라 창 크기·폰트 크기에 따라 경계가 달라진다(같은 책도 데스크탑 ~2,000자,
// 모바일 ~600자). 기기마다 달라지는 값을 저장 규칙으로 쓸 수 없어 글자 수로 대신 건다.
// 참고로 선택 자체는 spine item(챕터 XHTML 파일 1개, 실측 1만~6만자)을 넘지 못한다 —
// iframe 경계라서다. 즉 이 상한이 없으면 챕터 전체가 통째로 긁힌다.
//
// 🔴 백엔드 catalog.highlight.max-selected-chars 와 같은 값이어야 한다. 프론트가 더 크면
// 사용자는 다 긁고 나서 저장 단계에서 거절당한다.
//
// 값은 .env.development / .env.production 이 준다 — 여기 폴백을 두지 않는다. 폴백이 있으면
// env 배선이 끊겨도(실제로 오래 그런 상태였다) 아무도 모른 채 상수값으로 돌아간다.
// 누락은 vite.config.ts 가 빌드 시점에 잡는다.
export const MAX_SELECTED_CHARS = Number(import.meta.env.VITE_HIGHLIGHT_MAX_SELECTED_CHARS);
