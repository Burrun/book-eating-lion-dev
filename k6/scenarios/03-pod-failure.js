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
//   3) catalog 트래픽은 영향받지 않아야 하고(장애 격리), ai 트래픽은 일시적으로
//      실패했다가 회복 시간 이후 정상화되어야 한다(자가 치유).
//
// 실행:
//   k6 run -e TARGET_ENV=eks-msa -e RUN_LABEL=ai-pod-kill \
//          -e CATALOG_URL=http://<HOST>:8081 -e MEMBER_URL=http://<HOST>:8083 \
//          -e AI_URL=http://<HOST>:8084 \
//          -e LOGIN_EMAIL=... -e LOGIN_PASSWORD=... \
//          k6/scenarios/03-pod-failure.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { CATALOG_URL, AI_URL } from '../lib/config.js';
import { login, authHeaders } from '../lib/auth.js';
import { buildReport } from '../lib/report.js';

export const options = {
  scenarios: {
    // 격리 대상 — 장애 중에도 100% 성공해야 한다.
    catalogSteady: {
      executor: 'constant-vus',
      vus: 100,
      duration: '6m',
      exec: 'catalogTraffic',
    },
    // 장애 유발 대상.
    aiSteady: {
      executor: 'constant-vus',
      vus: 50,
      duration: '6m',
      exec: 'aiTraffic',
    },
  },
  thresholds: {
    'http_req_failed{scenario:catalogSteady}': ['rate<0.001'], // 장애 격리 KPI
    // aiSteady는 의도적으로 threshold를 강하게 걸지 않는다 — 여기서 에러가 나는 게
    // "정상"이다(장애를 유발했으니까). 회복까지 걸린 시간은 JSON summary의 시계열이
    // 아니라 k6 콘솔 로그 타임스탬프 + kubectl/docker 관찰 로그를 나란히 놓고 본다.
  },
};

export function setup() {
  const token = login();
  return { token };
}

export function catalogTraffic() {
  const res = http.get(`${CATALOG_URL}/api/catalog/books?page=0&size=20`, {
    tags: { name: 'catalog-during-chaos' },
  });
  check(res, { '200': (r) => r.status === 200 });
  sleep(1);
}

export function aiTraffic(data) {
  const res = http.post(
    `${AI_URL}/api/ai/lion/ask`,
    JSON.stringify({ query: '객체지향 관련 메모 있어?' }),
    { ...authHeaders(data.token), tags: { name: 'ai-during-chaos' } },
  );
  check(res, { 'not 5xx or expected during chaos': () => true }); // 실패 자체가 관찰 대상
  sleep(1);
}

export function handleSummary(data) {
  return buildReport('03-pod-failure', data);
}
