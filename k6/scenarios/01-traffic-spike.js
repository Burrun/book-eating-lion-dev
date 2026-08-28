// 트래픽 급증 대응 — 자정 오픈 순간의 홈/도서목록 조회 폭주 재현.
// 대상: GET /api/catalog/books, GET /api/catalog/books/{bookId} (인증 불필요)
//
// 실행:
//   k6 run -e TARGET_ENV=ec2-single -e RUN_LABEL=baseline \
//          -e CATALOG_URL=http://<EC2-IP>:8081 \
//          k6/scenarios/01-traffic-spike.js
//
// EKS로 넘어갈 때는 -e CATALOG_URL=http://<API_HOST> 만 바꾸면 된다(단, k8s Ingress
// 경로 불일치가 먼저 고쳐져 있어야 한다 — README §0-1).
//
// "몇 VU부터 터지는지" 찾으려면 SPIKE_TARGET_VUS를 바꿔가며 여러 번 실행하고
// RUN_LABEL에 그 값을 반영할 것(예: RUN_LABEL=spike-8000) — runbooks/capacity-and-cost-guide.md 참고.
//   k6 run -e SPIKE_TARGET_VUS=8000 -e RUN_LABEL=spike-8000 ...
import http from 'k6/http';
import { check, sleep } from 'k6';
import { CATALOG_URL, TEST_BOOK_ID, TEST_BOOK_CATEGORY } from '../lib/config.js';
import { buildReport } from '../lib/report.js';

const SPIKE_TARGET_VUS = Number(__ENV.SPIKE_TARGET_VUS || 5000);

export const options = {
  scenarios: {
    spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        // 기획서 요구치는 "1초 만에 5,000 VUs" — 원래 30s→500 → 1m→5000 처럼 90초에
        // 걸쳐 서서히 램프업하던 걸 없앴다. k6도 물리적으로 완벽한 1초 동시성을
        // 보장하진 못하지만(VU 초기화·TCP 연결 자체에 시간이 든다), 실행기가 낼 수
        // 있는 최대 속도로 즉시 목표치까지 밀어붙이는 쪽이 "자정 오픈 순간 폭주"에
        // 훨씬 가깝다. k6 실행 머신의 CPU/네트워크가 SPIKE_TARGET_VUS를 감당할 수 있어야 한다.
        { duration: '1s', target: SPIKE_TARGET_VUS },
        { duration: '3m', target: SPIKE_TARGET_VUS }, // 스케일아웃/포화 관찰 구간
        { duration: '1m', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.001'],
  },
};

export default function () {
  const listRes = http.get(
    `${CATALOG_URL}/api/catalog/books?category=${encodeURIComponent(TEST_BOOK_CATEGORY)}&page=0&size=20`,
    { tags: { name: 'books-list' } },
  );
  check(listRes, { 'list 200': (r) => r.status === 200 });

  const detailRes = http.get(`${CATALOG_URL}/api/catalog/books/${TEST_BOOK_ID}`, {
    tags: { name: 'book-detail' },
  });
  check(detailRes, { 'detail 200': (r) => r.status === 200 });

  sleep(1);
}

export function handleSummary(data) {
  return buildReport('01-traffic-spike', data);
}
