# 트러블슈팅 — 이벤트 발행 (afterCommit)

> 주문·결제처럼 **상태를 바꾼 뒤 다른 서비스에 알리는** 지점에서 겪은 문제들이다.
> 결론은 하나로 모인다: **이벤트는 커밋이 끝난 뒤에만 보내고, 보낸 뒤의 실패는 되돌리려 하지 말고 드러낸다.**
>
> 관련 코드: `OrderService.publishPurchaseConfirmed` · `OrderService.activateSubscriptionIfOrdered` · `AdminBookService.publishIngestEvent` · `SqsBookPurchasePublisher` · `SqsBookIngestPublisher` · `BookPurchaseHandler` · `FeedService`

---

## 1. 롤백된 주문에 리뷰 권한(= eBook 열람 권한)이 새어 나갈 수 있었다

**상태**: 해결 · PR #119 (`644a98c`, 2026-08-31)

### 증상

결제 흐름 중간에 예외가 나서 주문이 롤백돼도, catalog-service에는 그 주문의 **리뷰 권한이 이미 적재**될 수 있는 구조였다.
실제로 2026-08-27 결제 500 장애 때 이 경로가 열려 있었다 — 주문·주문항목 저장과 **이벤트 발행까지 끝난 뒤** 재고 차감에서 `NullPointerException`이 나 롤백됐고, `orders`는 계속 0행이었다(`c97703a`, [DB 트러블슈팅 §2](troubleshooting-database.md#2-inventoryversion이-null이라-결제가-전부-500)).

### 원인

`publishPurchaseConfirmed`는 `order.markPaid()` 직후에 호출된다. 그런데 결제 확정 트랜잭션은 거기서 끝나지 않는다.

```text
markPaid() → [이벤트 발행] → 쿠폰 사용 확정 → 재고 차감 → 장바구니 정리 → 배송 생성(UNIQUE) → COMMIT
```

당시 코드는 두 이벤트를 다르게 다뤘다.

| 이벤트 | 채널 | 발행 시점 (수정 전) |
| --- | --- | --- |
| 구매 확정 | SQS | `afterCommit` ✅ |
| 리뷰 권한 | Redis Streams | **즉시** — "catalog 가용성과 무관한 별개 관심사라 커밋 대기 없이 나가도 된다"는 판단 |

리뷰 권한 발행 뒤의 단계에서 롤백이 나면, Redis Stream에 나간 메시지는 되돌릴 방법이 없다. 그리고 이 권한은 리뷰 작성에만 쓰이지 않는다.

```java
// EbookService.getAccess — eBook 열람 판정
boolean purchased = reviewPermissionRepository.existsByIdMemberIdAndBookId(memberId, bookId);
```

**`review_permissions`가 곧 eBook 열람 권한**이다. 롤백된 주문의 권한이 새면 **결제되지 않은 책을 계속 읽을 수 있다.**

### 해결

리뷰 권한 발행도 구매 확정과 같이 `afterCommit`으로 옮겼다.

```java
Runnable publish = () -> { /* Redis Streams + SQS */ };

if (TransactionSynchronizationManager.isSynchronizationActive()) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() { publish.run(); }
    });
} else {
    publish.run();
}
```

"커밋을 기다리면 catalog 응답이 늦어진다"는 걱정은 해당하지 않는다. 두 채널 모두 비동기라 order는 던지기만 하고, 소비는 상대 서비스가 알아서 한다.

### 같은 PR에서 함께 막은 경합

이벤트 누수의 근본 원인인 "결제 후 롤백" 자체를 줄이려고 락도 보강했다.

- **쿠폰 이중 사용**: 결제 직전 쿠폰 조회를 `SELECT … FOR UPDATE`로 바꿨다. 낙관적 락만으로는 충돌이 **커밋 시점**에야 드러나는데, 그때는 카드/카카오 승인이 이미 끝나 되돌릴 수 없다.
- **카카오 승인 중복 요청**: `approveKakaoPay`가 주문 행을 잠그고 읽는다. 두 번째 요청은 앞 요청의 커밋을 기다렸다가 상태 검사에서 **카카오 승인 API를 부르기 전에** 거절된다 — `createDelivery` UNIQUE 충돌로 "돈은 나갔는데 주문은 미결제"로 롤백되던 경로를 없앴다.

### 재발 방지

- 새 이벤트를 추가할 때 **"이 발행 뒤에 롤백될 수 있는 단계가 있는가"**를 먼저 본다. 있으면 `afterCommit`.
- 권한처럼 **되돌릴 수 없는 부수효과**는 무조건 커밋 뒤로.

---

## 2. Redis 발행이 실패하면 SQS 발행까지 통째로 빠졌다

**상태**: 해결 · PR #119 코드 리뷰 반영 (`644a98c`)

### 증상

§1 수정 직후 리뷰에서 발견. 두 채널을 한 `Runnable`에 넣었더니, **앞쪽 Redis 발행이 예외를 던지면 뒤쪽 SQS 발행이 아예 실행되지 않았다.** Redis 순간 장애 하나로 RAG 검색 권한(SQS)까지 유실되는 구조다.

### 원인

`afterCommit` 시점엔 주문이 이미 커밋돼 있다. 여기서 예외를 올려 봐야 되돌릴 것은 없고, 같은 블록의 나머지 코드만 건너뛴다.

### 해결

채널마다 따로 `try/catch`로 감싸 **서로를 막지 않게** 했다.

```java
Runnable publish = () -> {
    try {
        for (OrderItem item : items) reviewPermissionPublisher.publish(...);
    } catch (RuntimeException e) {
        log.error("리뷰 권한 이벤트 발행 실패 — 결제는 이미 확정됨. 수동 확인 필요. memberId={}", memberId, e);
    }
    try {
        items.forEach(item -> bookPurchasePublisher.publish(memberId, item.getBookId()));
    } catch (RuntimeException e) {
        log.error("구매 확정 SQS 이벤트 발행 실패 — 결제는 이미 확정됨. 수동 확인 필요. memberId={}", memberId, e);
    }
};
```

### 재발 방지

- `afterCommit` 안에서는 **독립적인 부수효과마다 예외 경계를 따로** 둔다.
- 예외를 호출자에게 올리지 않는다. 결제는 성공했으므로 사용자 응답도 성공이어야 한다. Spring의 일반 로그(`afterCommit threw exception`)보다 `memberId`/`bookId`를 담은 ERROR 로그가 추적에 낫다(`SqsBookPurchasePublisher`, `SqsBookIngestPublisher`가 같은 이유로 예외를 삼킨다).

---

## 3. 구독 활성화를 트랜잭션 안에서 부르면 "돈은 나갔는데 주문은 미결제"가 된다

**상태**: 설계 단계에서 차단 · `7b9221c` (2026-08-26, 구독을 결제 경로에 연동)

### 문제

구독권을 카탈로그의 도서 한 행(`book_id 9001`)으로 표현해 기존 주문·결제 경로를 그대로 타게 했다. 결제가 확정되면 order가 member-service에 구독 활성화를 요청해야 하는데, 이걸 **트랜잭션 안에서** 부르면:

1. 카드/카카오 승인 완료 (돈이 나감)
2. member-service 호출 실패 → 예외
3. 주문 트랜잭션 롤백 → 주문은 **미결제**로 남음

최악의 상태가 DB에 굳는다.

### 해결

```text
결제 전      : 중복 구독 검사 — 실패하면 503으로 거부(fail-closed). 승인 API 호출 전이라 환불 불필요
결제 확정    : 주문 먼저 커밋
afterCommit : 구독 활성화 요청 — 실패해도 예외를 삼키고 ERROR 로그("결제는 이미 확정됐다. 수동 복구 필요")
```

- member 쪽 엔드포인트를 **멱등**하게 만들었다(이미 ACTIVE면 409가 아니라 기존 구독 반환). 수동 복구 시 몇 번을 다시 불러도 안전하다.
- 중복 구독 조회가 실패하면 "구독 없음"으로 넘기지 **않는다.** 잘못 막으면 사용자가 다시 시도하면 되지만, 잘못 통과시키면 돈이 나간 뒤에야 드러난다. 그래서 order의 `MemberSubscriptionClient`에는 fallback이 없다.

---

## 4. 발행 실패를 삼켰더니, 구매한 책이 RAG에서 조용히 안 보였다

**상태**: 해결 · PR #110 (`135a93e`, 2026-08-28)

### 증상

책을 구매해도 AI 사서에게 물으면 **"읽을 수 있는 책에서 근거를 찾지 못했습니다."** 에러도 500도 없었다.

### 원인

order-service와 catalog-service 파드에 **IRSA(ServiceAccount ↔ IAM Role)가 없었다.** `SqsClient.sendMessage`가 자격증명 체인에서 실패했지만, §2의 설계대로 발행자는 예외를 삼키고 ERROR 로그만 남겼다.

```text
구매 확정 이벤트 발행 실패: memberId=..., bookId=...
```

주문은 정상 완료 → ai-service는 구매 이벤트를 영영 받지 못함 → `purchased_books`가 비어 있음 → 검색 허용 목록이 빔 → 검색을 하지 않고 `grounded=false`.

"발행 실패가 주문을 깨뜨리지 않는다"는 설계는 의도대로 동작했지만, 그 대가로 **실패가 사용자 쪽에서 보이지 않았다.**

### 해결

- `catalog_service_iam` / `order_service_iam` Terraform 모듈 신설(SQS `SendMessage`, 미디어 버킷 S3 권한) + k8s `ServiceAccount` 연결.
- 두 앱에 `software.amazon.awssdk:sts` 의존성 추가 — 없으면 IRSA를 붙여도 `'sts' service module must be on the class path`로 **증상만 바뀐다.**
- `main-cd.yml`에 `CATALOG_SERVICE_IRSA_ARN` / `ORDER_SERVICE_IRSA_ARN` 빈 값 가드 추가.

### 진단 팁

"근거를 못 찾았다"가 나오면 AI 모델보다 **권한 데이터가 도착했는지**부터 본다.

```bash
kubectl -n <ns> logs deploy/order-deployment | grep "구매 확정 .*발행 실패"
psql ... -c "SELECT * FROM ai_db.purchased_books WHERE member_id = '<sub>'"
aws sqs get-queue-attributes --queue-url <book-purchase-queue> --attribute-names All   # 적체 / DLQ 확인
```

### 남은 과제

발행 실패를 로그로만 남기면 사람이 로그를 봐야 드러난다. 인제스트(`POST /api/catalog/admin/books/ingest-index/rebuild`)와 추천 인덱스(`…/recommendation-index/rebuild`)는 재발행 API가 있지만, **구매 확정·리뷰 권한은 재발행 수단이 없다.** 근본 해법은 이벤트를 같은 트랜잭션의 outbox 테이블에 쓰고 별도 릴레이가 내보내는 **Transactional Outbox**다.

---

## 5. 캐시도 커밋 뒤에 쓴다

**상태**: 설계 단계에서 차단 · `BookPurchaseHandler`, `FeedService`

구매 이벤트 소비(`BookPurchaseHandler`)와 라이언 먹이기(`FeedService`)는 DB에 행을 쓰고 Redis Set(`ai:purchased:*`, `ai:fed:*`)도 갱신한다. 캐시를 트랜잭션 **안에서** 먼저 쓰면, 롤백 시 DB에는 행이 없는데 Redis에는 남는다 — 사자가 먹지도 않은 책을 먹은 것으로 세거나, 사지 않은 책이 검색 권한에 들어간다.

그래서 캐시 갱신도 `afterCommit`으로 미룬다. 반대 방향(커밋은 됐는데 캐시 쓰기 실패)은 안전하다 — 원본이 DB에 있으므로 다음 조회가 다시 채운다. 단, **부분 반영된 캐시**는 안전하지 않다는 것이 나중에 드러났다 → [AI·RAG 트러블슈팅 §1](troubleshooting-ai-rag.md#1-rag-접근-권한-캐시가-stale-상태에서-self-heal-되지-않았다).

---

## 6. (알려진 공백) 재입고 이벤트는 아직 트랜잭션 안에서 발행한다

**상태**: 미해결

`InventoryService.restock`은 재고가 0 → 양수로 바뀌면 `InventoryRestockedEvent`를 **트랜잭션 안에서 바로** Redis Stream에 넣는다.

```java
@Transactional
public InventoryView restock(Long bookId, int quantity) {
    ...
    inventory.restock(quantity);
    if (previousStock == 0 && inventory.getStock() > 0) {
        inventoryRestockedPublisher.publish(...);   // ← 커밋 전
    }
    return InventoryView.from(inventory);
}
```

발행이 메서드의 마지막 단계라 롤백 여지가 작지만 0은 아니다. `inventory`에는 `@Version`이 있어서, 같은 책의 주문과 겹치면 **커밋 시점 flush에서 낙관적 락 충돌**로 롤백될 수 있다. 그러면 재고는 여전히 0인데 catalog는 재입고 알림 메일을 보낸다.

**할 일**: §1과 같은 `afterCommit` 패턴으로 옮긴다.

---

## 체크리스트 — 새 이벤트를 추가할 때

- [ ] 발행이 `afterCommit`(또는 `@TransactionalEventListener(AFTER_COMMIT)`)에 있는가
- [ ] 한 훅 안의 독립 채널마다 `try/catch`가 따로 있는가
- [ ] 실패 로그에 추적 키(`memberId`, `bookId`, `orderId`)가 있는가
- [ ] 소비 측이 멱등한가 (PK 중복 확인, 처리 이력 테이블, 결정적 키)
- [ ] 유실되면 **조용히 틀리는** 이벤트인가? 그렇다면 Redis가 아니라 SQS + DLQ
- [ ] 발행 주체 파드에 IRSA와 `awssdk:sts`가 있는가 (SQS/SNS/S3 사용 시)
