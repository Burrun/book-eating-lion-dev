# 트러블슈팅 — AI · RAG (Bedrock · S3 Vectors)

> "먹인 책 RAG(라이언에게 묻기)"와 인제스트 파이프라인에서 겪은 문제들이다.
> RAG의 고장은 대부분 **예외가 아니라 "근거를 찾지 못했습니다"**로 나타난다. 그래서 이 문서의 절반은
> "조용히 틀리는" 경로를 어떻게 시끄럽게 만들었는지에 대한 기록이다.
>
> 파이프라인 전체 그림은 [README §4-5](../README.md#4-5-ai--rag) 참고.

---

## 1. RAG 접근 권한 캐시가 stale 상태에서 self-heal 되지 않았다

**상태**: 해결 · PR #129 (`e8741e4`, 2026-09-01)

### 증상

분명히 산 책인데, 특정 사용자에게서만 그 책 하나가 계속 "근거를 찾지 못했습니다". 다른 책은 정상. 시간이 지나도 낫지 않았고, **활발히 쓰는 사용자일수록** 더 오래 지속됐다.

### 원인

검색 허용 목록(구매한 책)은 Redis Set `ai:purchased:{memberId}`(TTL 24h)가 읽기 경로이고 원본은 `ai_db.purchased_books`다.

```java
// 수정 전 PurchasedBookCache.add()
try {
    redis.opsForSet().add(key, String.valueOf(bookId));
    redis.expire(key, TTL);
} catch (DataAccessException e) {
    log.warn(...);          // ← 로그만 찍고 삼킴
}
```

1. 새 구매의 `SADD`가 순간 장애로 실패 → 로그만 남고 끝
2. Set은 **비어 있지 않으므로** 조회 시 캐시 히트로 판단 → DB를 보지 않음
3. 이 책 하나만 빠진 **부분 반영 상태**가 굳음
4. 이후 다른 구매/먹이기가 있을 때마다 `expire`가 다시 24h로 갱신 → **TTL로도 자연 치유되지 않음**

"캐시 쓰기 실패는 안전하다(원본이 DB에 있으니까)"는 가정이 **캐시가 통째로 없을 때만** 참이었다. 부분적으로 틀린 캐시는 없는 캐시보다 나쁘다.

### 해결

`add()`가 실패하면 **그 회원의 캐시를 통째로 삭제**한다. 다음 조회가 캐시 미스로 DB에 떨어져 방금 커밋된 구매까지 포함해 전체를 다시 채운다. `FedBookCache`(`ai:fed:*`)도 같은 방식으로 고쳤다.

```java
} catch (DataAccessException e) {
    log.warn("Redis 갱신 실패 — 캐시를 무효화한다. …");
    try { redis.delete(key); } catch (DataAccessException e2) { log.warn(…); }
}
```

### 교훈

- 접근 제어용 캐시는 **fail-closed**다: Redis를 못 읽으면 DB 원본으로 떨어진다. 쿼터(§2)의 fail-open과 **같은 규칙으로 다루면 안 된다.**
- 캐시를 "갱신"하다 실패했을 때의 올바른 복구는 재시도가 아니라 **무효화**인 경우가 많다.

---

## 2. 실패한 질의가 사용자의 무료 한도를 다 써버렸다

**상태**: 해결 · `4bd5399` (2026-08-31)

### 증상

사용자가 답을 한 번도 제대로 못 받았는데 "오늘의 질문 한도(5회)를 모두 사용했습니다".

### 원인

`DailyQuota.consume()`이 **컨트롤러 진입 즉시** 사용량을 올렸다. 그날 dev에서는 두 가지 서버 문제가 겹쳐 있었다.

- 벡터 인덱스가 비어 있어 모든 질의가 근거 없음(`grounded=false`)으로 끝남
- Bedrock 권한 오류로 500

아무것도 돌려주지 못한 질의들이 무료 5회를 전부 소진했다.

### 해결

`check`(소모 없음, 초과면 429)와 `consume`(증가)로 나눴다.

```text
check()  → 이미 초과한 사용자에겐 임베딩·검색 비용을 태우지 않도록 "처리 전"에
처리      → 임베딩 · 벡터 검색 · (answer 모드면) LLM
consume()→ "과금되는 일을 했을 때"만
```

기준은 "LLM을 불렀는가"가 아니라 **"과금되는 일을 했는가"**다. `search` 모드(LLM 없음)도 임베딩·검색 비용이 들기 때문에 센다 — LLM 호출만 세면 `search`가 무제한 무료가 된다. 반대로 거리 가드에 걸려 `grounded=false`로 끝난 질의는 임베딩을 했어도 세지 않는다("답을 못 줬으면 안 받는다").

둘이 나뉘어 동시 요청이 상한을 약간 넘길 수 있지만 괜찮다. 쿼터는 인증이 아니라 **과금 방어선**이고, 같은 이유로 Redis 장애 시 **fail-open**(통과 + WARN)이다.

### 같은 커밋에서 고친 것 — 구독 회원은 읽을 수 있는 책을 물어볼 수 없었다

eBook 열람은 구독 회원에게 보유 도서 전체를 허용하는데, RAG는 구매 이벤트만 근거로 삼아 구독으로 읽는 중에 물으면 "근거를 찾지 못했습니다"만 돌아왔다. 검색 허용 목록을 **구매한 책 ∪ (구독 중이면) 인제스트된 책 전체**로 넓혔다.

- 클라이언트가 보낸 `bookIds`는 여전히 **교집합으로 좁히기만** 한다. 합집합이면 아무 `bookId`나 넣어 권한 없는 책 본문을 읽어 갈 수 있다 — 에러도 로그도 없이 정상 응답처럼 보이는 접근 제어 사고다.
- "전체 허용"도 빈 목록이 아니라 **명시적인 id 목록**으로 넘긴다. 빈 `$in`은 "제한 없음"으로 해석될 여지가 있어 `S3VectorSearchAdapter`는 빈 목록이면 호출 자체를 거부한다.

---

## 3. S3 Vectors 인덱스 문제로 ai-service가 세 번 연속 기동 실패

**상태**: 해결 · PR #68 (2026-08-21) · 인프라-트러블슈팅 ⑯⑰⑱

ai-service는 기동 시 `VectorIndexVerifier`가 인덱스의 **차원·거리척도·비필터 메타데이터 키**를 설정과 대조하고, 다르면 기동을 실패시킨다. 이 값들은 인덱스 생성 후 바꿀 수 없고, 어긋난 채로 뜨면 **에러 없이 틀린 결과만** 나오기 때문이다. 이 검증이 배포 초기에 세 번 연달아 파드를 막았다 — 전부 인프라 쪽 불일치였고, 검증이 없었다면 조용히 틀린 검색이 나갔을 것이다.

| 순서 | 에러 | 원인 | 해결 |
| --- | --- | --- | --- |
| ⑯ | `not authorized to perform: s3vectors:GetIndex` | `ai_service_iam`에 `GetIndex` 권한 누락 | IAM 권한 추가 |
| ⑰ | 인덱스 이름 불일치로 기동 실패 | 앱은 `wiki-v1` / `recommendation-books-v1`을 부르는데 실제 인덱스는 `purchased-book-rag` / `recommendation` | 두 인덱스가 벡터 0개였으므로 **앱이 부르는 이름으로** 재생성(1024 / float32 / cosine) |
| ⑱ | 비필터 키 불일치 | 재생성 시 `--metadata-configuration nonFilterableMetadataKeys=text` 누락 | 삭제 후 옵션 포함해 재생성 |

### 교훈

- **바꾸기 어려운 값은 바꾸기 어렵게 둔다.** 임베딩 모델(`titan-embed-text-v2`)과 차원(1024)은 의도적으로 환경변수가 아니다. 1024차원을 지원하는 다른 모델로 바꿔도 차원 검사는 통과하고 **벡터 공간만 달라져** 검색 결과가 쓰레기가 된다.
- 반대로 LLM 모델과 거리 임계값(`AI_MAX_DISTANCE`)은 자료 마이그레이션이 없으므로 환경변수로 열어 둔다.
- 인덱스 버전 교체는 `AI_VECTOR_INDEX` 이름만 바꾸면 컷오버·롤백이 된다.

---

## 4. 신간 EPUB이 인제스트되지 않았다 — 파이프라인은 살아 있었다

**상태**: 해결 · PR #115 (`9d64e0b`, 2026-08-28)

### 증상

관리자 API(`POST /api/catalog/admin/books/ingest-index/rebuild`)로 재인제스트를 요청해 **큐 발행까지 성공**했는데, `ai_db.wiki_book_chunks`가 계속 0행.

### 원인 (로그로 확정)

```text
SqsWorker: 메시지 처리 실패 [ingest] — body={"bookId":101,...}
IllegalStateException: EPUB 을 읽지 못했다: s3://…-media/books/101/frankenstein.epub
Caused by: AccessDeniedException: assumed-role/…-ai-service is not authorized to perform: s3:GetObject
```

catalog → SQS → ai 파이프라인은 정상이었다. **ai-service가 EPUB 원본을 받아오는 `s3:GetObject` 권한 하나만** 빠져 있었다. catalog의 IAM에는 같은 버킷 권한을 줬는데(PR #110), 실제로 다운로드를 실행하는 ai 쪽에는 처음부터 없었다.

### 잘 동작한 부분

`SqsWorker`가 **실패한 메시지를 지우지 않았기 때문에** 메시지가 재배달되며 로그에 같은 원인이 반복해서 남았고, `maxReceiveCount`를 넘긴 뒤에는 DLQ에 보관됐다. 실패를 삼켜서 지웠다면 원인을 찾기 훨씬 어려웠을 것이다.

### 해결

`ai_service_iam`에 미디어 버킷 `s3:GetObject` 권한 추가. 적용 후 rebuild API를 다시 호출해 `wiki_book_chunks` 생성과 `wiki-v1` 벡터 적재를 확인한다.

---

## 5. Bedrock 호출이 IAM에서 거부됐다 — inference profile의 리전 함정 두 가지

**상태**: 해결 · `b2bccb7` (2026-08-25), `cf5ea99` (2026-08-31)

최신 Bedrock 모델은 **inference profile**로 호출한다(Claude는 `inferenceTypesSupported=[INFERENCE_PROFILE]`이라 맨 모델 ID로는 거부된다 — 그래서 기본값이 `global.anthropic.claude-haiku-4-5-…`다). 프로필은 IAM 권한이 평가되는 리전이 직관과 달라서 두 번 걸렸다.

### 5-1. 문의봇이 가끔만 실패 (`b2bccb7`)

봇 모델 `apac.amazon.nova-micro-v1:0`은 **크로스리전** 프로필이라 호출마다 APAC 안의 임의 리전으로 라우팅된다. IAM에는 프로필 ARN만 있고 **라우팅 대상 리전의 foundation-model ARN**이 없어서, `ap-southeast-2`로 라우팅될 때만 `AccessDeniedException`이 났다.

→ `foundation-model/amazon.nova-micro-v1:0`을 **리전 와일드카드(`*`)**로 허용.

### 5-2. RAG의 LLM 호출이 전부 403 (`cf5ea99`)

IAM에 `global.` 프로필 ARN을 `us-east-1`로 적어 두었다. 그런데 `global.` 프로필은 **호출한 리전(ap-northeast-2)의 ARN으로** 권한이 평가된다. 서울에서 도는 ai-service의 LLM 호출이 전부 403이었다(2026-08-31 실배포). §2에서 무료 한도를 소진시킨 "Bedrock 권한 오류 500"과 같은 날이다.

→ 프로필 ARN을 `ap-northeast-2`로 고치고, 라우팅 대상 모델(`foundation-model/anthropic.claude-haiku-4-5-…`)도 리전 와일드카드로 허용.

### 교훈

inference profile을 쓸 때 IAM에는 **(1) 호출 리전 기준의 프로필 ARN + (2) 라우팅될 수 있는 모든 리전의 foundation-model ARN** 둘 다 필요하다.

---

## 6. 데모 코퍼스와 실제 도서의 `bookId`가 겹쳐 엉뚱한 책이 인용될 뻔했다

**상태**: 해결 · `BookIngestService.guardAgainstIdCollision`

데모 코퍼스(JSONL, bookId 1~5)와 `catalog_db.books`(IDENTITY, 1부터)가 정면으로 충돌했다. 같은 `bookId`로 다른 책이 인제스트되면 **조용히 덮어쓴다** — 기존 벡터가 지워지고, `wiki_books` 제목이 바뀌고, 그 책을 먹였던 사용자는 먹은 적 없는 책을 먹은 상태가 된다. 증상은 "왜 엉뚱한 책이 인용되지"뿐이다.

- 코퍼스를 `900001~` 예약 대역으로 옮겼다.
- ID 재사용 같은 다른 원인으로도 같은 사고가 날 수 있어, **같은 `bookId`에 제목이 다른 책이 오면 예외**를 던진다. 개정판처럼 정상적인 제목 변경이면 해당 `wiki_books` 행을 지우고 다시 인제스트한다 — 덮어쓰기를 기본 동작으로 두는 것보다 한 번 막히는 편이 낫다.

---

## 7. "조용히 틀리는" 코드를 막아 둔 곳들

사고로 이어지기 전에 설계·리뷰 단계에서 막은 것들이다. 전부 **바꾸면 예외도 로그도 없이 결과만 틀려지는** 지점이라 소스에 🔴 주석으로 표시해 두었다.

| 위치 | 잘못 바꾸면 |
| --- | --- |
| `WikiRagService.score()` — `1.0 - distance` | S3 Vectors는 거리(작을수록 유사), 계약은 점수(클수록 유사). 부호를 뒤집으면 **관련 질문엔 모른다고, 무관한 질문엔 자신 있게** 답한다 |
| `WikiRagService.allowedBooks()` — `retainAll` | 합집합으로 바꾸면 남의 책 본문이 새는 접근 제어 사고 |
| `WikiRagService` — `sourceType = user_summary` 제외 필터 | 지우면 예전에 적재된 **개인 메모 벡터가 같은 책을 산 다른 회원의 인용에** 올라온다(`6a26e41`에서 먹이기 대상을 메모 → 책으로 바꾸며 메모 인용을 완전히 제외) |
| `S3VectorSearchAdapter` — 인덱스 `filter`로 권한 필터 | 전건 검색 후 자바에서 거르면 topK를 남의 책이 채워 볼 수 있는 책이 밀려난다 |
| 거리 가드 (`AI_MAX_DISTANCE`) | 근거 없이 LLM을 부르면 반드시 지어낸다. 프롬프트로는 못 막는다 |
| `GuardedAiCalls` (Bulkhead 전용 얇은 층) | `WikiRagService` 안으로 인라인하면 self-invocation이 되어 `@Bulkhead`가 **조용히 사라진다** |
| `BookIngestService` — 적재·건수 검증 **후** `wiki_books` 등록 | 순서를 바꾸면 벡터가 반만 들어간 책이 검색 대상으로 노출된다 |
| SQS 인제스트 가시성 시간(300s) | 장편(200~500청크) 처리가 넘으면 **재배달 → 중복 임베딩 = 중복 과금**. 원본 SHA-256이 같으면 임베딩을 건너뛴다 |

---

## 진단 순서 — "근거를 찾지 못했습니다"가 나올 때

1. **권한 데이터가 도착했나** — `ai_db.purchased_books`에 (memberId, bookId)가 있는가. 없으면 order의 SQS 발행 실패 로그와 큐/DLQ를 본다([이벤트 발행 §4](troubleshooting-event-publishing.md#4-발행-실패를-삼켰더니-구매한-책이-rag에서-조용히-안-보였다)).
2. **캐시가 원본과 같은가** — `SMEMBERS ai:purchased:{memberId}`를 DB와 비교. 다르면 키를 지운다(§1).
3. **책이 인제스트됐나** — `ai_db.wiki_books`에 행이 있는가. 없으면 ai 파드 로그의 `메시지 처리 실패 [ingest]`와 DLQ를 본다(§4).
4. **거리 가드에 걸렸나** — ai 로그 `근거 부족 — LLM 을 호출하지 않는다 … top1거리=`. 임계값(`AI_MAX_DISTANCE`, 기본 0.75)과 비교한다.
5. **외부 호출이 실패했나** — `임베딩/벡터 검색 실패 — grounded=false 로 낮춘다` 로그. IAM·inference profile 리전·인덱스 이름을 본다(§3, §5).
