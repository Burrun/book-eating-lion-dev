// 상담 채팅 UI 전용 도메인 타입.
// 서버 프레임 스펙은 backend/contracts/ai-v1.yaml(/ws/ai/chat), ChatWebSocketHandler를 따른다.

export type ChatRole = "USER" | "BOT" | "AGENT" | "SYSTEM";

export type ChatRoomState = "BOT" | "WAITING" | "LIVE" | "CLOSED";

export type ConnectionStatus = "CONNECTING" | "CONNECTED" | "DISCONNECTED";

// 대화 한 줄. ChatMessage.java(seq/role/nickname/text/at)와 대응한다.
// seq로 재접속 시 겹쳐 받는 메시지를 중복 제거한다.
export interface ChatMessage {
  seq: number;
  role: ChatRole;
  nickname: string | null;
  text: string;
  at: string;
}

// 클라이언트 -> 서버 프레임. ChatWebSocketHandler.Frame(type/text/roomId)과 대응한다.
// roomId는 상담사 전용(CLAIM/SAY)이라 1:1 사용자 화면에서는 쓰지 않는다.
export type ChatOutgoingFrame =
  | { type: "ASK"; text: string }
  | { type: "SAY"; text: string }
  | { type: "ESCALATE" }
  | { type: "CLOSE" };

// 실제 WebSocket과 목업 소켓이 공통으로 구현하는 최소 인터페이스.
export interface ChatTransport {
  send(data: string): void;
  close(): void;
  onopen: (() => void) | null;
  onmessage: ((data: string) => void) | null;
  onclose: (() => void) | null;
  onerror: ((error: unknown) => void) | null;
}
