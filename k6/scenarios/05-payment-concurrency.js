// 결제 안정성 및 정합성(오버셀링 방지) — 이 프로젝트의 핵심 검증 시나리오.
//
// ⚠️ nginx(./nginx/default.conf)는 정확히 "/api/orders"(트레일링 슬래시 없음, 주문
// 생성 POST가 쓰는 그 경로)를 처리할 location이 없다 — 있는 건 "/api/orders/"(프리픽스)
// 뿐이라 "/api/orders"는 그 프리픽스에 안 걸리고 catch-all(/)로 떨어져 프론트엔드
// 정적 서버가 응답한다. /api/cart, /api/coupons, /api/cards도 nginx에 아예 라우팅이
// 없다. 그래서 이 시나리오는 반드시 ORDER_URL/MEMBER_URL을 nginx(80)가 아니라
// 서비스 포트로 직접 지정해야 한다(docker-compose 기준 order=8082, member=8083).
//
// 시드 데이터(db/postgres/90-demo-data.sql): book_id=1, 재고 정확히 100개, ON_SALE.
// 목표: 동시 주문이 아무리 몰려도 PAID 건수가 정확히 100건을 넘지 않는지 검증.
//
// 카드는 setup()에서 1장만 발급하고 monthlyLimit을 넉넉히 잡는다(기본 100만원으로는
// 25,000원 도서 40건만 지나도 카드 한도 초과(402)가 섞여 "재고 때문에 거절"과
// "카드 한도 때문에 거절"이 뒤섞인다 — 오버셀링 검증 목적에서는 그 잡음을 없애야 한다).
//
// 실행:
//   k6 run -e TARGET_ENV=ec2-single -e RUN_LABEL=baseline \
//          -e ORDER_URL=http://<HOST>:8082 -e MEMBER_URL=http://<HOST>:8083 \
//          -e LOGIN_EMAIL=... -e LOGIN_PASSWORD=... \
//          k6/scenarios/05-payment-concurrency.js
//
// 실행 후 검증(HTTP 응답 카운트만으로는 락 경합 중 이중 차감 여부를 못 잡는다.
// 반드시 DB를 직접 조회할 것):
//   SELECT stock FROM order_db.inventory WHERE book_id = 1;             -- 기대값: 0
//   SELECT count(*) FROM order_db.orders WHERE status = 'PAID';         -- 기대값: 100
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { ORDER_URL, MEMBER_URL, TEST_BOOK_ID } from '../lib/config.js';
import { login, authHeaders } from '../lib/auth.js';
import { buildReport } from '../lib/report.js';

const paidCount = new Counter('order_paid');
const rejectedStockCount = new Counter('order_rejected_out_of_stock');
const rejectedCardCount = new Counter('order_rejected_card_limit');
const unexpectedErrorCount = new Counter('order_unexpected_error');

export const options = {
  scenarios: {
    // 동시에 정확히 VUS명이 1회씩만 주문을 시도한다 — "자정 오픈 순간 몰리는 버스트"를
    // 재현하는 데는 constant-arrival-rate보다 이쪽이 더 정확하다(반복이 아니라 동시성이 목적).
    burst: {
      executor: 'per-vu-iterations',
      vus: 1000,
      iterations: 1,
      maxDuration: '2m',
    },
  },
  thresholds: {
    // 200(성공) + 400(정상 거절: 재고부족)의 합이 전체의 99.9% 이상이어야 한다.
    // 타임아웃/503처럼 "판단 자체를 못 내린" 요청이 0.1% 미만이어야 가용성 KPI를 만족.
    http_req_failed: ['rate<0.001'], // k6 기준 http_req_failed는 네트워크 실패/타임아웃만 잡는다(4xx/402는 포함 안 됨)
  },
};

export function setup() {
  const token = login();
  const cardRes = http.post(
    `${MEMBER_URL}/api/cards`,
    JSON.stringify({ monthlyLimit: 100000000 }), // 100,000,000원 — 25,000원 x 100건 대비 넉넉한 한도
    { ...authHeaders(token), tags: { name: 'issue-card' } },
  );
  check(cardRes, { 'card issued (201)': (r) => r.status === 201 });
  const cardId = cardRes.json('data.cardId');
  if (!cardId) {
    throw new Error(`카드 발급 실패, 응답 확인 필요: ${cardRes.status} ${cardRes.body}`);
  }
  return { token, cardId };
}

export default function (data) {
  const body = JSON.stringify({
    items: [{ bookId: TEST_BOOK_ID, quantity: 1 }],
    recipient: {
      name: `k6-vu-${__VU}`,
      phone: '010-0000-0000',
      postalCode: '06236',
      address: '서울시 강남구 테헤란로 k6 부하테스트',
    },
    paymentMethod: 'VIRTUAL_CARD', // order-v1.yaml CreateOrderRequest 실제 enum. CARD 아님.
    cardId: data.cardId,
  });

  const res = http.post(`${ORDER_URL}/api/orders`, body, {
    ...authHeaders(data.token),
    tags: { name: 'create-order' },
  });

  if (res.status === 200) {
    paidCount.add(1);
  } else if (res.status === 400) {
    rejectedStockCount.add(1); // 재고 부족 등 — 정상적인 거절
  } else if (res.status === 402) {
    rejectedCardCount.add(1); // 카드 한도 — monthlyLimit을 넉넉히 잡았다면 여긴 0에 가까워야 정상
  } else {
    unexpectedErrorCount.add(1);
  }

  check(res, { '200/400/402 중 하나(타임아웃/5xx 아님)': (r) => [200, 400, 402].includes(r.status) });
}

export function handleSummary(data) {
  return buildReport('05-payment-concurrency', data, {
    note: 'PAID 건수는 이 JSON이 아니라 DB(order_db.orders, order_db.inventory) 직접 조회로 확정할 것.',
  });
}
