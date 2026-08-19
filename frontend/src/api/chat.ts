import { apiClient, unwrap } from "./client.ts";
import { mockDelay } from "../mocks/delay.ts";
import { mockIssueChatTicket } from "../mocks/chat.ts";
import type { ApiResponse, ChatTicketResponse } from "./types.ts";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// POST /api/ai/bot/chat/ticket — /ws/ai/chat 접속용 1회성 교환권 발급 (로그인 필요)
export async function issueChatTicket(): Promise<ChatTicketResponse> {
  return USE_MOCK
    ? mockDelay(mockIssueChatTicket(), 200)
    : unwrap(apiClient.post<ApiResponse<ChatTicketResponse>>("/ai/bot/chat/ticket"));
}
