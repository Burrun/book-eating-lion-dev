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
import http from 'k6/http';
import { check, sleep } from 'k6';
import { CATALOG_URL, TEST_BOOK_ID, TEST_BOOK_CATEGORY } from '../lib/config.js';
import { buildReport } from '../lib/report.js';

export const options = {
  scenarios: {
    spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 500 },
        { duration: '1m', target: 5000 }, // 자정 오픈 순간
        { duration: '3m', target: 5000 }, // 스케일아웃/포화 관찰 구간
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
