1
# contracts/ — 서비스 간 계약 (단일 진실 공급원)

Phase 0-4 의 산출물이다. **이 디렉터리의 YAML 이 서비스 간 계약의 단일 진실 공급원이다.**

## 왜 산문 명세가 아니라 YAML 인가

`docs/superpowers/specs/*.md` 는 산문이라 필드명 불일치가 컴파일 타임에 잡히지 않는다.
4개 팀이 병렬로 개발하면 그 불일치가 통합일에 한꺼번에 터진다 — 계획서 §5 가
**최상단 리스크**로 꼽은 항목이다.

## 파일

| 파일 | 서비스 | 로컬 포트 |
| --- | --- | --- |
| `catalog-v1.yaml` | catalog-service | 8081 |
| `order-v1.yaml` | order-service | 8082 |
| `member-v1.yaml` | member-service | 8083 |
| `ai-v1.yaml` | ai-service | 8084 |

## 검증

```bash
python -c "import yaml,glob; [yaml.safe_load(open(p,encoding='utf-8')) for p in glob.glob('backend/contracts/*.yaml')]"
```

2초면 끝난다. 실제로 `ai-v1.yaml` 은 커밋된 시점부터 파싱에 실패하고 있었다 —
flow mapping `{ }` 안의 `?` 가 YAML 의 복합 키 지시자로 잡혀서다. 이런 건
사람이 읽어서는 못 잡는다.

> **Prism mock 은 걷어냈다.** `docker-compose.mock.yml` 로 4개 서비스 mock 을
> 띄우게 돼 있었는데, 계약이 파싱조차 안 되는 상태였고 `stoplight/prism:5` 도
> `require("node:cluster").default` 접근으로 기동 즉시 크래시했다. 그런데도
> 아무도 불편해하지 않았다 — 쓰는 사람이 없었다는 뜻이다.
> 프론트는 `pnpm gen` 으로 실서버 Swagger 에서 타입을 뽑고, 백엔드 4개는
> `docker compose up` 한 번에 다 뜬다. 필요해지면 그때 다시 넣는다.

## 계약에서 절대 바꾸면 안 되는 것

| 항목 | 이유 |
| --- | --- |
| `GET /internal/inventory` 가 **벌크**라는 점 | 단건으로 바꾸면 도서 목록에서 N+1 이 난다. 나중에 되돌리려면 양쪽 서비스를 다 고쳐야 한다 |
| JWT 의 `member_id` / `nickname` 클레임 | 이게 없으면 모든 서비스가 회원 확인을 위해 member-service 를 동기 호출하게 되고, 인증이 전 요청의 임계경로가 된다 |
| 임베딩 차원 **1024** (`ai-v1.yaml`) | S3 Vectors 인덱스는 생성 후 차원을 못 바꾼다. 바꾸려면 인덱스 재생성 + 전건 재인제스트다. 재고 소유권과 같은 등급의 결정이다 |
| `bookIds` 가 **좁히기 전용**이라는 점 (`ai-v1.yaml`) | 클라이언트가 보낸 목록을 그대로 믿으면 먹지 않은 책의 본문을 읽어갈 수 있다. 서버는 항상 "먹인 책"과 교집합을 취한다 |

## 아직 하지 않은 것

Contract test 를 `backend-ci.yml` 에 붙이는 작업(§5 리스크 대응)은 미완이다.
없으면 문서에만 존재하는 엔드포인트가 쌓이므로, Phase 1 착수 전에 추가할 것.
