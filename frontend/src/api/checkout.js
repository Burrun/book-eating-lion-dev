import apiClient from "./client.js";
import { MOCK_CHECKOUT_SUMMARY } from "../mocks/checkout.js";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

export async function fetchCheckoutSummary() {
  if (USE_MOCK) return MOCK_CHECKOUT_SUMMARY;
  const { data } = await apiClient.get("/checkout/summary");
  return data;
}
