// 공통 설정 — 전부 환경변수(-e KEY=VALUE)로 덮어쓴다. 코드 수정 없이
// EC2 단일 배포 / EKS MSA 배포를 오가며 같은 스크립트를 재사용하기 위함이다.
//
// nginx(docker-compose, ./nginx/default.conf)에는 /api/cart, /api/coupons,
// /api/cards, /ws/ai/chat 라우팅이 없다(2025-08 기준). 이 경로가 필요한 시나리오는
// BASE_URL이 아니라 ORDER_URL/MEMBER_URL을 서비스 포트로 직접 override 해서 우회한다
// (docker-compose 기준 order=8082, member=8083, ai=8084).

export const TARGET_ENV = __ENV.TARGET_ENV || 'unknown'; // 예: ec2-single, eks-msa — 결과 비교 축
export const RUN_LABEL = __ENV.RUN_LABEL || 'default'; // 예: baseline, cache-on, rds-proxy-on — 같은 TARGET_ENV 안에서의 비교 축

const BASE_URL = __ENV.BASE_URL || 'http://localhost';

export const CATALOG_URL = __ENV.CATALOG_URL || `${BASE_URL}`;
export const ORDER_URL = __ENV.ORDER_URL || `${BASE_URL}`;
export const MEMBER_URL = __ENV.MEMBER_URL || `${BASE_URL}`;
export const AI_URL = __ENV.AI_URL || `${BASE_URL}`;
export const WS_URL = __ENV.WS_URL || (BASE_URL.replace(/^http/, 'ws'));

// db/postgres/90-demo-data.sql 시드 기준 — 재고 정확히 100개인 유일한 ON_SALE 도서.
export const TEST_BOOK_ID = Number(__ENV.TEST_BOOK_ID || 1);
export const TEST_BOOK_CATEGORY = __ENV.TEST_BOOK_CATEGORY || 'IT/컴퓨터';

// 03-pod-failure.js가 쓰는 재고. book_id=1(TEST_BOOK_ID)은 05-payment-concurrency.js가
// 소진시키는 자원이라 공유하면 03이 05 뒤에 도니 "재고 0 → catalogClient 호출 전에
// 즉시 400" 으로 카탈로그 의존 경로를 아예 안 타게 된다(order-v1.yaml OrderService:
// checkStock이 catalogClient.getBook()보다 먼저 실행됨). 시드에 재고가 있는 다른
// book_id(101/102)를 기본으로 써서 05와 자원을 분리한다.
export const SECONDARY_BOOK_ID = Number(__ENV.SECONDARY_BOOK_ID || 101);

// Cognito 실계정. setup()에서 1회만 로그인해 토큰을 전 VU가 공유한다(§0-7).
export const LOGIN_EMAIL = __ENV.LOGIN_EMAIL;
export const LOGIN_PASSWORD = __ENV.LOGIN_PASSWORD;

export function requireAuthEnv() {
  if (!LOGIN_EMAIL || !LOGIN_PASSWORD) {
    throw new Error(
      'LOGIN_EMAIL / LOGIN_PASSWORD 가 없다. Cognito 테스트 계정을 -e LOGIN_EMAIL=... -e LOGIN_PASSWORD=... 로 전달할 것.',
    );
  }
}

// 콤마로 구분된 -e 값을 배열로 판다(예: -e CHAT_CUSTOMER_EMAILS=a@x.com,b@x.com,c@x.com).
function parseList(raw) {
  return (raw || '')
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

// 06-chat-concurrency.js 전용. 상담 채팅은 "1인 1방 강제"(ChatRoomStore.openOrResume)라
// 05/07처럼 로그인 토큰 하나를 여러 VU가 공유하면 전부 같은 방 하나로 몰려 "여러 세션이
// 서로 다른 Pod에서 브로드캐스트를 받는지"를 검증할 수 없다(§0-10) — 그래서 채팅만
// 계정 풀이 필요하다. 상담사 계정은 Cognito `cognito:groups`에 ADMIN이 있어야 한다
// (ai-v1.yaml POST /api/ai/bot/chat/ticket 설명 참고).
export const CHAT_CUSTOMER_EMAILS = parseList(__ENV.CHAT_CUSTOMER_EMAILS);
export const CHAT_CUSTOMER_PASSWORDS = parseList(__ENV.CHAT_CUSTOMER_PASSWORDS);
export const CHAT_AGENT_EMAIL = __ENV.CHAT_AGENT_EMAIL;
export const CHAT_AGENT_PASSWORD = __ENV.CHAT_AGENT_PASSWORD;

export function requireChatEnv() {
  if (
    CHAT_CUSTOMER_EMAILS.length === 0 ||
    CHAT_CUSTOMER_EMAILS.length !== CHAT_CUSTOMER_PASSWORDS.length ||
    !CHAT_AGENT_EMAIL ||
    !CHAT_AGENT_PASSWORD
  ) {
    throw new Error(
      'CHAT_CUSTOMER_EMAILS/CHAT_CUSTOMER_PASSWORDS(콤마로 구분, 개수 일치)와 ' +
        'CHAT_AGENT_EMAIL/CHAT_AGENT_PASSWORD(ADMIN 그룹 계정)가 모두 필요하다 — README §0-10 참고.',
    );
  }
}
