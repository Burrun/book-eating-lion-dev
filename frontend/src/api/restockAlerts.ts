import { apiClient, unwrap } from "./client.ts";
import type { ApiResponse, RestockAlertResponse, RestockAlertStatus } from "./types.ts";

export function subscribeRestockAlert(bookId: number | string): Promise<RestockAlertResponse> {
  return unwrap(
    apiClient.post<ApiResponse<RestockAlertResponse>>(`/catalog/books/${bookId}/restock-alert`),
  );
}

export function getMyRestockAlerts(status?: RestockAlertStatus): Promise<RestockAlertResponse[]> {
  return unwrap(
    apiClient.get<ApiResponse<RestockAlertResponse[]>>("/catalog/restock-alerts/me", {
      params: status ? { status } : undefined,
    }),
  );
}

export async function cancelRestockAlert(bookId: number | string): Promise<void> {
  await unwrap(apiClient.delete<ApiResponse<void>>(`/catalog/books/${bookId}/restock-alert`));
}
