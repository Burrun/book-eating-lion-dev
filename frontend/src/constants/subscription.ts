// 구독권은 별도 상품 타입이 아니라 카탈로그의 도서 한 행이다(db/postgres/90-demo-data.sql).
// 그래서 가격 조회·주문·결제·주문내역이 전부 도서와 같은 경로를 탄다.
//
// 🔴 프론트는 구독을 직접 만들지 않는다. /checkout 으로 보내 결제만 시키고, 결제가 확정되면
// order-service 가 member-service 에 구독 활성화를 요청한다
// (OrderService#activateSubscriptionIfOrdered). 프론트에서 결제 후 subscribe() 를 부르면
// 그 호출이 실패했을 때 돈만 나가고 구독은 없는 상태가 되는데, 되돌릴 방법이 없다.
//
// 아래 세 곳의 값이 어긋나면 조용히 깨진다 — 결제는 되는데 구독이 안 생긴다:
//   1. 이 파일의 SUBSCRIPTION_BOOK_ID
//   2. order-api 의 SUBSCRIPTION_BOOK_ID (GitHub Environment Variables -> ConfigMap)
//   3. 90-demo-data.sql 의 books.book_id
export const SUBSCRIPTION_BOOK_ID = Number(import.meta.env.VITE_SUBSCRIPTION_BOOK_ID ?? 9001);

export const SUBSCRIPTION_TITLE = "책 먹는 사자 정기구독 (월간)";

export const SUBSCRIPTION_PRICE = 9900;

/**
 * Checkout 이 location.state.items 로 기대하는 모양.
 *
 * shippingFee 를 0 으로 준다 — 생략하면 Checkout 이 기본 3000 원을 붙이는데(Checkout.jsx),
 * 구독권에 배송비가 붙으면 화면 합계가 실제 결제 금액과 어긋난다.
 */
export function subscriptionCheckoutItem() {
  return {
    id: `subscription-${SUBSCRIPTION_BOOK_ID}`,
    bookId: SUBSCRIPTION_BOOK_ID,
    title: SUBSCRIPTION_TITLE,
    price: SUBSCRIPTION_PRICE,
    quantity: 1,
    shippingFee: 0,
  };
}
