# WAF / CloudFront 검증 절차

WAF와 CloudFront는 오리진(EC2/EKS Ingress) 앞단의 엣지 레이어라서, k6로 오리진을 직접 때리는 방식으로는 "WAF가 막았는지"를 알 수 없다 — 오리진에 도달한 시점엔 이미 WAF를 통과한 트래픽이다. 그래서 이건 k6 스크립트가 아니라 수동 절차로 관리한다.

`terraform/modules/compute/edge_routing`에 WAF/CloudFront 모듈 코드가 있고 dev 환경 `main.tf`에도 실제로 배선돼 있다(README §0-6) — 다만 `terraform output`으로 apply 상태를 먼저 확인할 것. 확인되면 아래 진행:

## WAF 봇 차단율

1. k6로 **CloudFront 도메인**(오리진이 아니라)에 악성 패턴을 흉내낸 요청을 보낸다 — 과도한 요청 빈도, 알려진 스크래핑 `User-Agent` 헤더 등.
2. WAF 콘솔의 `Sampled requests` 및 CloudWatch `BlockedRequests` 메트릭으로 차단 건수를 확인한다.
3. k6 응답 상태코드(403)로도 1차 확인 가능 — `check(res, { 'blocked by WAF': (r) => r.status === 403 })`.

## CloudFront 캐싱 효과

1. 정적 자산(프론트엔드 빌드 결과물, 도서 커버 이미지)에 대해 CloudFront 도메인으로 동일 URL을 반복 요청한다.
2. 응답 헤더 `X-Cache: Hit from cloudfront` 비율을 집계한다.
3. CloudWatch에서 오리진(S3) 트래픽 감소량을 CloudFront 미적용 대비로 비교한다.

## 기록 형식

`tools/merge-results.js`가 다루는 JSON 결과 포맷과는 별도로, 이 항목은 아래 표를 수동으로 채워 `results/waf-cloudfront-manual.csv`로 저장해 Excel 비교표에 합친다:

| 항목 | 값 |
|---|---|
| WAF 차단 대상 요청 수 | |
| 실제 차단된 요청 수 / 차단율 | |
| CloudFront Cache Hit 비율 | |
| 오리진(S3) 트래픽 절감률 | |
