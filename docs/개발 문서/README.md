# 개발 문서 안내

팀원이 매주 교체되는 프로젝트라, 이 문서 하나로 "전체 문서가 어디에 뭐가 있고 뭐부터 읽어야 하는지" 파악할 수 있게 만들었습니다. 문서가 여러 곳에 흩어져 있어서(저장소 루트, 이 폴더, `k6/`, `backend/contracts/`) 그것부터 지도로 그려둡니다.

---

## 처음 왔으면 이 순서로 읽으세요

1. **[`/README.md`](../../README.md)** (저장소 최상위) — 가장 먼저. MSA 전환 배경, 서비스 4개 구성, 핵심 설계 결정 4가지, 로컬 실행법(`docker compose up`), "검증된 항목"/"남은 작업" 체크리스트가 있습니다. **이게 사실상 온보딩 문서입니다.**
2. **[`db-erd-v2.md`](db-erd-v2.md)** — 실제 DB 스키마(`db/postgres/*.sql` 기준, 코드 검증됨). 어떤 테이블이 어느 서비스 소유인지 여기서 확인하세요.
3. **[`이벤트-메시징-명세.md`](이벤트-메시징-명세.md)** — 서비스 간 비동기 통신(SQS/Redis Streams) 전부. 코드 파일·라인까지 인용돼 있어 가장 정밀합니다.
4. **[`k8s-명세.md`](k8s-명세.md)** — 배포 스펙(HPA, Probe, Ingress 라우팅, ConfigMap). ⚠️ Ingress 라우팅 표에 알려진 버그가 있으니 §3 경고 박스 꼭 읽으세요.
5. 그 다음은 필요할 때 골라 읽으면 됩니다 — 기획서/요구사항서는 "왜 이렇게 만들었는지" 배경이 궁금할 때, 테라폼 문서는 인프라 작업할 때.

---

## 이 폴더(`docs/개발 문서/`)의 문서

| 문서 | 다루는 내용 | 신뢰도 |
| --- | --- | --- |
| [`db-erd-v2.md`](db-erd-v2.md) | 4개 스키마(`member_db`/`catalog_db`/`order_db`/`ai_db`) 전체 테이블·컬럼·관계 | ✅ 코드(`db/postgres/*.sql`) 기준, 신뢰 가능 |
| [`이벤트-메시징-명세.md`](이벤트-메시징-명세.md) | SQS/Redis Streams 이벤트 4종, 발행측/소비측 구현 상태, 인프라(큐·IAM·ConfigMap) 요구사항 | ✅ 코드 기준이나 원본 검증 시점(`6af8640`)이 오래돼 일부는 이후 갱신됨 — 문서 상단 갱신 메모 참고 |
| [`k8s-명세.md`](k8s-명세.md) | 네임스페이스, 리소스/Probe, Ingress 라우팅, HPA, ConfigMap/Secret, NetworkPolicy | ⚠️ 대체로 정확하나 Ingress 라우팅 표는 **실제 배포된 버그**를 그대로 기록한 것 (§3 참고) |
| [`기획서-v6.md`](기획서-v6.md) | 프로젝트 배경, 사용자/운영자 시나리오, MSA 도메인 설계, 기술 채택 근거, 부하테스트 계획 | 📋 기획 문서 — 일부 표현(ElastiCache **Redis**, **3AZ** 등)이 구버전. 실제 스펙은 `TERRAFORM_STRUCTURE.md`/`db-erd-v2.md` 참고 |
| [`요구사항-정의서-v2.md`](요구사항-정의서-v2.md) | 기능/비기능 요구사항, 기능 목록 20개, 차별화 전략 | 📋 기획 문서 — 정기구독을 하나처럼 서술하지만 실제로는 `premium_memberships`(eBook 구독)와 `subscriptions`(실물 배송)로 나뉨(`db-erd-v2.md` 참고) |
| [`TERRAFORM_STRUCTURE.md`](TERRAFORM_STRUCTURE.md) | AWS 인프라 계층 구조·모듈 설계 (`terraform/TERRAFORM_STRUCTURE.md`와 동일 사본) | ✅ `00-base`/`01-data`/`02-runtime` 3계층 전부 `.tf` 코드 구현·`terraform validate` 통과 완료(`terraform` 브랜치). 구현 중 발견한 설계와의 차이는 각 모듈 설명에 "실제 구현 중 발견"/"재검토 중 발견"으로 표시돼 있음. AI 파이프라인(S3 Vectors)만 여전히 provider 미지원으로 출력값이 `null` |

---

## 이 폴더 밖에 있는 문서

| 문서 | 위치 | 내용 |
| --- | --- | --- |
| 프로젝트 README | `/README.md` | 위 "처음 왔으면" 참고 |
| API 계약 (OpenAPI) | `backend/contracts/*.yaml` + `backend/contracts/README.md` | 서비스 4개 API 스펙의 **단일 진실 공급원**. 프론트 타입도 여기서 생성 |
| 부하테스트 실행 가이드 | `k6/README.md` | k6 스크립트, **실행 전 확인할 것(코드와 대조한 버그/불일치 목록)** — 라우팅 버그도 여기서 먼저 발견됨 |
| 부하테스트 런북 | `k6/runbooks/*.md` | 카오스 테스트, WAF/CloudFront 검증 절차 |

---

## 알려진 문서 공백

- `docs/msa-migration-plan.md`, `docs/ai-api-plan.md` — 루트 `README.md`가 참조하지만 저장소에 커밋된 적이 없습니다. README 자체가 그 내용을 상당 부분 대신합니다.
- 프론트엔드(`frontend/`) 아키텍처 문서 없음.
- 결제 수단 값(`payment_method`)이 DB 제약(`CARD`/`KAKAOPAY`)과 OpenAPI 계약(`VIRTUAL_CARD`/`KAKAO_PAY`, `k6/README.md` 근거) 사이에 표기가 다릅니다 — 아직 어디가 맞는지 정리되지 않았습니다.
