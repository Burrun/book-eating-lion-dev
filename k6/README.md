# k6 부하 테스트

기획서(§5 부하 테스트 및 인프라 검증 계획)를 이 저장소의 **실제 구현**(`backend/contracts/*.yaml`, `k8s/*`, `nginx/*`, `db/postgres/90-demo-data.sql`)과 대조해 작성한 실행 가능한 k6 스크립트 모음이다.

**GitHub Actions에는 포함하지 않는다.** 별도의 테스트용 EC2에서 수동으로 실행하고, 그 결과를 모아 인프라 구성 간 효율을 비교하는 용도다(§7, §8).

---

## 0. 실행 전 반드시 확인할 것 (Blocker)

| # | 문제 | 근거 | 영향 | 조치 |
|---|---|---|---|---|
| 1 | **Catalog 서비스 k8s Ingress 경로 불일치** | `k8s/base/08-ingress.yaml`은 `/api/books`, `/api/reviews`, `/api/wishlist`, `/api/recent-books`로 라우팅하는데, 실제 컨트롤러는 전부 `/api/catalog/*` 하위다. | EKS `API_HOST`로 외부에서 `/api/books`를 호출하면 404. | EKS 대상 테스트 전에 Ingress부터 고칠 것. `lib/config.js`의 `CATALOG_URL`을 서비스 직접 주소로 돌리면 임시 우회는 가능하나 실제 진입 경로 검증은 아니게 됨. |
| 2 | **nginx(docker-compose/EC2)도 라우팅이 불완전** | `nginx/default.conf`에 `/api/cart`, `/api/coupons`, `/api/cards`, `/ws/ai/chat` location이 아예 없다. 게다가 `location /api/orders/`(트레일링 슬래시 프리픽스)는 **정확히 `/api/orders`(슬래시 없음, 주문 생성 POST가 쓰는 경로)와 매칭되지 않는다** — nginx 프리픽스 매칭 규칙상 `/api/orders`는 `/api/orders/`로 시작하지 않으므로 catch-all(`location /`, 프론트엔드)로 떨어진다. | **EC2/docker-compose를 nginx(80번 포트)로 테스트하면 주문 생성(`POST /api/orders`)·장바구니·쿠폰·카드 발급이 전부 조용히 프론트엔드 정적 응답으로 빠진다.** | 이 엔드포인트들은 nginx를 거치지 말고 서비스 포트로 직접 호출한다(`docker-compose.yml` 기준 order=8082, member=8083). 각 스크립트 상단 주석에 명시해뒀다. |
| 3 | **도서 목록/베스트셀러 Redis 캐싱 미구현** | `backend/modules/book`, `catalog-api` 전체에 `@Cacheable`/`RedisTemplate` 사용처 없음(Redis는 이벤트 Stream 용도로만 사용 중). | "DB 부하 분산" 시나리오는 지금은 비교 대상이 없다. | 🔜 스크립트(`02-cache-offload.js`)는 작성해뒀다. 지금 돌리면 베이스라인(캐시 없음)만 나온다 — 그것도 유효한 데이터다. |
| 4 | **상담 채팅 WebSocket 미노출** | `/ws/ai/chat`이 k8s Ingress·nginx 어디에도 없다. 채팅 기능은 Member가 아니라 **AI 서비스(ai-bot)**에 구현돼 있다. | 서비스 포트로 직접 붙어야 한다. | 🔜 스크립트(`06-chat-concurrency.js`) 작성 완료, `WS_URL`을 ai 서비스 포트로 직접 지정해서 실행. |
| 5 | **AI 스와이프 추천 / 정기구독 배너 미구현** | 코드베이스 전체에 없음. | 기획서 사용자 시나리오 일부를 그대로 옮길 수 없음. | ⛔ 로드맵에 없는 기능이라 시나리오 자체를 작성하지 않는다. |
| 6 | **RDS Proxy / WAF / CloudFront / Route 53 / Karpenter 미프로비저닝** | `./terraform`이 비어 있고, `k8s/base/04-db.yaml`도 Aurora 직결(Proxy 미경유)이다. | 해당 인프라 의존 검증은 지금 못 함. | 🔜 RDS Proxy는 `07-connection-saturation.js`로 미리 작성(고갈 재현 절반은 지금도 가능). WAF/CloudFront는 k6로 오리진을 때려서는 검증 자체가 안 되는 엣지 레이어라 런북(`runbooks/waf-cloudfront-verification.md`)으로 뺐다. |
| 7 | **로그인은 로컬에서도 실제 AWS Cognito를 탄다** | `application-local.yml`도 `issuer-uri`가 실제 Cognito를 가리킴. | Cognito User Pool에 테스트 계정이 미리 있어야 하고, VU마다 로그인하면 Cognito rate limit이 병목이 된다. | 모든 인증 필요 스크립트는 `setup()`에서 1회만 로그인해 토큰을 공유한다(`lib/auth.js`). |
| 8 | `paymentMethod` 실제 enum 값 | `order-v1.yaml`의 실제 enum은 `VIRTUAL_CARD` / `KAKAO_PAY` (기획서의 CARD/KAKAOPAY 아님). | 잘못 보내면 전부 400. | 스크립트에 이미 올바른 값으로 반영됨. |

