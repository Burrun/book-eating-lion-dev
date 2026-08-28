// CPU 기반 HPA vs 요청(동시 처리량) 기반 HPA 비교.
//
// ai-rag(k8s/ai/hpa-rag.yaml)는 CPU 사용률로 스케일하고, ai-bot(k8s/ai/hpa-bot.yaml)은
// Pod당 동시 요청 수(http_server_requests_active)로 스케일한다. 둘 다 외부 LLM API
// 응답을 기다리는 순수 I/O 바운드 워크로드라 부하가 몰려도 CPU는 안 오른다 —
// hpa-bot.yaml 주석이 이미 이 실험을 예견해뒀다:
//   "요청 100건 동시 유입 → 스레드 100개가 LLM 응답 대기로 블록 → CPU 5% →
//    CPU 70% HPA는 영원히 트리거되지 않음 → Pod는 그대로, 요청만 큐에 쌓임 → 타임아웃"
// 이 스크립트는 두 워크로드에 동시에 같은 강도로 부하를 걸어서, ai-rag는 Pod가 안
// 늘어나고 ai-bot은 늘어나는 대조를 실측으로 재현한다(hpa-bot.yaml이 "Phase 4의
// 대조 시연 항목"이라고 부르는 바로 그것).
//
// ⚠️ ai-bot의 Pods 메트릭은 Prometheus Adapter 또는 KEDA가 설치돼 있어야 동작한다.
// 먼저 확인할 것:
//   kubectl get hpa ai-bot-hpa -n lion-app
//   → CONDITIONS의 ScalingActive가 True인지 확인. False면 메트릭 파이프라인 자체가
//     없는 것이다 — 그 상태로 돌리면 "요청 기반 HPA도 결국 안 늘어나더라"라는 잘못된
//     결론을 낼 수 있다(원인이 요청 기반 HPA의 한계가 아니라 설치 누락이므로).
//
// ⚠️ ai-rag(/api/ai/lion/ask)는 유저별 일일 quota가 있다(기본 50회, 초과 시 429 —
// README §0-9). 03-pod-failure.js와 동일하게 실행 전 AI_DAILY_QUOTA를 올려둘 것:
//   kubectl set env deployment/ai-rag-deployment -n lion-app AI_DAILY_QUOTA=100000
// ai-bot(/api/ai/bot/ask)은 인증도 quota도 없다(ai-v1.yaml — 로그인 불필요).
//
// k6는 k8s 상태를 못 읽는다. 진짜 증거는 이 스크립트가 도는 동안 별도 터미널에서
// 아래를 지켜보고 기록하는 것 — 그래서 이 스크립트는 "부하를 오래·꾸준히 유지"하는
// 데만 집중한다(응답 정합성은 관심사가 아님):
//   kubectl get hpa -n lion-app -w
//   kubectl get pods -n lion-app -l app=ai-rag -w
//   kubectl get pods -n lion-app -l app=ai-bot -w
//
// 실행:
//   kubectl set env deployment/ai-rag-deployment -n lion-app AI_DAILY_QUOTA=100000
//   k6 run -e TARGET_ENV=integrated -e RUN_LABEL=hpa-metric-compare \
//          -e AI_URL=https://dev.ajttk.com \
//          -e LOGIN_EMAIL=... -e LOGIN_PASSWORD=... \
//          k6/scenarios/09-hpa-metric-comparison.js
//   kubectl set env deployment/ai-rag-deployment -n lion-app AI_DAILY_QUOTA-
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { AI_URL } from '../lib/config.js';
import { login, authHeaders } from '../lib/auth.js';
import { buildReport } from '../lib/report.js';

const ragQuotaExceeded = new Counter('rag_quota_exceeded');
const ragOk = new Counter('rag_ok');
const botOk = new Counter('bot_ok');

const HPA_COMPARE_VUS = Number(__ENV.HPA_COMPARE_VUS || 30);

export const options = {
  scenarios: {
    // CPU 기반 HPA 대상 — 계속 CPU가 안 오르면서 큐만 쌓이는지 관찰.
    ragLoad: {
      executor: 'constant-vus',
      vus: HPA_COMPARE_VUS,
      duration: '5m',
      exec: 'ragTraffic',
    },
    // 요청 기반 HPA 대상 — 같은 강도의 동시 요청.
    botLoad: {
      executor: 'constant-vus',
      vus: HPA_COMPARE_VUS,
      duration: '5m',
      exec: 'botTraffic',
    },
  },
  thresholds: {
    // 강한 threshold를 걸지 않는다 — 이 테스트의 목적은 실패를 막는 게 아니라
    // "CPU가 안 오르는데 Pod가 늘어나는지"를 kubectl로 관찰하는 것이다.
    rag_quota_exceeded: ['count<1'], // 0이 아니면 AI_DAILY_QUOTA를 안 올린 것 — 결과 무효
  },
};

export function setup() {
  const token = login();
  return { token };
}

export function ragTraffic(data) {
  const res = http.post(
    `${AI_URL}/api/ai/lion/ask`,
    JSON.stringify({ query: '객체지향 관련 메모 있어?' }),
    { ...authHeaders(data.token), tags: { name: 'rag-cpu-hpa' } },
  );
  if (res.status === 429) {
    ragQuotaExceeded.add(1);
  } else if (res.status === 200) {
    ragOk.add(1);
  }
  check(res, { 'not quota-exceeded (429)': (r) => r.status !== 429 });
  sleep(0.5);
}

export function botTraffic() {
  const res = http.post(
    `${AI_URL}/api/ai/bot/ask`,
    JSON.stringify({ question: '배송은 얼마나 걸리나요?' }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'bot-request-hpa' } },
  );
  if (res.status === 200) {
    botOk.add(1);
  }
  check(res, { '200': (r) => r.status === 200 });
  sleep(0.5);
}

export function handleSummary(data) {
  return buildReport('09-hpa-metric-comparison', data, {
    note:
      'k6 메트릭만으로는 결론을 못 낸다. 같은 시간대의 kubectl get hpa/pods -w 로그를 나란히 ' +
      '놓고, ai-rag 파드 수는 안 늘어나는데(CPU 안 오름) ai-bot 파드 수는 늘어나는지 확인할 것. ' +
      'rag_quota_exceeded가 0이 아니면 AI_DAILY_QUOTA를 안 올리고 돌린 것이라 결과를 신뢰하지 말 것.',
  });
}
