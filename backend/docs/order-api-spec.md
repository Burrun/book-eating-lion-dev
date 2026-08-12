# 📦 Order Service API Specification

> `modules/order` 소스 코드(Controller/DTO/Domain/Enum/ExceptionHandler)를 직접 파싱해 역추출한 명세서입니다.
> 담당 도메인: 🛒 장바구니, 🎟️ 쿠폰, 📦 주문, 💳 결제/승인/취소/반품/환불.
> 작성 기준 커밋: `main` 최신 (`fix/test` 브랜치 기준 `d27660b`).

---

## 1. 공통 규격

- **Base URL**: `/api` (배포 단위: `apps/order-api`)
- **인증 방식**: `Authorization: Bearer <accessToken>` (AWS Cognito 발급 JWT, OAuth2 Resource Server 검증)
  - `member_id` 클레임을 서버가 직접 읽어 소유권 판단에 사용합니다(`SecurityUtils.currentMemberId()`). 이 클레임은 member-service가 Cognito PreTokenGeneration 단계에서 주입하며, 없으면 `500 Internal Server Error`(`IllegalStateException`)가 발생합니다.
  - `/api/cart/**`, `/api/coupons/**`, `/api/orders/**`, `/api/payments/**` 전 엔드포인트가 인증 필요(`SecurityConfig.authorizeHttpRequests`).
- **공통 응답 포맷** (`com.bookeatinglion.common.dto.ApiResponse<T>`):

  ```json
  {
    "success": true,
    "message": "SUCCESS",
    "data": { },
    "error": null
  }
  ```

  | 필드 | 타입 | 설명 |
  |---|---|---|
  | `success` | boolean | 처리 성공 여부 |
  | `message` | string | 성공 시 `"SUCCESS"` 고정(현재 컨트롤러는 커스텀 메시지를 넘기지 않음), 실패 시 예외 메시지 |
  | `data` | T \| null | 성공 시 응답 페이로드, 실패 시 `null` |
  | `error` | `ErrorDetail` \| null | 실패 시 `{ code, message }`, 성공 시 `null` |

  실패 응답 예시:
  ```json
  {
    "success": false,
    "message": "존재하지 않는 주문입니다: 123",
    "data": null,
    "error": { "code": "ORDER_NOT_FOUND", "message": "존재하지 않는 주문입니다: 123" }
  }
  ```

- **검증 실패(`@Valid` 위반) 응답**: `MethodArgumentNotValidException`을 각 도메인 `*ExceptionHandler`가 잡아 `400 Bad Request` + 첫 번째 필드 오류(`필드명: 메시지`)를 `message`/`error.message`에 담아 반환합니다(`GlobalErrorHelper.toValidationResponse`). 에러 코드는 도메인별 `INVALID_REQUEST`.

---

## 2. Enum 정의 (실제 코드 값 — 예시 스펙과 다를 수 있음)

> ⚠️ 본 문서 하단 "6. 참고" 참조: 요청서의 Output Example에 적힌 `CARD`/`KAKAOPAY`는 실제 코드값이 아닙니다. 실제 값은 아래와 같습니다.

| Enum | 값 | 출처 |
|---|---|---|
| `PaymentMethod` | `VIRTUAL_CARD`, `KAKAO_PAY` | `payment/domain/PaymentMethod.java` |
| `OrderStatus` | `PENDING_PAYMENT`, `PAID`, `CANCELLED`, `RETURN_REQUESTED`, `REFUNDED` | `order/domain/OrderStatus.java` |
| `PaymentStatus` | `READY`, `APPROVED`, `DECLINED`, `CANCELLED`, `REFUNDED` | `payment/domain/PaymentStatus.java` |

### OrderStatus 상태 전이

```
PENDING_PAYMENT --(CARD: createOrder 내 즉시 승인)--> PAID
PENDING_PAYMENT --(KAKAO_PAY: POST /api/payments/kakao/approve)--> PAID
PAID --(POST /api/orders/{id}/cancel)--> CANCELLED
PAID --(POST /api/orders/{id}/return)--> RETURN_REQUESTED
RETURN_REQUESTED --(POST /api/orders/{id}/refund)--> REFUNDED
```

### PaymentStatus 상태 전이