---

## 1. 파일 구조

```
k6/
  README.md                          # 이 문서
  load-test.js                       # 기존 스모크 테스트(헬스체크)
  lib/
    config.js                        # BASE_URL류, 시드 book_id, TARGET_ENV/RUN_LABEL 등 전부 -e로 주입
    auth.js                          # Cognito 로그인 헬퍼 (setup()에서 1회 호출)
    report.js                        # handleSummary 공통 포맷 — 매 실행을 results/*.json으로 남긴다
  scenarios/
    01-traffic-spike.js              # ✅ 지금 실행 가능 — 트래픽 급증(홈/도서목록)
    02-cache-offload.js              # 🔜 캐싱 배포 후 — DB 부하 분산
    03-pod-failure.js                # ✅ 지금 실행 가능 — 장애 복구/격리 (chaos는 runbook 참고)
    04-rolling-deploy.js             # ✅ 지금 실행 가능 — 무중단 배포 (chaos는 runbook 참고)
    05-payment-concurrency.js        # ✅ 지금 실행 가능 — 결제 안정성/오버셀링 방지 (핵심)
    06-chat-concurrency.js           # 🔜 WS 라우팅 필요 — 문의 채팅 동시성
    07-connection-saturation.js      # 🔜(부분 가능) — DB 커넥션 고갈 한계 / RDS Proxy 효과
  runbooks/
    chaos-actions.md                 # 03/04와 짝 — 환경별 장애·배포 트리거 명령어
    waf-cloudfront-verification.md   # k6로 검증 안 되는 엣지 레이어 수동 절차
  tools/
    merge-results.js                 # results/*.json → results/comparison.csv (엑셀용)
  results/
    (실행할 때마다 *.json이 여기 쌓인다 — git에는 커밋하지 않는 걸 권장)
```

## 2. 테스트 대상 엔드포인트 (실제 계약 기준)

| 도메인 | 메서드/경로 | 용도 | 인증 |
|---|---|---|---|
| Catalog | `GET /api/catalog/books?category&page&size` | 도서 목록(홈/카테고리) | X |
| Catalog | `GET /api/catalog/books/{bookId}` | 도서 상세 | X |
| Member | `POST /api/auth/login` | 로그인 (`{email,password}` → JWT) | X |
| Member | `POST /api/cards` | 가상카드 발급 | O |
| Order | `POST /api/orders` | 주문 생성/결제 | O |
| AI | `POST /api/ai/lion/ask` | RAG 질의응답 | O |
| AI | `POST /api/ai/bot/chat/ticket` + `GET/WS /ws/ai/chat` | 상담 채팅 | O |

## 3. 시드 데이터

`db/postgres/90-demo-data.sql`: 도서 `book_id=1`(`클라우드 엔지니어링 교재`, `ON_SALE`)의 재고가 정확히 **100개**다. 결제 동시성/오버셀링 시나리오(`05-payment-concurrency.js`)는 이 값을 그대로 이용한다. 회원 시드는 있지만 Cognito 실계정은 별도로 만들어야 한다(§0-7).

---

## 4. 실행 방법 (테스트 EC2 대상)

k6는 EC2에 직접 설치하거나(`https://k6.io/docs/get-started/installation/`), 다른 머신에서 원격으로 EC2를 향해 쏴도 된다 — 스크립트는 `-e` 인자로 대상만 바꾸면 되므로 어느 쪽이든 동일하다.

**항상 `k6/` 디렉터리 안에서 `k6 run scenarios/...`로 실행할 것.** `lib/report.js`가 결과를 `results/...`라는 상대경로에 쓰는데, 이건 실행 시점의 작업 디렉터리 기준이다 — 저장소 루트에서 `k6 run k6/scenarios/...`로 실행하면 `k6/results/`가 아니라 루트에 엉뚱한 `results/`가 생긴다.

