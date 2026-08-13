export const MOCK_CART_ITEMS = [
  {
    cartItemId: 1,
    bookId: 1,
    title: "자바 ORM 표준 JPA 프로그래밍",
    option: "신간 · 개정판",
    price: 38700,
    shippingFee: 3000,
    quantity: 1,
    coverImageUrl: null,
  },
  {
    cartItemId: 2,
    bookId: 2,
    title: "클린 코드 (Clean Code)",
    option: "베스트셀러",
    price: 29000,
    shippingFee: 3000,
    quantity: 1,
    coverImageUrl: null,
  },
  {
    cartItemId: 3,
    bookId: 3,
    title: "해리 포터와 마법사의 돌",
    price: 12000,
    shippingFee: 3000,
    quantity: 1,
    coverImageUrl: null,
  },
];

export const MOCK_CART_BENEFITS = {
  availableCoupon: { label: "신규 가입 3,000원 할인 쿠폰", discount: 3000 },
};

// 비회원 장바구니 화면 표시용 — 실제로는 GET /api/books/{bookId}에서 내려오는 정보를 대신한다.
export const MOCK_BOOK_CATALOG = {
  1: { bookId: 1, title: "자바 ORM 표준 JPA 프로그래밍", price: 38700, coverImageUrl: null },
  2: { bookId: 2, title: "클린 코드 (Clean Code)", price: 29000, coverImageUrl: null },
  3: { bookId: 3, title: "해리 포터와 마법사의 돌", price: 12000, coverImageUrl: null },
};

// 로그인 상태의 mock "서버" 장바구니 — 백엔드 cart 모듈이 없어 메모리에서 흉내낸다.
// 페이지를 새로고침하면 MOCK_CART_ITEMS 초기값으로 리셋된다.
let mockServerCart = MOCK_CART_ITEMS.map((item) => ({ ...item }));
let nextMockCartItemId = mockServerCart.length + 1;

// CartItemView 는 subtotal(price*quantity)을 서버가 계산해서 내려준다. mock도 동일하게 맞춘다.
function withSubtotal(item) {
  return { ...item, subtotal: item.price * item.quantity };
}

export function mockGetCart() {
  const items = mockServerCart.map(withSubtotal);
  const totalQuantity = items.reduce((sum, item) => sum + item.quantity, 0);
  const totalPrice = items.reduce((sum, item) => sum + item.subtotal, 0);
  return { items, totalQuantity, totalPrice };
}

export function mockAddToCart(bookId, quantity) {
  const existing = mockServerCart.find((item) => item.bookId === bookId);
  if (existing) {
    existing.quantity += quantity;
    return withSubtotal(existing);
  }
  const book = MOCK_BOOK_CATALOG[bookId] ?? {
    bookId,
    title: `도서 #${bookId}`,
    price: 0,
    coverImageUrl: null,
  };
  const item = {
    cartItemId: nextMockCartItemId++,
    bookId,
    title: book.title,
    coverImageUrl: book.coverImageUrl,
    price: book.price,
    quantity,
  };
  mockServerCart.push(item);
  return withSubtotal(item);
}

export function mockUpdateQuantity(cartItemId, quantity) {
  const found = mockServerCart.find((item) => item.cartItemId === cartItemId);
  if (!found) throw new Error(`mock cart item not found: ${cartItemId}`);
  found.quantity = quantity;
  return withSubtotal(found);
}

// 실제 DELETE /api/cart/{cartItemId} 가 204 No Content(반환값 없음)라 mock도 아무것도 반환하지 않는다.
export function mockRemoveFromCart(cartItemId) {
  mockServerCart = mockServerCart.filter((item) => item.cartItemId !== cartItemId);
}
