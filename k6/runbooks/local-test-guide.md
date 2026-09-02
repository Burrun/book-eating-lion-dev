# 로컬에서 부하테스트 실행 가이드

k6를 EC2가 아니라 **본인 컴퓨터에서** 실행해 dev EKS(`dev.ajttk.com`)를 대상으로
테스트하는 절차. 01번(5,000 VU 스파이크)을 제외하면 대부분 시나리오는 로컬 실행이
충분하다 — 이유는 이 문서 맨 아래 "왜 로컬로 충분한가" 참고.

⚠️ **dev는 팀이 공유하는 환경이다.** 무거운 테스트(01/03/07)를 돌리기 전에 팀에
미리 알릴 것 — 다른 팀원의 데모/개발 작업에 지연이나 에러로 영향을 줄 수 있다.

⚠️ **실측 사례(2026-08-28) — 원인 확정, 자세한 내용은 `waf-rate-limit-incident-2026-08-28.md` 참고**:
`02`(200 req/s, 1분) + `01`(최대 5,000 VU, ~4분)을 `dev.ajttk.com`에 연달아 돌렸더니
`http_req_failed`가 각각 47%/100%로 튀었다. **앱/인프라 문제가 아니라 WAF
`rate-limit` 규칙(IP당 5분에 2,000건 초과 시 차단)이 원인이었다** — k6를 한 머신(단일
IP)에서 돌리면 `02`의 200 req/s조차 10초 만에 이 한도를 넘는다. 이 조사 중 NLB가
WAF 없이 공개 노출돼 있다는 별개의 **보안 이슈도 발견**(아직 미해결, 위 문서 참고) —
**이 문제를 감안한 실행 방법이 정해지기 전까지는 01/02를 포함해 dev.ajttk.com에
추가로 부하를 걸지 말 것.**

---

## 0. 설치 (최초 1회)

```bash
sudo dnf install -y k6   # Fedora. 다른 배포판은 https://k6.io/docs/get-started/installation/
k6 version
mkdir -p results         # k6는 이 디렉터리를 스스로 못 만든다 — 없으면 결과 저장이 조용히 실패한다
```

## 1. 사전 준비 체크리스트

| # | 준비물 | 왜 필요한가 | 관련 문서 |
|---|---|---|---|
| 1 | Cognito 테스트 계정 1개(`LOGIN_EMAIL`/`LOGIN_PASSWORD`) | 03/05/06/07이 인증에 씀 | README §0-7 |
| 2 | 03 실행 전 AI_DAILY_QUOTA 상향 | 안 하면 `ai_quota_exceeded`가 결과를 오염시킴 | README §0-9 |
| 3 | 06용 고객 계정 풀 + 상담사(ADMIN) 계정 1개 | "1인 1방 강제" 때문에 계정 하나로는 브로드캐스트 검증이 안 됨 | README §0-10 |
| 4 | `dev.ajttk.com`(또는 실제 `API_HOST`) 접근 가능 여부 확인 | `curl -i https://<API_HOST>/api/catalog/books` 로 200 확인 | — |

## 2. 실행 순서 — 왜 순서가 중요한가

`05`와 `03`은 **재고를 실제로 소진시킨다**(되돌릴 수 없음 — DB를 직접 리셋해야 원복).
그래서 재고와 무관한 시나리오부터 먼저 돌리고, 재고 소모형은 맨 뒤로 미룬다.

| 순서 | 시나리오 | 재고/계정 영향 | 비고 |
|---|---|---|---|
| 1 | `01-traffic-spike` | 없음 | 인증 불필요. 무거움 — 팀에 미리 공지 |
| 2 | `02-cache-offload` | 없음 | 인증 불필요 |
| 3 | `04-rolling-deploy` | 없음 | 별도 터미널에서 배포 트리거 필요(`chaos-actions.md`) |
| 4 | `07-connection-saturation` | 없음(bookId=999999라 재고 안 건드림) | 카드 발급만 필요 |
| 5 | `06-chat-concurrency` | 없음(채팅 계정 풀 별도) | 사전 준비 #3 필요 |
| 6 | `05-payment-concurrency` | **book_id=1 재고 100→0 소진** | 재실행하려면 DB 리셋 필요(§4) |
| 7 | `03-pod-failure` | **book_id=101 재고 최대 100 소진** + AI quota | 사전 준비 #2 필요, 별도 터미널에서 pod-kill 트리거(`chaos-actions.md`) |

