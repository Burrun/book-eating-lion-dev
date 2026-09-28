# 트러블슈팅 — Redis(Valkey) · SQS 메시징

> 비동기 채널에서 겪은 문제들이다. 이 영역의 고장은 대부분 **에러 없이 조용히** 나타난다 —
> 파드는 떠 있는데 메시지만 안 오거나, 기동이 끝나지 않고 멈춰 있다.
>
> 채널 선택 기준은 [README §4-2](../README.md#4-2-메시징-채널-sqs--redis-streams--redis-pubsub--sns) 참고.

---

## 1. Valkey TLS 설정이 없어서 앱이 기동 중 영원히 멈췄다

**상태**: 해결 · `07173be` (2026-08-21) · 인프라-트러블슈팅 ⑬

### 증상

catalog / order / ai 파드가 전부 `CrashLoopBackOff`. 로그는 Hibernate 초기화 이후 **아무 에러 없이 끊겼고**, Startup probe 180초 뒤 kubelet이 강제 종료(exit 143)하고 재시작을 반복했다.

### 원인

ElastiCache Valkey는 Terraform `cache_valkey` 모듈에서 `transit_encryption_enabled = true`(**TLS 강제**)로 만들었다. 그런데 Spring 쪽에는 `spring.data.redis.ssl.enabled`가 없었다.

- 클라이언트는 평문 TCP로 연결
- 서버는 TLS 핸드셰이크를 기다리며 응답하지 않음
- 커넥션 타임아웃 설정도 없어 **Redis 커넥션 팩토리 초기화 단계에서 무한 대기**

### 해결

서비스 공통 파일 `modules/common/src/main/resources/application-redis-prod.yml`에 `ssl.enabled: true`를 넣고, 각 서비스의 `application-prod.yml`이 이 파일을 import하게 했다.

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST}          # 기본값 없음 — 없으면 기동 실패가 맞다
      port: ${REDIS_PORT:6379}
      ssl:
        enabled: true
```

member-api만 이 공통 파일을 쓰지 않고 자체 블록을 복붙해 두었던 것도 정리했다.

### 같은 뿌리의 사고: `REDIS_HOST`가 안 읽혔다

k8s ConfigMap은 `REDIS_HOST`를 주는데 Boot가 읽는 키는 `spring.data.redis.host`다. 매핑이 없어서 prod 파드가 **컨테이너 안 `localhost:6379`**를 찾고 있었다. 세 서비스의 `application-prod.yml`에 같은 블록이 복붙돼 있어 **같은 사고가 세 번 반복**됐고, 그래서 값을 공통 파일 한 곳에서만 관리한다. 기본값을 두지 않는 것도 의도적이다 — 조용히 로컬로 붙는 것보다 기동 실패가 낫다.

---

## 2. Redisson(분산 락)만 여전히 평문으로 붙었다

**상태**: 해결 · PR #63 (`6444a10`, 2026-08-21)

### 증상

§1을 고친 뒤 catalog / ai는 떴는데 **order-service만** 계속 기동 중 멈췄다(`PING` 타임아웃).

### 원인

order는 재고 차감 분산 락에 **Redisson**을 쓴다. Spring Data Redis(Lettuce)는 `spring.data.redis.ssl.enabled`를 자동으로 따라가지만, Redisson은 별도 클라이언트라 이 값을 읽지 않고 `"redis://"`(평문)가 하드코딩돼 있었다.

### 해결

`RedissonConfig`가 같은 프로퍼티를 읽어 스킴을 직접 고른다. TLS 없이 붙을 때는 WARN을 남긴다.

```java
@Value("${spring.data.redis.ssl.enabled:false}")
private boolean sslEnabled;

