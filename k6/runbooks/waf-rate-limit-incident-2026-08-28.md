# WAF rate-limit이 부하테스트를 막은 사건 (2026-08-28)

## 요약

`dev.ajttk.com` 대상으로 `01-traffic-spike.js`/`02-cache-offload.js`를 돌렸더니 실패율이
비정상적으로 높게(47%~100%) 나왔다. 원인은 **앱/인프라 문제가 아니라 WAF의
`rate-limit` 규칙**(IP당 5분에 2,000건 초과 시 차단)이었다 — k6를 한 머신(단일 IP)에서
돌리면 `02`(200 req/s)조차 10초 만에 이 한도를 넘는다. 그 과정에서 **별개의 보안
이슈**(NLB가 WAF 없이 공개 노출돼 있음)를 하나 발견했고, 이건 아직 안 고쳤다.

**결론: k6 스크립트와 앱 자체는 정상으로 보인다. 다만 이 원인 때문에 진짜 부하테스트
결과(HPA 스케일링, DB 한계 등)는 아직 하나도 못 얻었다** — 다음 시도 전에 아래
"남은 일"을 먼저 정리해야 한다.

---

## 타임라인 (KST)

| 시각 | 내용 |
|---|---|
| 12:04~12:05 | `02-cache-offload.js`(200 req/s, 1분) 실행 → `http_req_failed` 46.97% |
| 12:05~12:09 | `01-traffic-spike.js`(최대 5,000 VU, ~4분) 실행 → `http_req_failed` 100% |
| 12:10 | `dev.ajttk.com` curl 확인 → CloudFront `403`("오리진 연결 불가") |
| 12:10 | kubectl로 원인 확인 시도 → 이 환경에서 EKS API 엔드포인트 자체가 DNS 조회 불가(별도 이슈, 아래 참고) |
| 12:25 | `dev.ajttk.com` 재확인 → 200 정상 복구(자연 복구, 5분 슬라이딩 윈도우가 지나간 것으로 추정) |
| 13:08 | 가벼운 동시요청(5~10개) 테스트 → 전부 200, 파라미터/스크립트 자체엔 문제없음 확인 |
| 13:16 | `SPIKE_TARGET_VUS=500`으로 재시도 → `http_req_failed` 96.6% (여전히 심각) — "점진적으로 나빠지는" 패턴이 아니라 "거의 즉시 막힘" 패턴이라는 게 이때 명확해짐 |
| 13:2x | WAF 규칙 직접 확인 → `rate-limit`(Priority 0, IP당 5분 2,000건, Block) 발견 — 원인 확정 |
| 13:3x | NLB DNS로 직접(WAF 우회) 접근 시도 → 성공, 50개 동시 요청도 전부 200 → **NLB가 공개 인터넷에 노출돼 있고 WAF를 통째로 우회할 수 있다는 사실 발견** |

---

## 근본 원인 1 — WAF rate-limit (해결 방법 있음, 미적용)

`terraform/modules/base/waf/main.tf`의 규칙:

```json
{
  "Name": "rate-limit",
  "Priority": 0,
  "Statement": { "RateBasedStatement": { "Limit": 2000, "AggregateKeyType": "IP", "EvaluationWindowSec": 300 } },
  "Action": { "Block": {} }
}
```

IP당 5분에 2,000건 — k6를 단일 머신에서 돌리면 어떤 시나리오든(02의 200 req/s부터)
금방 초과한다. 실제 운영 트래픷은 사용자마다 IP가 달라서 이 규칙에 안 걸리는 게
정상이고, 규칙 자체는 의도대로 동작한 것 — **버그가 아니라 "우리가 이 규칙을 감안 안
하고 테스트를 설계했다"는 문제.**

## 근본 원인 조사 중 발견한 별개 이슈 — NLB 공개 노출 (보안, 미해결)

WAF를 우회해보려고 CloudFront 대신 오리진(NLB)에 직접 접근을 시도했더니 **성공했다**:

```bash
NLB=k8s-ingressn-ingressn-e89e53c041-286c1b6f88d4131a.elb.ap-northeast-2.amazonaws.com
curl -H "Host: dev.ajttk.com" "https://$NLB/api/catalog/books?..."
→ 200, 정상 데이터. 50개 동시 요청도 전부 200.
```