```bash
cd k6

# 1
k6 run -e TARGET_ENV=eks-msa -e RUN_LABEL=baseline \
  -e CATALOG_URL=https://<API_HOST> \
  scenarios/01-traffic-spike.js

# 2
k6 run -e TARGET_ENV=eks-msa -e RUN_LABEL=cache-off \
  -e CATALOG_URL=https://<API_HOST> \
  scenarios/02-cache-offload.js

# 3 (30~60초 뒤 다른 터미널에서 chaos-actions.md의 배포 명령 실행)
k6 run -e TARGET_ENV=eks-msa -e RUN_LABEL=rolling-deploy \
  -e CATALOG_URL=https://<API_HOST> \
  scenarios/04-rolling-deploy.js

# 4
k6 run -e TARGET_ENV=eks-msa -e RUN_LABEL=no-proxy \
  -e ORDER_URL=https://<API_HOST> -e MEMBER_URL=https://<API_HOST> \
  -e LOGIN_EMAIL=... -e LOGIN_PASSWORD=... \
  scenarios/07-connection-saturation.js

# 5
k6 run -e TARGET_ENV=eks-msa -e RUN_LABEL=baseline \
  -e AI_URL=https://<API_HOST> -e WS_URL=wss://<API_HOST> \
  -e CHAT_CUSTOMER_EMAILS=c1@x.com,c2@x.com,c3@x.com,c4@x.com,c5@x.com \
  -e CHAT_CUSTOMER_PASSWORDS=pw1,pw2,pw3,pw4,pw5 \
  -e CHAT_AGENT_EMAIL=agent@x.com -e CHAT_AGENT_PASSWORD=pw \
  scenarios/06-chat-concurrency.js

# 6 — book_id=1 재고를 실제로 소진시킨다(마지막에 가깝게 돌릴 것)
k6 run -e TARGET_ENV=eks-msa -e RUN_LABEL=baseline \
  -e ORDER_URL=https://<API_HOST> -e MEMBER_URL=https://<API_HOST> \
  -e LOGIN_EMAIL=... -e LOGIN_PASSWORD=... \
  scenarios/05-payment-concurrency.js
# 실행 후 DB 직접 조회로 확정 (스크립트 상단 주석의 SQL 참고)

# 7 — AI_DAILY_QUOTA 먼저 올리고, 90초 뒤 다른 터미널에서 pod-kill 트리거
kubectl set env deployment/ai-rag-deployment -n lion-app AI_DAILY_QUOTA=100000
k6 run -e TARGET_ENV=eks-msa -e RUN_LABEL=ai-pod-kill \
  -e CATALOG_URL=https://<API_HOST> -e MEMBER_URL=https://<API_HOST> \
  -e ORDER_URL=https://<API_HOST> -e AI_URL=https://<API_HOST> \
  -e LOGIN_EMAIL=... -e LOGIN_PASSWORD=... \
  scenarios/03-pod-failure.js
kubectl set env deployment/ai-rag-deployment -n lion-app AI_DAILY_QUOTA-
```

## 3. 결과 취합

```bash
node k6/tools/merge-results.js   # → k6/results/comparison.csv
```

Excel/피벗 테이블 활용법은 `capacity-and-cost-guide.md` §4 참고.

## 4. 재고/상태 리셋 (반복 실행할 때)

dev는 Aurora가 아니라 **EC2 PostgreSQL**을 쓴다(`terraform/인프라구성명세.md` §2.2).
접속 방법은 팀 인프라 문서(bastion/SSM 등) 참고 — 접속 후:

```sql
-- 05/03을 다시 돌리기 전에 재고를 원복
UPDATE order_db.inventory SET stock = 100 WHERE book_id IN (1, 101, 102);
```

06(채팅)은 별도 리셋이 필요 없다 — 방 상태는 TTL로 자동 정리된다.

## 5. 왜 로컬로 충분한가

`03/04/05/06/07`은 VU 수가 수십~1,000대라 순수 처리량보다 정합성/장애복구/브로드캐스트
검증이 목적이라 가정용 회선으로도 결과가 왜곡될 가능성이 낮다. `01`(5,000 VU 지속
부하)만 본인 네트워크 대역폭이 병목이 될 수 있어 주의가 필요하다 — 그 값도 확실히
하려면 `runbooks/ec2-loadtest-setup.md`로 같은 리전 EC2를 그때만 띄워서 돌리는 걸
권장한다.
