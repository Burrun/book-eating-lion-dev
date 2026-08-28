# k6 부하 테스트

기획서(§5 부하 테스트 및 인프라 검증 계획)를 이 저장소의 **실제 구현**(`backend/contracts/*.yaml`, `k8s/*`, `nginx/*`, `db/postgres/90-demo-data.sql`)과 대조해 작성한 실행 가능한 k6 스크립트 모음이다.

**GitHub Actions에는 포함하지 않는다.** 별도의 테스트용 EC2에서 수동으로 실행하고, 그 결과를 모아 인프라 구성 간 효율을 비교하는 용도다(§7, §8).

---

## 0. 실행 전 반드시 확인할 것 (Blocker)

| # | 문제 | 근거 | 영향 | 조치 |
|---|---|---|---|---|
| 1 | ~~Catalog 서비스 k8s Ingress 경로 불일치~~ — **✅ 해결됨(2026-08-18, `f332c36`)** | `k8s/base/08-ingress.yaml`이 이제 `/api/catalog` prefix 하나로 라우팅한다. 예전에 있던 `/api/books`/`/api/reviews`/`/api/wishlist`/`/api/recent-books` 개별 path는 실제 컨트롤러(`/api/catalog/**`)와 안 맞는 죽은 규칙이라 제거됐다. | 없음 — EKS `API_HOST`로 바로 테스트 가능. | 추가 조치 불필요. |
| 2 | ~~nginx(docker-compose/EC2) 라우팅이 불완전~~ — **✅ 해결됨(2026-08-18, `f332c36`)** | `nginx/default.conf`에 `/api/cart`, `/api/coupons`, `/api/cards`, `/ws/ai/chat` location이 전부 추가됐고, `location /api/orders`도 트레일링 슬래시 없이 정확히 매칭된다. | 없음 — nginx(80번 포트)로도 주문 생성/장바구니/쿠폰/카드 발급이 정상 도달한다. | 각 스크립트 상단의 "서비스 포트로 직접 호출" 주석은 이제 필수 우회가 아니라 선택(nginx 오버헤드를 배제하고 싶을 때만) 사항이다. |
| 3 | **도서 목록 Redis 캐싱 미구현** (베스트셀러는 2026-08-18에 구현됨) | `BookService.getBooks()`(카테고리/목록 조회 — `01`/`02`가 때리는 그 엔드포인트)는 여전히 캐싱이 없다. 다만 `getBestsellers()`에는 `@Cacheable(cacheNames = "bestsellers")`가 붙었다(`BookService.java:56`). | "목록 조회 DB 부하 분산" 시나리오는 여전히 비교 대상이 없음 — `02-cache-offload.js`는 그대로 베이스라인(캐시 없음)만 남긴다. | 🔜 캐시 on/off 비교를 지금 당장 하고 싶다면 `/api/catalog/books/bestsellers`를 때리는 스크립트를 별도로 추가할 것. `02-cache-offload.js` 자체는 그대로 유효. |
| 4 | ~~상담 채팅 WebSocket 미노출~~ — **✅ 해결됨(2026-08-18 nginx/Ingress, PR #84 CloudFront)** | `/ws/ai/chat`이 nginx·k8s Ingress 양쪽에 라우팅됐고, CloudFront 라우팅 누락도 PR #84로 고쳐졌다. | 없음. | `WS_URL`을 ai 서비스 포트로 직접 지정하는 기존 방식도 여전히 유효하다(선택 사항이 됨). |
| 5 | **AI 스와이프 추천 / 정기구독 배너** — ⚠ "코드베이스에 없음"은 낡은 정보(2026-08-18 병합) | `RecommendationController`(`/api/catalog/recommend/queue`, `/reaction`)와 `SubscriptionBannerController`(`/api/catalog/subscription-banners`)가 백엔드·프론트(`frontend/src/api/recommendations.ts`, `subscriptionBanners.ts`) 모두에 구현돼 있다. | "미구현이라 시나리오를 못 만듦"이 아니라 이제 순수한 **커버리지 갭**(k6 시나리오가 없을 뿐)이다. | 🔜 우선순위 낮음(핵심 KPI 아님)이라 지금 당장 작성하진 않는다. 필요해지면 `05`/`06`처럼 `setup()` 1회 로그인 공유 패턴으로 추가. |
| 6 | **RDS Proxy / WAF / CloudFront / Route 53 / Karpenter** — ⚠ "`./terraform`이 비어 있음"은 낡은 정보 | `terraform/`에 `modules/data/rds_proxy`, `modules/compute/karpenter`, `modules/base/dns_zone`(Route53), `modules/compute/edge_routing`(CloudFront/WAF)가 이미 있고 `environments/{dev,prod}`도 구성돼 있다. 최근 커밋들(helm_release destroy 순서, Cognito IAM 수정 등)도 dev 환경이 실제 운영 중임을 시사한다. | 코드 존재 ≠ 실제 apply 상태 확인 완료. | `07-connection-saturation.js`(RDS Proxy 전/후 비교)를 돌리기 전에 대상 환경에 RDS Proxy가 실제로 붙어 있는지 `terraform output`/AWS 콘솔로 먼저 확인할 것. WAF/CloudFront는 여전히 k6로 오리진을 때려선 검증 안 되는 엣지 레이어라 런북(`runbooks/waf-cloudfront-verification.md`)으로 뺀 것 그대로 유효. |
| 7 | **로그인은 로컬에서도 실제 AWS Cognito를 탄다** | `application-local.yml`도 `issuer-uri`가 실제 Cognito를 가리킴. | Cognito User Pool에 테스트 계정이 미리 있어야 하고, VU마다 로그인하면 Cognito rate limit이 병목이 된다. | 모든 인증 필요 스크립트는 `setup()`에서 1회만 로그인해 토큰을 공유한다(`lib/auth.js`). |
| 8 | `paymentMethod` 실제 enum 값 | `order-v1.yaml`의 실제 enum은 `VIRTUAL_CARD` / `KAKAO_PAY` (기획서의 CARD/KAKAOPAY 아님). | 잘못 보내면 전부 400. | 스크립트에 이미 올바른 값으로 반영됨. |
| 9 | **`03-pod-failure.js`의 AI 부하가 일일 quota에 막힘** (신규 발견) | `ai-api`의 `AI_DAILY_QUOTA` 기본값이 **유저당 하루 50회**(`docker-compose.yml:199`, `k8s/ai/configmap.yaml:51` — 초과 시 429). `aiSteady` 시나리오는 로그인 토큰 하나를 여러 VU가 공유해 6분 내내 호출하므로, quota가 그대로면 시작 직후 소진되고 이후는 전부 "장애"가 아니라 "quota 초과"로 실패해 chaos 실험 자체가 성립하지 않는다. | 그대로 돌리면 pod-kill 회복 시간이 아니라 quota 초과율을 측정하게 됨. | 스크립트에 `ai_quota_exceeded` 카운터를 추가해 429를 실제 장애(5xx/timeout)와 분리 집계하도록 수정함. **실행 전 필수**: EC2는 `AI_DAILY_QUOTA=100000 docker compose up -d ai`, EKS는 `kubectl set env deployment/ai-rag-deployment -n lion-app AI_DAILY_QUOTA=100000`로 테스트 계정 quota를 올려둘 것(끝나면 `AI_DAILY_QUOTA-`로 원복). 결과 JSON의 `ai_quota_exceeded`가 0이 아니면 이 조치를 빠뜨린 것이니 결과를 신뢰하지 말 것. |
| 10 | **`06-chat-concurrency.js`가 "1인 1방 강제" 때문에 계정 풀이 필요함** (신규 발견) | `ChatRoomStore.openOrResume()`이 memberId당 방 1개를 강제한다(재접속하면 같은 방을 돌려줌). 05/07처럼 로그인 토큰 하나를 여러 VU가 공유하면 VU가 몇 개든 전부 같은 방 하나로 몰려 "서로 다른 세션 간 브로드캐스트"를 애초에 검증할 수 없다. 또한 메시지 프레임(`ChatMessage`)엔 `roomId`가 없어 상담사가 방을 2개 이상 동시에 맡으면 어느 방 메시지인지 구분이 안 된다(실제 프로토콜 제약). | 기존 스크립트는 각 VU가 자기 혼자만 있는 방에서 자기 메시지의 응답만 확인해 실제로는 "브로드캐스트"를 검증하지 못하고 있었다(이전 리뷰에서 지적한 사항). | `06-chat-concurrency.js`를 고객(ESCALATE)↔상담사(CLAIM→SAY) 흐름으로 재작성함 — 서로 다른 세션 두 개가 실제로 메시지를 주고받는다. **실행 전 필수**: 서로 다른 회원 계정 여러 개(`CHAT_CUSTOMER_EMAILS`/`CHAT_CUSTOMER_PASSWORDS`, 콤마 구분, 개수 일치)와 Cognito `ADMIN` 그룹에 속한 상담사 계정 1개(`CHAT_AGENT_EMAIL`/`CHAT_AGENT_PASSWORD`)를 미리 만들어둘 것. 상담사 동시 처리량은 `CHAT_AGENT_VUS`(기본 1)로 조절 — 고객 동시 접속(기본 10명)보다 너무 적으면 `chat_no_agent`가 늘어난다(유실이 아니라 상담사 용량 부족 신호). **`CHAT_AGENT_EMAIL`이 진짜 ADMIN으로 인식되는지 자체는 06번이 검증 안 한다 — `runbooks/cognito-admin-verification.md`로 별도 확인할 것(2026-08-28 PASS 확인됨).** |
| 11 | `k6/experimental/websockets`는 deprecated (신규 발견) | k6 공식 문서상 stable 모듈은 `k6/websockets`(`import { WebSocket } from 'k6/websockets'`)로 교체됐고, 타이머도 `socket.setTimeout()`이 아니라 전역 `setTimeout`/`clearTimeout`을 쓴다. | 오래된 문서/예제를 따라 `k6/experimental/websockets`로 작성하면 최신 k6에서 deprecation 경고가 뜨거나(버전에 따라) 동작이 달라질 수 있음. | `06-chat-concurrency.js`를 `k6/websockets`로 교체함. 최신 k6 바이너리를 쓸 것. |
| 12 | **⛔ WAF `rate-limit`이 단일 IP에서 돌리는 부하테스트를 전부 막음** (2026-08-28 실측, 미해결) | `terraform/modules/base/waf`의 `rate-limit` 규칙 — IP당 5분에 2,000건 초과 시 차단. `02`의 200 req/s만 해도 10초 만에 초과. k6를 로컬/EC2 한 대에서 돌리는 이상 모든 시나리오가 이 벽에 먼저 부딪힌다. | 01/02/03/05/07/08/09 전부 진짜 부하테스트 결과를 못 얻는다(막힌 요청만 관측됨 — 앱 자체는 정상으로 보임). 자세한 내용·타임라인은 `runbooks/waf-rate-limit-incident-2026-08-28.md`. | 🔜 **미결정.** 후보: ①k6를 여러 소스 IP로 분산 실행 ②알려진 테스트 IP 하나만 rate-limit에서 예외(`scope_down_statement`, 초안 작성 후 보류). **NLB를 직접 때려서 우회하는 방법은 기각** — WAF 전체(OWASP 방어 포함)가 우회되고 그 경로가 애초에 공개 노출돼 있다는 별개의 보안 이슈로 이어짐(§13). 결정 전까지 dev.ajttk.com에 추가 부하 금지. |
| 13 | **⛔ NLB가 WAF 없이 공개 인터넷에 노출됨** (2026-08-28 발견, 미해결, 보안 이슈) | `terraform/modules/compute/ingress_alb`가 NLB를 `internet-facing`으로 설정(CloudFront 오리진 연결에 필요해서 의도적으로 한 것). 그런데 그 뒤 "CloudFront 이외의 소스는 막기"가 안 돼 있음 — WAF는 CloudFront에만 붙어있고 NLB 자체엔 보호 장치가 없다. DNS 이름과 `Host` 헤더만 알면 누구나 WAF(rate-limit + OWASP 방어 전부)를 완전히 우회해 오리진에 직접 접근 가능함을 실측으로 확인(NLB로 직접 50개 동시 요청, 전부 200). | §12과 무관하게 그 자체로 심각한 보안 취약점 — 실제 공격자도 이 경로로 WAF를 통째로 우회할 수 있음. | 부하테스트보다 우선순위 높음, **팀에 바로 공유 필요**. 정식 해결책 두 가지(택1): ① NLB에 보안그룹 붙여서 CloudFront 관리형 prefix list(`com.amazonaws.global.cloudfront.origin-facing`)만 허용 ② CloudFront→오리진에 비밀 헤더를 심고 ingress-nginx가 없으면 거절. **부하테스트 편의를 위해 이 노출을 남겨두는 방안은 검토 후 기각함.** |
| 14 | **⛔ `k6/websockets`(stable)는 `sleep()` 도중 WS 이벤트 콜백을 안 돌림 — 06번이 실제로는 한 번도 통신을 못 하고 있었음** (2026-08-28 실측, 수정함) | `06-chat-concurrency.js`/`tools/verify-agent-connect.js`를 dev에서 실행하면 소켓 연결(`ws_sessions`)은 되는데 `message` 이벤트가 단 한 번도 안 불림. public echo 서버로 격리 재현: `sleep(5)` 동안 `open`/`message` 콜백이 전혀 안 불리다가, `sleep()`을 없애고 핸들러만 걸고 함수를 바로 리턴하니(`setTimeout`으로 종료 예약) 정상 동작함. **k6 공식 예제도 이 패턴(핸들러 등록 후 바로 리턴, `sleep` 미사용)을 쓴다** — `k6/experimental/websockets`(deprecated)의 로컬 이벤트 루프와 달리 stable 모듈은 전역 이벤트 루프라 `sleep()`이 막아버리는 것으로 보인다. | §11에서 `k6/experimental/websockets` → `k6/websockets`로 모듈만 바꾸고 `sleep()` 기반 구조는 그대로 둬서, 06번은 애초에 한 번도 제대로 동작한 적이 없었다(WAF 문제와 별개로 이것부터 막혀있었음). | `06-chat-concurrency.js`, `tools/verify-agent-connect.js` 둘 다 `sleep()`을 전부 제거하고 `setTimeout` 기반으로 재작성함. **WebSocket을 쓰는 스크립트를 새로 추가하거나 고칠 땐 반드시 `sleep()` 없이 짤 것** — 이 세션에서 실제로 겪은 문제라 다음에도 똑같이 재발할 수 있다. |

---

## 1. 파일 구조

```
k6/
  README.md                          # 이 문서
  lib/
    config.js                        # BASE_URL류, 시드 book_id, TARGET_ENV/RUN_LABEL 등 전부 -e로 주입
    auth.js                          # Cognito 로그인 헬퍼 (setup()에서 1회 호출)
    report.js                        # handleSummary 공통 포맷 — 매 실행을 results/*.json으로 남긴다
  scenarios/
    # ⚠️ 아래 "실행 가능" 표시는 전부 dev.ajttk.com의 WAF rate-limit 이슈(§0-12) 해결 전
    # 기준이다 — 지금 그대로 돌리면 전부 WAF에 막혀서 결과를 못 얻는다. §0-12/§0-13 확인할 것.
    01-traffic-spike.js              # ✅ 지금 실행 가능 — 트래픽 급증(홈/도서목록)
    02-cache-offload.js              # ✅ 실행 가능(베이스라인만) — DB 부하 분산, 목록 캐싱 배포 전
    03-pod-failure.js                # ✅ 실행 가능(quota 상향 필수, §0-9) — 장애 복구/격리 (chaos는 runbook 참고)
    04-rolling-deploy.js             # ✅ 지금 실행 가능 — 무중단 배포 (chaos는 runbook 참고)
    05-payment-concurrency.js        # ✅ 지금 실행 가능 — 결제 안정성/오버셀링 방지 (핵심)
    06-chat-concurrency.js           # ✅ 지금 실행 가능 — 문의 채팅 동시성 (WS 라우팅 해결됨)
    07-connection-saturation.js      # ✅ 실행 가능 — DB 커넥션 고갈 한계 / RDS Proxy 효과(적용 여부는 §0-6 확인)
    08-namespace-contention.js       # ✅ integrated 클러스터 전용 — 네임스페이스 간(dev↔prod) 자원 간섭
    09-hpa-metric-comparison.js      # ✅ 실행 가능(quota 상향 필수) — CPU 기반 vs 요청 기반 HPA 대조
  runbooks/
    chaos-actions.md                 # 03/04와 짝 — 환경별 장애·배포 트리거 명령어
    waf-cloudfront-verification.md   # k6로 검증 안 되는 엣지 레이어 수동 절차
    ec2-loadtest-setup.md            # k6 부하생성기 EC2 만들기/지우기(CLI, 공유 계정 태그 포함)
    waf-rate-limit-incident-2026-08-28.md  # ⚠️ 실측 사건 기록 — WAF rate-limit이 부하테스트를 막음 + NLB 노출 보안 이슈 발견(둘 다 미해결)
    local-test-guide.md              # 로컬(본인 PC)에서 dev EKS 대상으로 전체 시나리오 실행하는 순서
    cognito-admin-verification.md    # Cognito ADMIN 그룹 상승 → AI 모듈(상담사) 인식 검증 절차(2026-08-28 PASS 확인됨)
    capacity-and-cost-guide.md       # 한계점 탐색 + 증량 옵션 가성비 비교 방법론, 단가표, 실험 기록
  tools/
    merge-results.js                 # results/*.json → results/comparison.csv (엑셀용)
    verify-agent-connect.js          # 1회성 수동 검증(부하테스트 아님) — ADMIN 인식 + 고객↔상담사 연결 확인, cognito-admin-verification.md와 짝
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

k6는 EC2에 직접 설치하거나(`https://k6.io/docs/get-started/installation/`), 다른 머신에서 원격으로 EC2를 향해 쏴도 된다 — 스크립트는 `-e` 인자로 대상만 바꾸면 되므로 어느 쪽이든 동일하다. 이 프로젝트에 그런 EC2가 아직 없다면 `runbooks/ec2-loadtest-setup.md`에 만들기/지우기 CLI 명령을 정리해뒀다(공유 계정 태그 규칙 포함).

**항상 `k6/` 디렉터리 안에서 `k6 run scenarios/...`로 실행할 것.** `lib/report.js`가 결과를 `results/...`라는 상대경로에 쓰는데, 이건 실행 시점의 작업 디렉터리 기준이다 — 저장소 루트에서 `k6 run k6/scenarios/...`로 실행하면 `k6/results/`가 아니라 루트에 엉뚱한 `results/`가 생긴다.

⚠️ **`results/` 디렉터리가 미리 있어야 한다.** k6 스크립트는 JS 샌드박스 안에서 도는 거라 디렉터리를 직접 만들 권한이 없다 — `handleSummary`가 반환한 파일을 k6 바이너리가 실행 종료 시점에 쓰는데, 그 시점에 `results/`가 없으면 "no such file or directory"로 **결과 저장 자체가 조용히 실패**한다(테스트 자체는 끝까지 돌고 콘솔 요약도 나오지만, JSON이 안 남는다 — 처음 그 자리에서 알아채기 쉽지 않다). 매번 실행 전에:

```bash
cd k6
mkdir -p results
```

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

- **01-traffic-spike**: catalog HPA 실측치(2→20, `k8s/catalog/hpa.yaml`)에 맞춘 ramping-vus. 기획서 요구치("1초 만에 5,000 VUs")에 맞춰 완만한 램프업 대신 `{duration:'1s', target:5000}`로 즉시 밀어붙이도록 바꿨다 — k6 실행 머신 자원을 충분히 줄 것. P95<500ms, 에러율<0.1%.
- **02-cache-offload**: 동일 쿼리 반복. 캐시 미구현 상태에선 베이스라인만 확보(§0-3).
- **03-pod-failure**: catalog·order(격리 대상)와 ai(장애 유발 대상) 동시 부하. `runbooks/chaos-actions.md`와 짝으로 실행. `orderSteady`는 기획서 KPI "주문/결제 API 성공률 100% 유지"를 직접 검증한다 — book_id=`SECONDARY_BOOK_ID`(기본 101)를 쓰므로 05와 재고를 나눠 쓴다. **실행 전 AI_DAILY_QUOTA 상향 필수**(§0-9) — 안 하면 `ai_quota_exceeded`가 결과를 오염시킨다.
- **04-rolling-deploy**: 지속 부하 중 배포 트리거. EC2는 다운타임 발생이 "정상"(그게 증명하려는 것), EKS는 5xx 0건이 목표.
- **05-payment-concurrency**: 재고 100개 도서에 1,000 VU가 동시 1회 주문. **정합성은 k6 결과가 아니라 DB 직접 조회로 확정**(스크립트 주석의 SQL 참고).
- **06-chat-concurrency**: 고객 계정 풀 + 상담사(ADMIN) 계정 1개로 ESCALATE→CLAIM→SAY 흐름을 실제로 재현해 서로 다른 세션 간 브로드캐스트 유실률/지연을 측정한다(§0-10) — WS 라우팅 자체는 해결됐다(§0-4). `k6/websockets`(stable) 모듈을 쓴다 — `k6/experimental/websockets`는 deprecated.
- **07-connection-saturation**: ramping-arrival-rate로 부하를 계단식으로 올려 5xx가 튀는 지점을 찾는다. RDS Proxy 적용 전/후 비교용.
- **08-namespace-contention**: `TARGET_ENV=integrated`(dev/prod가 한 EKS에 네임스페이스로만 분리) 전용. 한쪽 환경에 부하를 걸면서 다른 쪽 환경의 응답시간을 관찰 — split과 달리 자원 간섭이 있는지가 곧 "완전분리가 필요한 이유"의 실측 근거가 된다. `ENABLE_LOAD=false`로 베이스라인부터 잴 것.
- **09-hpa-metric-comparison**: CPU 기반 HPA(`ai-rag`) vs 요청 기반 HPA(`ai-bot`) 대조 — 둘 다 I/O 바운드라 CPU는 안 오르는데 Pod 수가 다르게 움직이는지 확인. **k6 메트릭만으론 결론 못 냄, `kubectl get hpa/pods -w`를 같이 볼 것.** ai-bot 쪽은 Prometheus Adapter/KEDA가 설치돼 있어야 동작(`ScalingActive` 먼저 확인). ai-rag 쪽은 **AI_DAILY_QUOTA 상향 필수**(§0-9).
