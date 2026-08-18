// DB 부하 분산 — 캐싱 유무에 따른 Aurora/EC2-Postgres 부하 비교.
//
// 🔜 현재 backend/modules/book, catalog-api 어디에도 @Cacheable 등 캐싱 구현이
// 없다(README §0-2). 캐싱이 배포되기 전에 이 스크립트를 돌리면 "캐시 없음" 구간의
// 베이스라인만 얻는다 — 그것도 유효한 데이터이므로 RUN_LABEL=cache-off로 남겨두고,
// 나중에 캐싱 배포 후 RUN_LABEL=cache-on 으로 동일 스크립트를 재실행해 비교한다.
//
// 같은 쿼리 파라미터를 고정 반복해야 캐시 키가 일정해 Hit이 발생한다.
//
// 실행:
//   k6 run -e TARGET_ENV=ec2-single -e RUN_LABEL=cache-off \
//          -e CATALOG_URL=http://<EC2-IP>:8081 \
//          k6/scenarios/02-cache-offload.js
import http from 'k6/http';
import { check } from 'k6';
import { CATALOG_URL, TEST_BOOK_CATEGORY } from '../lib/config.js';
import { buildReport } from '../lib/report.js';

export const options = {
  scenarios: {
    cacheOffload: {
      executor: 'constant-arrival-rate',
      rate: 200, // 초당 요청 수 — 기획서의 "10,000회 반복 Read"를 50초 안팎으로 재현
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 100,
      maxVUs: 300,
    },
  },
  thresholds: {
    // 캐시 적용 후 목표치. 캐시 미적용 상태에서는 당연히 못 채운다 — 실패해도 정상이며,
    // 그 실패 자체가 "캐시가 필요하다"는 증거 데이터다.
    http_req_duration: ['p(95)<10'],
    http_req_failed: ['rate<0.001'],
  },
};

export default function () {
  const res = http.get(
    `${CATALOG_URL}/api/catalog/books?category=${encodeURIComponent(TEST_BOOK_CATEGORY)}&page=0&size=20`,
    { tags: { name: 'books-list-cacheable' } },
  );
  check(res, { '200': (r) => r.status === 200 });
}

export function handleSummary(data) {
  return buildReport('02-cache-offload', data);
}
