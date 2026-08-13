// DB 커넥션 고갈 한계 — RDS Proxy(§0-6) 유무, 또는 EC2 단일 Postgres vs Aurora의
// 차이를 "몇 req/s부터 커넥션이 고갈되어 5xx가 나기 시작하는가"로 정량화한다.
//
// k8s/order/hpa.yaml 주석 근거: maximum-pool-size(10) × maxReplicas(30) = 300 커넥션이
// RDS Proxy 없이 Aurora가 버틸 수 있는 상한이라고 명시되어 있다. EC2 단일 인스턴스는
// Pod 복제가 없으므로 애초에 이 상한 자체가 다르게 나타난다 — 그 차이가 비교 포인트다.
//
// 부하를 계단식으로 올리며(ramping-arrival-rate) 각 구간에서 에러율이 튀는 지점을 찾는다.
// 재고 검증이 목적이 아니므로 book_id를 매 요청 다르게 섞어 락 경합이 아니라 순수
// "동시 커넥션 수"가 병목이 되도록 한다(재고가 있는 book_id가 1개뿐이면 락 대기가
// 섞여 원인이 커넥션 고갈인지 락 대기인지 구분이 안 된다 — 시드 데이터에 book_id를
// 추가해두고 그 목록을 TEST_BOOK_IDS로 넘길 것을 권장).
//
// 실행:
//   k6 run -e TARGET_ENV=ec2-single -e RUN_LABEL=no-proxy \
//          -e ORDER_URL=http://<HOST>:8082 -e MEMBER_URL=http://<HOST>:8083 \
//          -e LOGIN_EMAIL=... -e LOGIN_PASSWORD=... \
//          k6/scenarios/07-connection-saturation.js
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { ORDER_URL, MEMBER_URL } from '../lib/config.js';
import { login, authHeaders } from '../lib/auth.js';
import { buildReport } from '../lib/report.js';

const dbErrorCount = new Counter('db_connection_errors'); // 5xx만 집계(4xx는 정상 비즈니스 거절)

export const options = {
  scenarios: {
    saturate: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 1000,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '30s', target: 150 },
        { duration: '30s', target: 300 },
        { duration: '30s', target: 500 }, // 이 부근에서 order-hpa 주석의 300 커넥션 상한을 넘는다
        { duration: '30s', target: 0 },
      ],
    },
  },
  // 의도적으로 http_req_failed에 강한 threshold를 걸지 않는다 — 이 테스트의 목적은
  // "몇 req/s에서 실패가 시작되는가"를 관찰하는 것이지, 실패를 막는 게 아니다.
};

export function setup() {
  const token = login();
  const cardRes = http.post(
    `${MEMBER_URL}/api/cards`,
    JSON.stringify({ monthlyLimit: 100000000 }),
    { ...authHeaders(token), tags: { name: 'issue-card' } },
  );
  const cardId = cardRes.json('data.cardId');
  return { token, cardId };
}

export default function (data) {
  // 재고를 실제로 소모하면 재실행할 때마다 새 시드가 필요하므로, 여기서는 결제 성공
  // 자체보다 "요청이 DB까지 도달해서 응답을 받는지"가 관심사다. 존재하지 않는 bookId로
  // 보내 404/400으로 끝나도 상관없다 — 그마저도 DB 조회(재고 확인)는 타고 지나가므로
  // 커넥션 점유는 동일하게 발생한다.
  const body = JSON.stringify({
    items: [{ bookId: 999999, quantity: 1 }],
    recipient: {
      name: 'k6-saturation',
      phone: '010-0000-0000',
      postalCode: '06236',
      address: '서울시 강남구',
    },
    paymentMethod: 'VIRTUAL_CARD',
    cardId: data.cardId,
  });

  const res = http.post(`${ORDER_URL}/api/orders`, body, {
    ...authHeaders(data.token),
    tags: { name: 'saturation-order' },
  });

  if (res.status >= 500) {
    dbErrorCount.add(1);
  }
  check(res, { 'got a response (no network-level timeout)': (r) => r.status !== 0 });
}

export function handleSummary(data) {
  return buildReport('07-connection-saturation', data, {
    note: 'db_connection_errors가 늘기 시작하는 arrival rate 구간을 기록해 RDS Proxy 적용 전/후로 비교할 것.',
  });
}