```
VIRTUAL_CARD: (즉시) APPROVED --cancel/refund--> CANCELLED / REFUNDED
KAKAO_PAY:    READY --approve--> APPROVED --cancel/refund--> CANCELLED / REFUNDED
```
`DECLINED`는 저장되지 않는 상태입니다 — `PaymentService.approveCard()`가 카드 한도 거절 시 `PaymentDeclinedException`을 던져 트랜잭션 전체를 롤백하므로, `Payment` 로우 자체가 생성되지 않습니다.

---

## 3. API 상세 명세

### 3.1 장바구니 (Cart) — `CartController`

#### [GET] `/api/cart`
- **설명**: 내 장바구니 목록 + 총 수량/총 금액 조회. 도서 제목/가격/이미지는 catalog-service를 동기 호출해 채웁니다(`CatalogClient`, 장애 시 `CatalogClientFallback`으로 degrade).
- **인증**: 필수
- **Path/Query 파라미터**: 없음
- **Response `data`**: `CartResponse`

  | 필드 | 타입 | 설명 |
  |---|---|---|
  | `items` | `CartItemView[]` | 장바구니 항목 목록 |
  | `totalQuantity` | int | 항목 수량 합계 |
  | `totalPrice` | long | 항목 소계(`price*quantity`) 합계 |

  `CartItemView`:

  | 필드 | 타입 | 설명 |
  |---|---|---|
  | `cartItemId` | Long | 장바구니 항목 PK |
  | `bookId` | Long | 도서 ID |
  | `title` | String | 도서명 (catalog 조회) |
  | `price` | int | 단가 (catalog 조회) |
  | `coverImageUrl` | String | 표지 이미지 URL (catalog 조회) |
  | `quantity` | int | 수량 |
  | `subtotal` | long | `price * quantity` |

- **상태 코드**: `200 OK`

#### [POST] `/api/cart`
- **설명**: 장바구니 담기. 이미 담긴 도서(`memberId`+`bookId` 유니크)면 수량을 누적, 아니면 새 항목 생성.
- **인증**: 필수
- **Request Body**: `AddCartItemRequest`

  | 필드 | 타입 | 필수 | 제약 |
  |---|---|---|---|
  | `bookId` | Long | ✅ | `@NotNull` |
  | `quantity` | Integer | ❌ | `@Min(1)`, 생략 시 컨트롤러가 `1`로 기본값 적용 |

- **Response `data`**: `CartItemView` (위와 동일 스키마)
- **상태 코드**: `200 OK` / `400 Bad Request`(quantity < 1, bookId 누락)

#### [PATCH] `/api/cart/{cartItemId}`
- **설명**: 장바구니 항목 수량 변경(절대값으로 덮어씀, 누적 아님).
- **인증**: 필수
- **Path Variable**: `cartItemId` (Long)
- **Request Body**: `ChangeCartItemQuantityRequest`

  | 필드 | 타입 | 필수 | 제약 |
  |---|---|---|---|
  | `quantity` | int | ✅ | `@Min(1)` |

- **Response `data`**: `CartItemView`
- **상태 코드**: `200 OK` / `400 Bad Request`(수량 0 이하) / `403 Forbidden`(타인 소유) / `404 Not Found`(항목 없음)

#### [DELETE] `/api/cart/{cartItemId}`
- **설명**: 장바구니 항목 삭제.
- **인증**: 필수
- **Path Variable**: `cartItemId` (Long)
- **Response**: 본문 없음
- **상태 코드**: `204 No Content` / `403 Forbidden` / `404 Not Found`

> 💡 별도의 `/api/cart/checkout` 엔드포인트는 존재하지 않습니다. 장바구니 → 주문 전환은 `POST /api/orders`가 전담하며, 결제 확정 시점(CARD는 즉시, KAKAO_PAY는 승인 시)에 서버가 주문에 포함된 도서를 장바구니에서 자동 삭제합니다(`cartItemRepository.deleteByMemberIdAndBookIdIn`).

**Cart 에러 코드** (`CartErrorCode`):

| code | HTTP Status |
|---|---|
| `CART_ITEM_NOT_FOUND` | 404 |
| `UNAUTHORIZED_CART_ACCESS` | 403 |
| `INVALID_REQUEST` | 400 |

---

### 3.2 쿠폰 (Coupon) — `CouponController`