```bash
cd k6

# 예시: EC2에 docker-compose로 올라간 스택을 대상으로 트래픽 급증 시나리오 실행
k6 run \
  -e TARGET_ENV=ec2-single -e RUN_LABEL=baseline \
  -e CATALOG_URL=http://<EC2-IP>:8081 \
  scenarios/01-traffic-spike.js

# 인증이 필요한 시나리오는 Cognito 테스트 계정도 같이 넘긴다
k6 run \
  -e TARGET_ENV=ec2-single -e RUN_LABEL=baseline \
  -e ORDER_URL=http://<EC2-IP>:8082 -e MEMBER_URL=http://<EC2-IP>:8083 \
  -e LOGIN_EMAIL=loadtest@example.com -e LOGIN_PASSWORD='...' \
  scenarios/05-payment-concurrency.js
```

`TARGET_ENV`와 `RUN_LABEL`이 결과 비교의 핵심 축이다:

- `TARGET_ENV` — 어느 인프라를 대상으로 돌렸는지(`ec2-single`, `eks-msa` 등). **"어느 인프라가 효율이 좋은가"를 증명하려면 같은 스크립트를 TARGET_ENV만 바꿔 두 번 실행하고 결과를 비교하면 된다.**
- `RUN_LABEL` — 같은 `TARGET_ENV` 안에서의 구성 비교(`cache-off`/`cache-on`, `rds-proxy-off`/`rds-proxy-on` 등).

03(장애 복구), 04(무중단 배포)는 k6 실행과 별도로 `runbooks/chaos-actions.md`의 명령을 60~90초 뒤에 다른 터미널에서 실행해야 실제 장애/배포가 재현된다.

---

## 5. 결과 취합 및 엑셀 비교

각 스크립트의 `handleSummary()`가 실행할 때마다 `results/<scenario>__<target_env>__<run_label>__<timestamp>.json`을 남긴다(`lib/report.js`). k6는 실행 중 파일에 누적으로 append를 못 하므로, "EC2 vs EKS" 같은 비교는 **여러 번 실행 → 끝난 뒤 한 번에 취합**하는 2단계로 한다.

```bash
# 여러 시나리오 × 여러 인프라를 반복 실행한 뒤 (어느 디렉터리에서 실행해도 무방 — 항상 k6/results 기준)
node k6/tools/merge-results.js
# → k6/results/comparison.csv 생성됨
```

`comparison.csv`는 헤더에 `scenario`, `target_env`, `run_label`, `timestamp`와 각 실행에서 나온 k6 지표(`metric.http_req_duration.p(95)`, `metric.http_req_failed.rate`, `metric.http_reqs.count` 등, 시나리오별 커스텀 카운터 포함)를 열로 펼쳐 담는다. 이 CSV를 엑셀에서 열어:

1. `scenario` + `target_env`로 피벗 테이블을 만들고
2. `metric.http_req_duration.p(95)`, `metric.http_req_failed.rate` 등을 값 영역에 놓으면

시나리오별로 EC2 단일 배포와 EKS MSA 배포의 응답시간·에러율·처리량 차이가 바로 표/그래프로 나온다. 오버셀링 여부(`05` 결과)나 장애 복구 소요시간(`03`)처럼 k6 메트릭에 안 잡히는 값은 DB 조회 결과·runbook 기록 시각을 같은 엑셀 파일의 별도 시트에 수동으로 채워 넣을 것을 권장한다(`05-payment-concurrency.js`, `runbooks/chaos-actions.md` 참고).

---

## 6. 시나리오별 핵심 메모

세부 요청 스키마·부하 프로파일·전제조건은 각 스크립트 파일 상단 주석에 있다(중복 방지를 위해 여기서는 요약만).

- **01-traffic-spike**: catalog HPA 실측치(2→20, `k8s/catalog/hpa.yaml`)에 맞춘 ramping-vus. P95<500ms, 에러율<0.1%.
- **02-cache-offload**: 동일 쿼리 반복. 캐시 미구현 상태에선 베이스라인만 확보(§0-3).
- **03-pod-failure**: catalog(격리 대상)와 ai(장애 유발 대상) 동시 부하. `runbooks/chaos-actions.md`와 짝으로 실행.
- **04-rolling-deploy**: 지속 부하 중 배포 트리거. EC2는 다운타임 발생이 "정상"(그게 증명하려는 것), EKS는 5xx 0건이 목표.
- **05-payment-concurrency**: 재고 100개 도서에 1,000 VU가 동시 1회 주문. **정합성은 k6 결과가 아니라 DB 직접 조회로 확정**(스크립트 주석의 SQL 참고).
- **06-chat-concurrency**: 티켓 발급 → WebSocket → 메시지 유실률/지연 측정. WS 라우팅 선행 필요(§0-4).
- **07-connection-saturation**: ramping-arrival-rate로 부하를 계단식으로 올려 5xx가 튀는 지점을 찾는다. RDS Proxy 적용 전/후 비교용.
