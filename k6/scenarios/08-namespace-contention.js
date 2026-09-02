// 네임스페이스 간 자원 간섭 — integrated 클러스터(dev/prod가 한 EKS에 네임스페이스로만
// 분리, DB도 공유 EC2)에서 한쪽 환경에 부하를 걸 때 다른 쪽 환경의 응답시간이 영향받는지
// 측정한다. split(dev/prod 완전분리 클러스터)이라면 이 영향은 0에 수렴해야 정상이다 —
// integrated에서 유의미한 영향이 관측되면 그게 곧 "분리해야 하는 이유"의 실측 근거가 된다.
//
// 판단 기준: watch_side_latency_ms의 p95를, 같은 WATCH_CATALOG_URL을 heavyLoad 없이
// 단독 실행했을 때(예: 01-traffic-spike.js를 WATCH_CATALOG_URL 대상으로 낮은 VU로 돌린
// 결과, 또는 이 스크립트를 ENABLE_LOAD=false로 한 번 더 돌린 결과)의 p95와 비교할 것.
//
// 방향을 바꿔가며 양쪽 다 확인할 것 — "dev가 prod를 침범하는지"와 "prod가 dev를
// 침범하는지"는 다른 질문이다.
//
// 실행:
//   # dev에 부하, prod 관찰
//   k6 run -e TARGET_ENV=integrated -e RUN_LABEL=dev-loads-watch-prod \
//          -e LOAD_CATALOG_URL=https://dev.ajttk.com \
//          -e WATCH_CATALOG_URL=https://<PROD_DOMAIN> \
//          k6/scenarios/08-namespace-contention.js
//
//   # 반대 방향
//   k6 run -e TARGET_ENV=integrated -e RUN_LABEL=prod-loads-watch-dev \
//          -e LOAD_CATALOG_URL=https://<PROD_DOMAIN> \
//          -e WATCH_CATALOG_URL=https://dev.ajttk.com \
//          k6/scenarios/08-namespace-contention.js
//
//   # 베이스라인(간섭 없는 상태) 확인용 — watch만 단독 실행
//   k6 run -e TARGET_ENV=integrated -e RUN_LABEL=watch-baseline -e ENABLE_LOAD=false \
//          -e WATCH_CATALOG_URL=https://<PROD_DOMAIN> \
//          k6/scenarios/08-namespace-contention.js
//
// ⚠️ WATCH_* 쪽이 prod라면 실제 서비스 트래픽이 섞여있는 운영 환경이다. watch 쪽
// 부하 자체는 가볍게(기본 5 VUs) 잡아뒀지만, 그래도 반드시 팀에 사전 공지하고 돌릴 것.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { TEST_BOOK_CATEGORY } from '../lib/config.js';
import { buildReport } from '../lib/report.js';

const LOAD_CATALOG_URL = __ENV.LOAD_CATALOG_URL;
const WATCH_CATALOG_URL = __ENV.WATCH_CATALOG_URL;
const ENABLE_LOAD = (__ENV.ENABLE_LOAD || 'true') !== 'false';
const LOAD_TARGET_VUS = Number(__ENV.LOAD_TARGET_VUS || 3000);

if (!WATCH_CATALOG_URL) {
  throw new Error('WATCH_CATALOG_URL 은 항상 필요하다 (관찰 대상 환경의 도메인).');
}
if (ENABLE_LOAD && !LOAD_CATALOG_URL) {
  throw new Error('LOAD_CATALOG_URL 이 없다. 베이스라인만 잴 거면 -e ENABLE_LOAD=false 로 끌 것.');
}

const watchLatency = new Trend('watch_side_latency_ms');

const scenarios = {
  // 관찰 대상 — 가벼운 steady 트래픽만 유지하면서 지연시간만 잰다. 항상 돈다.
  watchTraffic: {
    executor: 'constant-vus',
    vus: 5,
    duration: '4m',
    exec: 'watchTrafficFn',
  },
};

if (ENABLE_LOAD) {
  // 부하 유발 대상 — 이쪽 네임스페이스/공유 DB에 과부하를 건다.
  scenarios.heavyLoad = {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '30s', target: 500 },
      { duration: '3m', target: LOAD_TARGET_VUS },
      { duration: '30s', target: 0 },
    ],
    exec: 'loadTraffic',
  };
}

export const options = {
  scenarios,
  thresholds: {
    // "몇 ms면 괜찮다"는 절대값보다, ENABLE_LOAD=false로 잰 베이스라인 대비 얼마나
    // 나빠졌는지가 진짜 판단 기준이다 — 이 threshold는 명백히 나쁜 경우만 거른다.
    watch_side_latency_ms: ['p(95)<1000'],
  },
};

export function loadTraffic() {
  const res = http.get(
    `${LOAD_CATALOG_URL}/api/catalog/books?category=${encodeURIComponent(TEST_BOOK_CATEGORY)}&page=0&size=20`,
    { tags: { name: 'load-side' } },
  );
  check(res, { 'load-side 200': (r) => r.status === 200 });
}

export function watchTrafficFn() {
  const start = Date.now();
  const res = http.get(`${WATCH_CATALOG_URL}/api/catalog/books?page=0&size=20`, {
    tags: { name: 'watch-side' },
  });
  watchLatency.add(Date.now() - start);
  check(res, { 'watch-side 200': (r) => r.status === 200 });
  sleep(2);
}

export function handleSummary(data) {
  return buildReport('08-namespace-contention', data, {
    note:
      'watch_side_latency_ms의 p95를 ENABLE_LOAD=false로 잰 베이스라인과 비교할 것. ' +
      '차이가 크면 integrated 클러스터의 네임스페이스 간 자원 간섭이 실재한다는 뜻 — split이 필요하다는 근거가 된다.',
  });
}
