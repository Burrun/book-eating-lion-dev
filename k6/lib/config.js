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