#### [GET] `/api/coupons/me`
- **설명**: 내가 보유한(미사용) 쿠폰 목록 조회.
- **인증**: 필수
- **Response `data`**: `MemberCouponView[]`

  | 필드 | 타입 | 설명 |
  |---|---|---|
  | `memberCouponId` | Long | 보유 쿠폰 PK (주문 시 `memberCouponId`로 사용) |
  | `couponId` | Long | 원본 쿠폰 정의 PK |
  | `couponCode` | String | 쿠폰 코드 |
  | `couponName` | String | 쿠폰명 |
  | `discountAmount` | int | 할인 금액 |
  | `minimumOrderAmount` | int | 최소 주문 금액 |
  | `expiresAt` | LocalDateTime | 만료 일시 |

- **상태 코드**: `200 OK`

#### [POST] `/api/coupons/register`
- **설명**: 쿠폰 코드 등록(발급). 동시 등록 경합 시 DB UNIQUE 제약 위반을 `COUPON_ALREADY_ISSUED`로 변환.
- **인증**: 필수
- **Request Body**: `RegisterCouponRequest`

  | 필드 | 타입 | 필수 | 제약 |
  |---|---|---|---|
  | `code` | String | ✅ | `@NotBlank` |

- **Response `data`**: `MemberCouponView` (위와 동일 스키마)
- **상태 코드**: `200 OK` / `400 Bad Request`(코드 공백, 만료 쿠폰) / `404 Not Found`(존재하지 않는 코드) / `409 Conflict`(이미 보유)

**Coupon 에러 코드** (`CouponErrorCode`):

| code | HTTP Status |
|---|---|
| `COUPON_NOT_FOUND` | 404 |
| `COUPON_EXPIRED` | 400 |
| `COUPON_ALREADY_ISSUED` | 409 |
| `COUPON_ALREADY_USED` | 409 |
| `INVALID_REQUEST` | 400 |

---

### 3.3 주문 (Order) — `OrderController`

#### [POST] `/api/orders`
- **설명**: 주문 생성. `paymentMethod`에 따라 동작이 분기됩니다.
  - **`VIRTUAL_CARD`**: 단일 트랜잭션 내에서 카드 한도 즉시 차감(member-service 동기 호출) → 승인 성공 시 `PAID` 확정 → 쿠폰 즉시 사용확정 → 재고 차감 → 장바구니 자동 정제까지 한 번에 완료. 승인 거절 시 `PaymentDeclinedException`으로 전체 롤백(주문 자체가 생성되지 않음).
  - **`KAKAO_PAY`**: 카카오페이 `ready` API만 호출하고 주문은 `PENDING_PAYMENT`로 남습니다. 재고는 아직 차감하지 않고, 쿠폰도 사용확정하지 않습니다(주문에 `pendingMemberCouponId`로 의도만 기록). 응답에 `nextRedirectUrl`(카카오페이 결제 페이지 URL)이 포함됩니다.
- **인증**: 필수
- **Request Body**: `CreateOrderRequest`

  | 필드 | 타입 | 필수 | 제약 |
  |---|---|---|---|
  | `items` | `OrderItemRequest[]` | ✅ | `@NotEmpty`, 각 원소 `@Valid` |
  | `memberCouponId` | Long | ❌ | 미지정 시 쿠폰 미적용 |
  | `recipient` | `Recipient` | ✅ | `@NotNull @Valid` |
  | `paymentMethod` | `PaymentMethod` | ✅ | `@NotNull` (`VIRTUAL_CARD` \| `KAKAO_PAY`) |
  | `cardId` | Long | 조건부 | `paymentMethod=VIRTUAL_CARD`일 때 필수(서비스 레벨 검증, 없으면 `400 INVALID_REQUEST`) |

  `OrderItemRequest`:

  | 필드 | 타입 | 필수 | 제약 |
  |---|---|---|---|
  | `bookId` | Long | ✅ | `@NotNull` |
  | `quantity` | int | ✅ | `@Min(1)` |

  `Recipient`:

  | 필드 | 타입 | 필수 | 제약 |
  |---|---|---|---|
  | `name` | String | ✅ | `@NotBlank` |
  | `phone` | String | ✅ | `@NotBlank` |
  | `postalCode` | String | ✅ | `@NotBlank` |
  | `address` | String | ✅ | `@NotBlank` |