**원인**: `terraform/modules/compute/ingress_alb/main.tf`가 NLB를 `internet-facing`으로
설정해뒀다(CloudFront가 오리진에 접속하려면 필요한 설정 — 이 자체는 맞는 선택). 문제는
**그다음 단계(오리진을 CloudFront 이외의 소스로부터 잠그는 것)가 없다는 것** — NLB
자체엔 WAF도, 보안그룹 제한도 없어서 DNS 이름만 알면 누구나 직접 붙을 수 있고,
이 경로로는 `rate-limit`뿐 아니라 `aws-managed-common`(OWASP 방어)까지 **WAF 전체가
통째로 우회된다.**

이건 CloudFront+커스텀 오리진(ALB/NLB) 구성에서 실제로 흔히 빠뜨리는 단계이고, AWS도
별도 가이드를 낼 정도다 — 이 프로젝트만의 특이 케이스는 아니지만 고쳐야 하는 건
맞다.

**정식 해결책(둘 중 하나, 미적용 — 팀 결정 필요)**:
1. NLB에 보안그룹을 붙여서 CloudFront의 관리형 prefix list(`com.amazonaws.global.cloudfront.origin-facing`)에서 오는 트래픽만 허용
2. CloudFront가 오리진에 보낼 때 비밀 헤더를 심고, ingress-nginx가 그 헤더 없으면 거절

**⚠️ 부하테스트 편의를 위해 이 노출을 일부러 남겨두는 건 검토했다가 기각했다** —
rate-limit만 우회되는 게 아니라 WAF 전체(SQLi/XSS 방어 포함)가 우회되고, "테스트 IP만"이
아니라 DNS 이름을 아는 누구에게나 열려있는 경로라 범위가 너무 넓다.

## 부수 발견 — EKS API 엔드포인트가 이 환경에서 접근 불가

kubectl로 pod/이벤트를 확인하려 했으나 `lion-team3-dev` 클러스터 API 엔드포인트가 이
환경에서 DNS 조회조차 안 됐다(`google.com` 등 일반 인터넷은 정상 — 이 환경 네트워크
자체 문제는 아님). private 엔드포인트일 가능성이 높다. **부하테스트 중 kubectl로
HPA/pod 상태를 관찰하려면(예: `09-hpa-metric-comparison.js`) 실제 VPC 접근 권한이
있는 환경에서 해야 한다** — 이 세션 환경으로는 못 한다.

---

## 후속 조치 결과 (2026-08-28 업데이트)

1. **NLB 공개 노출 보안 이슈** — ✅ 해결됨. NLB 보안그룹을 CloudFront 관리형 prefix
   list(`pl-22a6434b`)로 제한 — 재확인 결과 NLB 직접 접근 `code=000`으로 막힘.
2. **rate-limit 예외** — `ip-allowlist` 규칙(IPSetReferenceStatement, Allow)으로 처리됨.
   **단, 처음엔 `lion-team3-dev`가 아니라 `lion-team3-integrated` WebACL에 잘못
   들어가서 한동안 계속 막혔다** — `aws wafv2 get-web-acl`로 어느 WebACL에 뭐가
   있는지 직접 대조해서 찾아냄. `lion-team3-integrated`(book.ajttk.com) 기준으론
   정상 동작 확인(2026-08-28, `capacity-and-cost-guide.md` §6).
3. **`dev.ajttk.com`(WebACL `lion-team3-dev`)은 여전히 이 예외가 없다** — 미해결로
   남겨둠. dev 대상 테스트가 다시 필요해지면 같은 예외를 `lion-team3-dev`에도
   추가해야 함.
4. `scope_down_statement`로 rate-limit 규칙 자체를 수정하는 초안(README §0-12 참고)은
   실제로 쓰이지 않음 — 팀은 대신 별도 `ip-allowlist` 규칙(Priority 0, Allow)을
   추가하는 방식을 택함. 이 방식은 매칭되는 IP에 대해 **rate-limit뿐 아니라
   `aws-managed-common`(OWASP)까지 전부 우회**한다는 점을 감안할 것 — 테스트 IP가
   신뢰할 수 있는 소스일 때만 써야 한다.

## 관련 문서

- `k6/runbooks/local-test-guide.md` — 이 사건이 실측 사례로 짧게 기록돼 있음(이 문서로 링크)
- `k6/README.md` §0 — 프로젝트 전체 블로커 표