String scheme = sslEnabled ? "rediss://" : "redis://";
config.useSingleServer().setAddress(scheme + host + ":" + port);
```

### 재발 방지

**같은 Redis를 두 클라이언트가 쓰면 설정도 두 번 확인한다.** Redisson을 락 전용 커넥션으로 분리한 결정 자체는 유지했다(이벤트·캐시와 용도가 다르다) — 대신 설정 출처를 하나로 맞췄다.

---

## 3. Redis Streams 컨테이너 빈이 둘이 되자 기동이 실패했다

**상태**: 해결 · `266c2f0` (2026-08-18)

### 증상

catalog에 재입고 알림 컨슈머(`InventoryRestockedStreamConfig`)를 추가하자 기동 시 빈 주입 오류.

### 원인

리뷰 권한과 재입고 알림이 각각 `StreamMessageListenerContainer<String, MapRecord<…>>` 빈을 만든다. **같은 타입의 빈이 둘**이 되었고, 구독 빈이 어느 컨테이너를 받을지 파라미터 이름으로 고르고 있었다. 파라미터 이름 매칭은 컴파일러의 `-parameters` 설정에 의존해 **조용히 깨진다.**

### 해결

두 곳 모두 `@Qualifier("reviewPermissionContainer")` / `@Qualifier("inventoryRestockedContainer")`로 명시했다. 소스에는 "빼면 기동이 실패한다"는 주석을 남겼다.

### 함께 알아둘 것 — `BUSYGROUP`

`XGROUP CREATE`는 멱등하지 않다. 이미 그룹이 있으면 `BUSYGROUP` 에러가 난다. 기동 때마다 그룹 생성을 시도하되 예외를 삼킨다(`ReviewPermissionStreamConfig.StreamSubscriptionLifecycle`). 컨슈머 이름은 `catalog-{HOSTNAME}`으로 파드마다 다르게 둔다.

| Stream | 컨슈머 그룹 | 실패 처리 |
| --- | --- | --- |
| `events:review-permission-granted` | `catalog-service` | auto-ack + PK 멱등 |
| `events:inventory-restocked` | `catalog-restock-alerts` | `processed_restock_events` + 메일 재시도 스케줄러 |
| `events:catalog:recommendation-books` | `ai-recommendation-indexer` | 실패 시 ACK 안 함 → pending 보존 |

---

## 4. 채팅에 Streams 컨슈머 그룹을 쓰면 메시지가 "가끔" 사라진다

**상태**: 설계 단계에서 차단 · `ChatPubSubConfig`

1:1 상담은 WebSocket이라 사용자와 상담사의 소켓이 **서로 다른 파드**에 붙을 수 있다. 저장소에 이미 있던 비동기 수단(Streams + 컨슈머 그룹)을 그대로 쓰면:

- 컨슈머 그룹의 목적은 "여러 파드 중 **정확히 하나만** 처리"
- 채팅은 정반대로 "그 방 소켓을 쥔 **모든** 파드가 받아야" 함
- 메시지가 소켓 없는 파드 하나로 배달되고, 그 파드는 조용히 버림 → **에러도 로그도 없이 "가끔 메시지가 안 감"**

그래서 채팅 팬아웃만 **Redis Pub/Sub**을 쓴다. Pub/Sub은 at-most-once라 구독 전에 발행된 메시지는 사라지므로, 소켓이 붙을 때 **구독을 먼저 하고 전사를 나중에 조회**한다. 겹쳐 받을 수는 있어도 유실은 없고, 중복은 `seq`로 지운다. 방 판별은 메시지 본문의 `roomId`가 아니라 **채널명**으로 한다(본문을 믿으면 다른 방 메시지를 엉뚱한 방에 뿌릴 수 있다).

상담사 배정은 Lua 스크립트(`chat-claim.lua`)로 원자적으로 처리한다. `SETNX` → 상태 전이 → 대기열 제거를 따로 하면, 그 사이 파드가 죽었을 때 "락은 잡혔는데 방은 WAITING"이 남아 사용자가 아무도 오지 않는 방에 갇힌다.

---

## 5. 베스트셀러 캐시가 저장은 되는데 읽을 때 깨졌다

**상태**: 개발 중 발견·해결 (로컬 Redis로 재현) · `RedisCacheConfig`

### 증상

`GET /api/catalog/books/bestsellers` 첫 호출은 성공, 두 번째(캐시 히트)부터 역직렬화 오류.

```text
Unexpected token (START_OBJECT), expected VALUE_STRING
```

### 원인

범용 `GenericJackson2JsonRedisSerializer`는 이 조합에서 비대칭이다. 원소(`BookSummaryResponse`, final record)에는 타입 태그가 붙지만 `List` 컨테이너 자체는 타입 래핑이 안 된다. **쓰기는 되고 읽기만 실패**한다. 로컬에서 Redis를 붙여 재현해 확인했다.

### 해결

`bestsellers` 캐시만 타입을 고정한 `Jackson2JsonRedisSerializer<List<BookSummaryResponse>>`를 쓴다. 직렬화에는 애플리케이션의 `ObjectMapper` 빈을 그대로 주입받는다 — 캐시만 별도 `ObjectMapper`를 만들면 API 응답 직렬화와 모듈·네이밍 전략이 어긋난다.

TTL도 캐시별로 분리했다(`bestsellers` 30분, 기본 10분). 베스트셀러는 실시간 정합성이 아니라 **DB 오프로딩**이 목적이라 `@CacheEvict` 없이 TTL로만 신선도를 관리한다.

---

## 6. SQS 큐 URL이 배선되지 않아 `Circular placeholder reference`로 기동 실패

**상태**: 해결 · 인프라-트러블슈팅 ⑨ (`bd275b8` → revert 후 `7560bb1`로 재적용, 2026-08-21)

### 원인

Terraform `ai_pipeline` 모듈이 구매 큐 URL은 output으로 내보내면서 **인제스트 큐 URL은 빠뜨렸다.** SSM, `sync-github-config.sh`, `main-cd.yml`의 `envsubst` VARS 목록에도 없어서, ConfigMap에 `${SQS_INGEST_QUEUE_URL}` **리터럴이 그대로** 들어갔다. Spring은 이걸 자기 자신을 가리키는 플레이스홀더로 해석해 순환 참조로 기동 실패했다.

### 해결 · 재발 방지

- 모듈 output → SSM → CD 배선 추가.
- `main-cd.yml`에 **필수 값 빈 값 가드**를 두었다. `envsubst`는 빈 값을 조용히 넘기기 때문에, 비어 있으면 `apply` 전에 `::error::`로 끊는다(롤백 워크플로도 같은 가드 세트).
- CI의 `k8s 매니페스트 파싱 + ConfigMap 키 참조 검사`가 `configMapKeyRef` 오타를 배포 전에 잡는다.

또, 앱의 발행/소비 코드는 이미 동작 중인데 **큐 자체가 Terraform에 없던** 적도 있었다(`book-purchase-queue`). 지금은 두 큐 모두 DLQ와 함께 코드로 관리한다.

---

## 7. SQS 소비 루프의 함정들

**상태**: 설계 기록 · `SqsWorker`, `SqsIngestListener`, `SqsPurchaseListener`

직접 구현한 롱폴링 루프라 한 곳(`SqsWorker`)에 위험한 규칙을 모았다.

| 함정 | 결과 | 대응 |
| --- | --- | --- |
| 실패한 메시지를 지운다 | 이벤트 영구 유실 (산 책을 못 읽음, 책이 영영 검색 안 됨) | **정상 반환했을 때만 삭제.** 예외면 남겨서 재배달 → `maxReceiveCount=3` 초과 시 DLQ(14일 보관) |
| 리스너가 예외를 잡아 삼킨다 | 워커가 "성공"으로 보고 삭제 | 리스너는 예외를 **다시 던진다**. 단, `epubS3Key`가 없는 메시지처럼 영원히 성공할 수 없는 건 정상 반환해 지운다(실패가 아니라 "대상 아님") |
| 폴링 예외로 루프가 끝난다 | 파드는 살아 있고 헬스체크도 통과하는데 **소비만 멈춤** — 가장 늦게 발견되는 고장 | 예외를 로그로 남기고 5초 쉬고 계속 돈다 |
| 처리는 끝났는데 삭제가 실패 | 재배달 | 핸들러를 멱등하게: 인제스트는 delete-then-put, 구매는 PK 중복 확인 |
| 인제스트가 가시성 시간(300s)을 넘김 | **처리 중인 책이 재배달 → 중복 임베딩 = 중복 과금** | 1건씩 처리, 원본 SHA-256이 같으면 임베딩 생략, 로그의 소요 시간으로 확인 |
| 롱폴링 `waitTimeSeconds=0` | 빈 응답을 초당 수십 번 받아 요청 과금만 증가 | 20초 롱폴링 |
| 큐 URL이 빈 로컬에서 폴링 | `QueueDoesNotExistException` 로그 폭주 | URL이 비면 리스너를 띄우지 않는다 |

---

## 8. 로컬에서 SQS 리스너를 켜면 배포 환경의 이벤트를 훔쳐 간다

**상태**: 운영 규칙 · `.env.example`

SQS는 메시지를 **컨슈머 하나에게만** 준다. 로컬 ai-service에서 `SQS_INGEST_ENABLED=true`나 `SQS_PURCHASE_ENABLED=true`로 실제 큐를 가리키면, 배포된 컨슈머와 **경쟁**해서 실제 이벤트를 가져가고, 로컬은 처리에 실패해 메시지를 DLQ로 보낸다.

그래서 두 플래그의 기본값은 `false`다. 켜면 파드가 큐를 계속 폴링하고, 메시지가 오면 임베딩 비용이 나기 때문에 **명시적으로 켜야만** 돈다.