- **Response `data`**: `OrderResponse` (아래 3.4절 공통 스키마 참고)
- **상태 코드**: `200 OK` / `400 Bad Request`(재고부족·요청형식오류·잘못된 쿠폰) / `402 Payment Required`(카드 결제 거절) / `404 Not Found`(존재하지 않는 보유쿠폰) / `403 Forbidden`(타인 쿠폰) / `503 Service Unavailable`(도서 가격 조회 실패, 재고 락 획득 실패)

#### [GET] `/api/orders/{orderId}`
- **설명**: 내 주문 상세 조회.
- **인증**: 필수
- **Path Variable**: `orderId` (Long)
- **Response `data`**: `OrderResponse`
- **상태 코드**: `200 OK` / `403 Forbidden`(타인 주문) / `404 Not Found`

#### [POST] `/api/orders/{orderId}/cancel`
- **설명**: `PAID` 주문 취소. 결제수단별 자금 복구(카드 한도 복구 / 카카오페이 취소 API), 재고 원복, 쿠폰 사용 원복을 단일 트랜잭션으로 처리. 자금 복구 실패 시 전체 롤백.
- **인증**: 필수
- **Path Variable**: `orderId` (Long)
- **Response `data`**: `OrderResponse` (`orderStatus: CANCELLED`)
- **상태 코드**: `200 OK` / `403 Forbidden` / `404 Not Found` / `409 Conflict`(`PAID`가 아님) / `409 Conflict`(카드 한도 복구 실패) / `503 Service Unavailable`(카카오페이 API 오류)

#### [POST] `/api/orders/{orderId}/return`
- **설명**: 배송 완료 후 반품 신청 접수. 사전 취소(`cancel`)와 달리 이 시점엔 재고/쿠폰/결제를 건드리지 않고 상태만 `RETURN_REQUESTED`로 전환 + 사유 저장. 실제 환불은 `refund`에서 별도 처리.
- **인증**: 필수
- **Path Variable**: `orderId` (Long)
- **Request Body**: `RequestReturnRequest`

  | 필드 | 타입 | 필수 | 제약 |
  |---|---|---|---|
  | `reason` | String | ✅ | `@NotBlank` |

- **Response `data`**: `OrderResponse` (`orderStatus: RETURN_REQUESTED`, `returnReason` 포함)
- **상태 코드**: `200 OK` / `400 Bad Request`(사유 공백) / `403 Forbidden` / `404 Not Found` / `409 Conflict`(`PAID`가 아님)

#### [POST] `/api/orders/{orderId}/refund`
- **설명**: `RETURN_REQUESTED` 주문의 환불 완료 처리. 자금 복구 방식은 `cancel`과 동일(카드 한도 복구 / 카카오페이 취소 API)하되 결제 최종 상태가 `REFUNDED`로 남는다는 점이 다름. 재고 복구 + 쿠폰 원복까지 단일 트랜잭션으로 원자적 처리 — 자금 복구 실패 시 전체 롤백.
- **인증**: 필수
- **Path Variable**: `orderId` (Long)
- **Response `data`**: `OrderResponse` (`orderStatus: REFUNDED`)
- **상태 코드**: `200 OK` / `403 Forbidden` / `404 Not Found` / `409 Conflict`(`RETURN_REQUESTED`가 아님) / `409 Conflict`(카드 한도 복구 실패) / `503 Service Unavailable`(카카오페이 API 오류)

---

### 3.4 결제/승인 (Payment) — `PaymentController`

카카오페이 2단계 흐름 중 **approve 전용** 컨트롤러입니다. **ready는 `POST /api/orders` 내부에서 일어나며 별도 엔드포인트가 없습니다.**

#### [POST] `/api/payments/kakao/approve`
- **설명**: 카카오페이 결제 승인 2단계. 재고를 재검증(통과해야만 카카오 승인 API를 호출) → 승인 성공 시 주문 `PAID` 전환 → 쿠폰 사용확정 → 재고 차감 → 장바구니 정제까지 단일 트랜잭션으로 완료. 재고가 부족하면 카카오 승인 API 자체를 호출하지 않습니다(사용자 실결제 발생 안 함).
- **인증**: 필수
- **Request Body**: `KakaoApproveRequest`

  | 필드 | 타입 | 필수 | 제약 |
  |---|---|---|---|
  | `orderId` | Long | ✅ | `@NotNull` |
  | `pgToken` | String | ✅ | `@NotBlank` (카카오페이 리다이렉트 콜백에서 전달받은 토큰) |

