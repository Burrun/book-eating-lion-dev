# contracts/ — 서비스 간 계약 (단일 진실 공급원)

Phase 0-4 의 산출물이다. **이 디렉터리의 YAML 이 계약이자 mock 이다.**

## 왜 산문 명세가 아니라 YAML 인가

`docs/superpowers/specs/*.md` 는 산문이라 필드명 불일치가 컴파일 타임에 잡히지 않는다.
4개 팀이 병렬로 개발하면 그 불일치가 통합일에 한꺼번에 터진다 — 계획서 §5 가
**최상단 리스크**로 꼽은 항목이다.

## 파일

| 파일 | 서비스 | 로컬 포트 | mock 포트 |
| --- | --- | --- | --- |
| `catalog-v1.yaml` | catalog-service | 8081 | 4401 |
| `order-v1.yaml` | order-service | 8082 | 4402 |
| `member-v1.yaml` | member-service | 8083 | 4403 |
| `ai-v1.yaml` | ai-service | 8084 | 4404 |

## mock 서버

```bash
docker compose -f backend/contracts/docker-compose.mock.yml up
```

`mocks/` 디렉터리를 따로 만들지 않는다. 그러면 두 번째 진실 공급원이 생겨
계약과 mock 이 갈라진다. Prism 이 위 YAML 을 그대로 mock 으로 띄우므로
**drift 가 원천적으로 불가능**하다.

의존 서비스 없이 개발하려면 URL 만 mock 으로 돌린다:

```bash
SERVICES_ORDER_URL=http://localhost:4402 ./gradlew :apps:catalog-api:bootRun
```

## 계약에서 절대 바꾸면 안 되는 것

| 항목 | 이유 |
| --- | --- |
| `GET /internal/inventory` 가 **벌크**라는 점 | 단건으로 바꾸면 도서 목록에서 N+1 이 난다. 나중에 되돌리려면 양쪽 서비스를 다 고쳐야 한다 |
| JWT 의 `member_id` / `nickname` 클레임 | 이게 없으면 모든 서비스가 회원 확인을 위해 member-service 를 동기 호출하게 되고, 인증이 전 요청의 임계경로가 된다 |
| 임베딩 차원 **1024** (`ai-v1.yaml`) | 바꾸면 `lion_memories` 전건 재임베딩이 필요하다. 재고 소유권과 같은 등급의 결정이다 |

## 아직 하지 않은 것

Contract test 를 `backend-ci.yml` 에 붙이는 작업(§5 리스크 대응)은 미완이다.
없으면 문서에만 존재하는 엔드포인트가 쌓이므로, Phase 1 착수 전에 추가할 것.
