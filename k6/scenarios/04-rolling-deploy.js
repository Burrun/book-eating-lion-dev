// 무중단 배포 검증.
//
// 이 스크립트도 부하만 만든다. 배포 트리거는 밖에서:
//   - EC2(docker-compose): docker compose build catalog && docker compose up -d --no-deps catalog
//     (컨테이너를 멈췄다 새로 띄우는 방식이라 그 사이 접속이 끊긴다 — "다운타임 0초"가
//      깨지는 걸 보여주는 것 자체가 EC2 단독 배포의 한계를 증명하는 데이터가 된다)
//   - EKS: main-cd.yml workflow_dispatch 수동 실행, 또는 kubectl rollout restart deployment/catalog-deployment
//     (maxSurge:1/maxUnavailable:0 이므로 이론상 다운타임 0초 — 이걸 숫자로 증명하는 게 이 스크립트다)
//
// 절차: 이 스크립트를 먼저 시작하고, 30~60초 뒤 배포를 트리거한다.
//
// 실행:
//   k6 run -e TARGET_ENV=eks-msa -e RUN_LABEL=rolling-deploy \
//          -e CATALOG_URL=http://<HOST>:8081 \
//          k6/scenarios/04-rolling-deploy.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { CATALOG_URL } from '../lib/config.js';
import { buildReport } from '../lib/report.js';

const errorsByStatus = new Counter('deploy_errors_by_status');

export const options = {
  scenarios: {
    steadyLoad: {
      executor: 'constant-vus',
      vus: 200,
      duration: '5m',
    },
  },
  thresholds: {
    // 목표: 배포 중에도 5xx 0건. EC2에서는 이 threshold가 실패하는 게 정상이며,
    // 그 실패 자체가 "왜 EKS가 필요한가"의 증거 데이터다.
    http_req_failed: ['rate<0.0001'],
  },
};

export default function () {
  const res = http.get(`${CATALOG_URL}/api/catalog/books?page=0&size=20`, {
    tags: { name: 'catalog-during-deploy' },
  });
  const ok = check(res, { 'not 5xx': (r) => r.status < 500 });
  if (!ok) {
    errorsByStatus.add(1, { status: String(res.status) });
  }
  sleep(0.5);
}

export function handleSummary(data) {
  return buildReport('04-rolling-deploy', data);
}