- **Response `data`**: `OrderResponse` (`orderStatus: PAID`)
- **상태 코드**: `200 OK` / `400 Bad Request`(`pgToken` 공백, 승인 시점 재고부족) / `403 Forbidden`(타인 주문) / `404 Not Found`(존재하지 않는 주문) / `409 Conflict`(이미 처리된 주문 — `PENDING_PAYMENT`가 아니거나 `Payment`가 `READY`가 아님) / `503 Service Unavailable`(카카오페이 API 오류)

#### 공통 응답 — `OrderResponse` (createOrder / getOrder / cancelOrder / requestReturn / refundOrder / approveKakaoPayment 전체 공통)

| 필드 | 타입 | 설명 |
|---|---|---|
| `orderId` | Long | 주문 PK |
| `orderStatus` | `OrderStatus` | 현재 상태 |
| `recipient` | `Recipient` | 수령인 정보(주문 생성 시 스냅샷) |
| `totalAmount` | int | 최종 결제 금액(쿠폰 할인 반영, `Math.max(0, subtotal-discount)`) |
| `items` | `OrderItemView[]` | 주문 항목 스냅샷 |
| `payment` | `PaymentView` \| null | 결제 정보(주문 생성 실패 등으로 결제 자체가 없으면 null) |
| `nextRedirectUrl` | String \| null | **KAKAO_PAY `ready` 직후 응답에서만 값이 채워짐.** 그 외 모든 응답(CARD 완료, 상세조회, 취소, 반품, 환불, 카카오 승인 이후)은 항상 `null` |
| `returnReason` | String \| null | 반품 사유(반품 신청 이후에만 값 존재) |

`OrderItemView`:

| 필드 | 타입 | 설명 |
|---|---|---|
| `orderItemId` | Long | 주문 항목 PK |
| `bookId` | Long | 도서 ID |
| `bookTitle` | String | 주문 시점 도서명 스냅샷 |
| `quantity` | int | 수량 |
| `unitPrice` | int | 주문 시점 단가 스냅샷 |

`PaymentView`:

| 필드 | 타입 | 설명 |
|---|---|---|
| `paymentId` | Long | 결제 PK |
| `paymentMethod` | `PaymentMethod` | `VIRTUAL_CARD` \| `KAKAO_PAY` |
| `amount` | int | 결제 금액 |
| `paymentStatus` | `PaymentStatus` | `READY`\|`APPROVED`\|`DECLINED`\|`CANCELLED`\|`REFUNDED` |
| `approvalNumber` | String \| null | 승인번호(READY 상태에선 null) |
| `pgTid` | String \| null | 카카오페이 거래 ID(CARD 결제는 null) |

**Order/Payment 에러 코드** (`OrderErrorCode` — `OrderController`, `PaymentController` 공용):

| code | HTTP Status | 발생 조건 |
|---|---|---|
| `ORDER_NOT_FOUND` | 404 | 존재하지 않는 `orderId` |
| `UNAUTHORIZED_ORDER_ACCESS` | 403 | 본인 소유가 아닌 주문 접근 |
| `OUT_OF_STOCK` | 400 | 주문 생성/카카오 승인 시점 재고 부족 |
| `BOOK_PRICE_UNAVAILABLE` | 503 | catalog-service 응답 degrade(가격 신뢰 불가) |
| `ORDER_COUPON_NOT_FOUND` | 404 | 존재하지 않는 보유 쿠폰(`memberCouponId`) |
| `UNAUTHORIZED_COUPON_ACCESS` | 403 | 본인 소유가 아닌 쿠폰 사용 시도 |
| `INVALID_COUPON` | 400 | 이미 사용/만료/최소주문금액 미달 쿠폰 |
| `INVALID_REQUEST` | 400 | `@Valid` 검증 실패, `VIRTUAL_CARD`인데 `cardId` 누락 |
| `ORDER_CANNOT_BE_CANCELLED` | 409 | `PAID`가 아닌 주문 취소 시도 |
| `PAYMENT_DECLINED` | 402 | 가상카드 한도 부족/비활성 카드 결제 거절 |
| `CARD_RESTORE_FAILED` | 409 | 취소/환불 시 카드 한도 복구 실패(member-service 응답 거절) |
| `LOCK_ACQUISITION_FAILED` | 503 | 재고 분산락(Redisson) 획득 실패/중단 |
| `KAKAOPAY_API_ERROR` | 503 | 카카오페이 실 API 통신 오류 |
| `PAYMENT_ALREADY_PROCESSED` | 409 | 이미 승인/처리된 주문에 재승인 요청 |
| `ORDER_CANNOT_BE_RETURNED` | 409 | `PAID`가 아닌 주문 반품 신청 시도 |
| `ORDER_CANNOT_BE_REFUNDED` | 409 | `RETURN_REQUESTED`가 아닌 주문 환불 시도 |

