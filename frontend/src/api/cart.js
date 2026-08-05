import { apiClient } from "./client.ts";
import { MOCK_CART_ITEMS, MOCK_CART_BENEFITS } from "../mocks/cart.js";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

export async function fetchCartItems() {
  if (USE_MOCK) return MOCK_CART_ITEMS;
  const { data } = await apiClient.get("/cart/items");
  return data;
}

export async function fetchCartBenefits() {
  if (USE_MOCK) return MOCK_CART_BENEFITS;
  const { data } = await apiClient.get("/cart/benefits");
  return data;
}
