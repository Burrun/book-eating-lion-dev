import apiClient from "./client.js";
import { isLoggedIn } from "./authStorage.js";
import {
  MOCK_CART_BENEFITS,
  MOCK_BOOK_CATALOG,
  mockGetCart,
  mockAddToCart,
  mockUpdateQuantity,
  mockRemoveFromCart,
} from "../mocks/cart.js";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";
const GUEST_CART_KEY = "guest_cart";

// 참고: 기존 cart.js/mypage.js/checkout.js는 ApiResponse<T> envelope({success,message,data,error})을
// 언래핑하지 않고 그대로 반환하는 기존 패턴을 쓴다(이번 스코프 밖, 손대지 않음).
// 이 파일의 실API 분기(로그인 상태)는 `data.data`로 명시적으로 언래핑한다.

function readGuestCart() {
  try {
    const raw = localStorage.getItem(GUEST_CART_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

function writeGuestCart(items) {
  localStorage.setItem(GUEST_CART_KEY, JSON.stringify(items));
}

export function getGuestCartItems() {
  return readGuestCart();
}

export function clearGuestCart() {
  localStorage.removeItem(GUEST_CART_KEY);
}

// 게스트 카트는 book 상세(title/price)를 갖지 않으므로 화면 표시용으로 book API를 조회해 합성한다.
// mock 모드에선 실제 book API 대신 MOCK_BOOK_CATALOG로 대체한다.
async function fetchBookSummary(bookId) {
  if (USE_MOCK) {
    return MOCK_BOOK_CATALOG[bookId] ?? { bookId, title: `도서 #${bookId}`, price: 0, coverImageUrl: null };
  }
  const { data } = await apiClient.get(`/books/${bookId}`);
  const book = data.data;
  return { bookId: book.id, title: book.title, price: book.price, coverImageUrl: book.coverImageUrl };
}

// 게스트 카트는 "한 책당 한 줄만 존재한다"는 전제 하에 서버 PK가 없는 bookId를
// cartItemId 대용으로 재사용한다. 게스트 카트에 같은 책이 여러 줄로 존재할 수 있게 되면
// 이 가정이 깨지므로, addToGuestCart는 항상 기존 줄의 수량을 합산하고 새 줄을 만들지 않는다.
async function getGuestCart() {
  const localItems = readGuestCart();
  const items = await Promise.all(
    localItems.map(async ({ bookId, quantity }) => {
      const book = await fetchBookSummary(bookId);
      return {
        cartItemId: bookId,
        bookId,
        title: book.title,
        coverImageUrl: book.coverImageUrl,
        price: book.price,
        quantity,
      };
    })
  );
  const totalPrice = items.reduce((sum, item) => sum + item.price * item.quantity, 0);
  return { items, totalPrice };
}

function addToGuestCart(bookId, quantity) {
  const items = readGuestCart();
  const existing = items.find((item) => item.bookId === bookId);
  if (existing) {
    existing.quantity += quantity;
  } else {
    items.push({ bookId, quantity });
  }
  writeGuestCart(items);
}

// cartItemId 파라미터는 게스트 모드에서 bookId와 동일한 값이다(getGuestCart 참고).
function updateGuestCartQuantity(cartItemId, quantity) {
  const items = readGuestCart();
  const existing = items.find((item) => item.bookId === cartItemId);
  if (!existing) return;
  existing.quantity = quantity;
  writeGuestCart(items);
}

function removeFromGuestCart(cartItemId) {
  writeGuestCart(readGuestCart().filter((item) => item.bookId !== cartItemId));
}

export async function getCart() {
  if (!isLoggedIn()) return getGuestCart();
  if (USE_MOCK) return mockGetCart();
  // TODO(BOO-23): 백엔드 cart 모듈 구현 대기 중 — 명세는 확정됨(API 명세서 섹션 0), 현재는 mock/게스트만 동작
  const { data } = await apiClient.get("/cart");
  return data.data;
}

export async function addToCart(bookId, quantity) {
  if (!isLoggedIn()) return addToGuestCart(bookId, quantity);
  if (USE_MOCK) return mockAddToCart(bookId, quantity);
  // TODO(BOO-23): 백엔드 cart 모듈 구현 대기 중 — 명세는 확정됨(API 명세서 섹션 0), 현재는 mock/게스트만 동작
  const { data } = await apiClient.post("/cart", { bookId, quantity });
  return data.data;
}

export async function updateQuantity(cartItemId, quantity) {
  if (!isLoggedIn()) return updateGuestCartQuantity(cartItemId, quantity);
  if (USE_MOCK) return mockUpdateQuantity(cartItemId, quantity);
  // TODO(BOO-23): 백엔드 cart 모듈 구현 대기 중 — 명세는 확정됨(API 명세서 섹션 0), 현재는 mock/게스트만 동작
  const { data } = await apiClient.patch(`/cart/${cartItemId}`, { quantity });
  return data.data;
}

export async function removeFromCart(cartItemId) {
  if (!isLoggedIn()) return removeFromGuestCart(cartItemId);
  if (USE_MOCK) return mockRemoveFromCart(cartItemId);
  // TODO(BOO-23): 백엔드 cart 모듈 구현 대기 중 — 명세는 확정됨(API 명세서 섹션 0), 현재는 mock/게스트만 동작
  await apiClient.delete(`/cart/${cartItemId}`);
}

function toDisplayItem(cartItem) {
  return {
    id: cartItem.cartItemId,
    bookId: cartItem.bookId,
    title: cartItem.title,
    price: cartItem.price,
    quantity: cartItem.quantity,
    coverUrl: cartItem.coverImageUrl ?? null,
    shippingFee: cartItem.shippingFee ?? 3000,
    option: cartItem.option,
    condition: cartItem.condition,
  };
}

export async function fetchCartItems() {
  const { items } = await getCart();
  return items.map(toDisplayItem);
}

export async function fetchCartBenefits() {
  if (USE_MOCK) return MOCK_CART_BENEFITS;
  const { data } = await apiClient.get("/cart/benefits");
  return data;
}