---

## 4. 엔드포인트 총괄표

| Domain | Method | Path | 인증 | 성공 코드 |
|---|---|---|---|---|
| Cart | GET | `/api/cart` | ✅ | 200 |
| Cart | POST | `/api/cart` | ✅ | 200 |
| Cart | PATCH | `/api/cart/{cartItemId}` | ✅ | 200 |
| Cart | DELETE | `/api/cart/{cartItemId}` | ✅ | 204 |
| Coupon | GET | `/api/coupons/me` | ✅ | 200 |
| Coupon | POST | `/api/coupons/register` | ✅ | 200 |
| Order | POST | `/api/orders` | ✅ | 200 |
| Order | GET | `/api/orders/{orderId}` | ✅ | 200 |
| Order | POST | `/api/orders/{orderId}/cancel` | ✅ | 200 |
| Order | POST | `/api/orders/{orderId}/return` | ✅ | 200 |
| Order | POST | `/api/orders/{orderId}/refund` | ✅ | 200 |
| Payment | POST | `/api/payments/kakao/approve` | ✅ | 200 |

모든 성공 응답은 `HTTP 200`이며(DELETE 제외), 생성 성공 시에도 별도의 `201 Created`를 사용하지 않습니다 — 컨트롤러가 `ApiResponse.success(data)`를 그대로 반환하기 때문입니다(`@ResponseStatus` 미지정 → 기본 200).

---

## 5. 소스 스캔 근거 파일

```
modules/order/src/main/java/com/bookeatinglion/order/
├── cart/controller/CartController.java, CartExceptionHandler.java
├── cart/dto/{AddCartItemRequest,ChangeCartItemQuantityRequest,CartItemView,CartResponse}.java
├── cart/exception/CartErrorCode.java
├── coupon/controller/CouponController.java, CouponExceptionHandler.java
├── coupon/dto/{RegisterCouponRequest,MemberCouponView}.java
├── coupon/exception/CouponErrorCode.java
├── order/controller/OrderController.java, OrderExceptionHandler.java
├── order/dto/{CreateOrderRequest,OrderItemRequest,Recipient,RequestReturnRequest,OrderResponse,OrderItemView}.java
├── order/domain/OrderStatus.java
├── order/exception/OrderErrorCode.java (+ 개별 예외 클래스 전체)
├── payment/controller/PaymentController.java
├── payment/dto/{KakaoApproveRequest,PaymentView}.java
├── payment/domain/{PaymentMethod,PaymentStatus}.java
└── payment/exception/{CardRestoreFailedException,KakaoPayApiException,PaymentDeclinedException}.java

modules/common/src/main/java/com/bookeatinglion/common/
├── dto/{ApiResponse,ErrorDetail}.java
├── exception/GlobalErrorHelper.java
└── security/SecurityUtils.java
```

## 6. 참고 (요청서 예시와의 차이)

작업 지시서의 Output Example에 제시된 Enum 값(`PaymentMethod: CARD, KAKAOPAY`, `PaymentStatus: READY, APPROVED, CANCELLED, REFUNDED`)은 실제 소스 코드 값과 다릅니다. 본 문서는 소스 코드를 단일 진실 공급원으로 삼아 실제 값(`VIRTUAL_CARD`/`KAKAO_PAY`, `PaymentStatus`에 `DECLINED` 추가)을 반영했습니다. 프런트엔드/타 서비스 연동 시 반드시 본 문서(§2)의 실제 값을 기준으로 삼아 주세요.
