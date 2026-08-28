// 장애 복구(Self-Healing) / 장애 격리(Fault Isolation).
//
// 이 스크립트는 부하 생성만 한다. "장애 유발"은 스크립트 밖에서 별도 터미널로
// 실행해야 한다(k6에는 인프라 제어 권한이 없다) — 정확한 명령어는
// k6/runbooks/chaos-actions.md 참고. 환경별로 명령이 다르다:
//   - EC2(docker-compose): docker stop msa-ai (재시작 정책이 없어 자동 복구 안 됨 — 그 자체가 비교 포인트)
//   - EKS: kubectl delete pod -n lion-app -l app=ai-rag --force
//
// 절차:
//   1) 이 스크립트를 시작한다(총 6분).
//   2) 90초 지난 시점에 별도 터미널에서 위 chaos 명령을 실행한다.
//   3) catalog·order 트래픽은 영향받지 않아야 하고(장애 격리 — 기획서 KPI "주문/결제 API
//      성공률 100% 유지"가 바로 이 부분이다), ai 트래픽은 일시적으로 실패했다가 회복
//      시간 이후 정상화되어야 한다(자가 치유).
//
// ⚠️ /api/ai/lion/ask 는 유저별 일일 quota(기본 50회, 초과 시 429 — README §0-9)가
// 있다. aiSteady 는 로그인 토큰 하나를 여러 VU가 공유해 6분 내내 호출하므로, quota를
// 올려두지 않으면 시작 직후 quota가 소진되고 이후는 전부 "장애"가 아니라 "quota
// 초과"로 실패해 이 실험 자체가 성립하지 않는다. **실행 전 반드시**:
//   - EC2(docker-compose): AI_DAILY_QUOTA=100000 docker compose up -d ai
//   - EKS: kubectl set env deployment/ai-rag-deployment -n lion-app AI_DAILY_QUOTA=100000
//   (끝나면 EKS는 `kubectl set env deployment/ai-rag-deployment -n lion-app AI_DAILY_QUOTA-`
//    로 원복 — configmap 기본값 50으로 되돌아간다.)
// 결과 JSON의 ai_quota_exceeded 카운터가 0이 아니면 이 조치를 빠뜨린 것이니 결과를
// 신뢰하지 말 것.
//
// ⚠️ orderSteady는 book_id=SECONDARY_BOOK_ID(기본 101)를 쓴다. OrderService.createOrder는
// 재고 확인(checkStock, 로컬 DB) 뒤에야 catalogClient.getBook()으로 catalog-service를
// 호출한다 — 재고가 바닥나면 catalogClient 호출 전에 즉시 400으로 끝나 catalog 의존
// 경로 자체를 안 타게 된다. 05-payment-concurrency.js가 book_id=1(TEST_BOOK_ID) 재고를
// 소진시키므로, 그것과 자원을 나눠 쓰지 않으려고 101을 기본으로 뒀다. 시드 재고가
// 100개뿐이라 orderSteady도 낮은 페이스로 돈다 — 6분 동안 100개를 넘지 않도록
// vus/sleep을 조절해뒀다. 반복 실행하면 101 재고도 결국 바닥나니, 여러 번 돌리려면
// DB를 재시드하거나 SECONDARY_BOOK_ID를 바꿀 것.
//
// 실행:
//   k6 run -e TARGET_ENV=eks-msa -e RUN_LABEL=ai-pod-kill \
//          -e CATALOG_URL=http://<HOST>:8081 -e MEMBER_URL=http://<HOST>:8083 \
//          -e ORDER_URL=http://<HOST>:8082 -e AI_URL=http://<HOST>:8084 \
//          -e LOGIN_EMAIL=... -e LOGIN_PASSWORD=... \
//          k6/scenarios/03-pod-failure.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { CATALOG_URL, AI_URL, ORDER_URL, MEMBER_URL, SECONDARY_BOOK_ID } from '../lib/config.js';
import { login, authHeaders } from '../lib/auth.js';
import { buildReport } from '../lib/report.js';

const aiQuotaExceeded = new Counter('ai_quota_exceeded');
const aiChaosFailure = new Counter('ai_chaos_failure'); // 5xx/timeout — 이게 진짜 관찰 대상
const orderChaosFailure = new Counter('order_chaos_failure'); // 5xx/timeout만 — 4xx(재고부족 등)는 정상

