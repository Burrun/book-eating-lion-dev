import apiClient from "./client.js";
import { mockLogin } from "../mocks/auth.js";
import { getCart, addToCart, updateQuantity } from "./cart.js";

// 참고: 기존 mypage.js/checkout.js/cart.js는 apiClient 응답(ApiResponse<T> envelope)을
// 언래핑하지 않고 그대로 반환하는 기존 패턴을 쓴다(이번 스코프 밖, 손대지 않음).
// 이 파일은 토큰 정합성이 중요해 실API 분기에서 `data.data`로 명시적으로 언래핑한다.
const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

export async function login(email, password) {
  if (USE_MOCK) return mockLogin(email, password);
  const { data } = await apiClient.post("/auth/login", { email, password });
  return data.data;
}

export async function mergeCart(localCartItems) {
  if (!localCartItems || localCartItems.length === 0) return;

  const serverCart = await getCart();
  const serverItemsByBookId = new Map(serverCart.items.map((item) => [item.bookId, item]));

  for (const localItem of localCartItems) {
    const existing = serverItemsByBookId.get(localItem.bookId);
    if (existing) {
      // TODO(BOO-23): 병합 시 동일 도서 수량 처리 정책이 명세서에 없어 우선 합산으로 구현.
      await updateQuantity(existing.cartItemId, existing.quantity + localItem.quantity);
    } else {
      await addToCart(localItem.bookId, localItem.quantity);
    }
  }
}