export const options = {
  scenarios: {
    // 격리 대상 — 장애 중에도 100% 성공해야 한다.
    catalogSteady: {
      executor: 'constant-vus',
      vus: 100,
      duration: '6m',
      exec: 'catalogTraffic',
    },
    // 격리 대상 — 기획서 KPI "주문/결제 API 성공률 100% 유지"를 직접 검증한다.
    // 재고가 100개뿐이라 낮은 페이스로 돈다(위 주석 참고).
    orderSteady: {
      executor: 'constant-vus',
      vus: 2,
      duration: '6m',
      exec: 'orderTraffic',
    },
    // 장애 유발 대상. 계정 하나를 공유하므로(§0-7) 일일 quota 소모를 줄이려고
    // VU 수와 호출 빈도를 낮게 잡는다 — 그래도 quota를 미리 올려두는 건 필수다.
    aiSteady: {
      executor: 'constant-vus',
      vus: 5,
      duration: '6m',
      exec: 'aiTraffic',
    },
  },
  thresholds: {
    'http_req_failed{scenario:catalogSteady}': ['rate<0.001'], // 장애 격리 KPI
    'order_chaos_failure': ['count<1'], // 장애 격리 KPI — 5xx/timeout 0건
    // aiSteady는 의도적으로 threshold를 강하게 걸지 않는다 — 여기서 에러가 나는 게
    // "정상"이다(장애를 유발했으니까). 회복까지 걸린 시간은 JSON summary의 시계열이
    // 아니라 k6 콘솔 로그 타임스탬프 + kubectl/docker 관찰 로그를 나란히 놓고 본다.
  },
};

export function setup() {
  const token = login();
  const cardRes = http.post(
    `${MEMBER_URL}/api/cards`,
    JSON.stringify({ monthlyLimit: 100000000 }),
    { ...authHeaders(token), tags: { name: 'issue-card' } },
  );
  check(cardRes, { 'card issued (201)': (r) => r.status === 201 });
  const cardId = cardRes.json('data.cardId');
  if (!cardId) {
    throw new Error(`카드 발급 실패, 응답 확인 필요: ${cardRes.status} ${cardRes.body}`);
  }
  return { token, cardId };
}

export function catalogTraffic() {
  const res = http.get(`${CATALOG_URL}/api/catalog/books?page=0&size=20`, {
    tags: { name: 'catalog-during-chaos' },
  });
  check(res, { '200': (r) => r.status === 200 });
  sleep(1);
}

export function orderTraffic(data) {
  const body = JSON.stringify({
    items: [{ bookId: SECONDARY_BOOK_ID, quantity: 1 }],
    recipient: {
      name: `k6-chaos-vu-${__VU}`,
      phone: '010-0000-0000',
      postalCode: '06236',
      address: '서울시 강남구 테헤란로 k6 부하테스트',
    },
    paymentMethod: 'VIRTUAL_CARD',
    cardId: data.cardId,
  });

  const res = http.post(`${ORDER_URL}/api/orders`, body, {
    ...authHeaders(data.token),
    tags: { name: 'order-during-chaos' },
  });
  if (res.status >= 500 || res.status === 0) {
    orderChaosFailure.add(1); // 5xx/timeout만 장애로 집계 — 400(재고부족 등)은 정상 응답
  }
  check(res, { 'not 5xx/timeout': (r) => r.status !== 0 && r.status < 500 });
  sleep(8); // 6분 동안 재고 100개를 넘지 않도록 페이스 조절(위 주석 참고)
}

export function aiTraffic(data) {
  const res = http.post(
    `${AI_URL}/api/ai/lion/ask`,
    JSON.stringify({ query: '객체지향 관련 메모 있어?' }),
    { ...authHeaders(data.token), tags: { name: 'ai-during-chaos' } },
  );
  if (res.status === 429) {
    aiQuotaExceeded.add(1); // quota 초과 — 장애와 무관한 잡음, README §0-9 참고
  } else if (res.status >= 500 || res.status === 0) {
    aiChaosFailure.add(1); // 실패 자체가 관찰 대상(장애를 유발했으니까)
  }
  check(res, { 'not quota-exceeded (429)': (r) => r.status !== 429 });
  sleep(3);
}

export function handleSummary(data) {
  return buildReport('03-pod-failure', data, {
    note: 'ai_quota_exceeded가 0이 아니면 AI_DAILY_QUOTA를 안 올리고 돌린 것 — ai_chaos_failure(5xx/timeout)만 pod-kill 결과로 신뢰할 것. order_chaos_failure는 "주문/결제 API 성공률 100% 유지" KPI에 대응한다(0이어야 정상).',
  });
}
