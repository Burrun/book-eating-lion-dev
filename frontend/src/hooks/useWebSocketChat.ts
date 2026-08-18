import { useCallback, useEffect, useRef, useState } from "react";
import { issueChatTicket } from "../api/chat.ts";
import { createMockChatSocket } from "../mocks/chat.ts";
import type {
  ChatMessage,
  ChatOutgoingFrame,
  ChatRoomState,
  ChatTransport,
  ConnectionStatus,
} from "../types/chat.ts";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";
const MAX_RECONNECT_ATTEMPTS = 3;

// 실 WebSocket을 ChatTransport(문자열 콜백 기반) 모양으로 맞춘다 — 목업 소켓과
// 같은 인터페이스를 쓰면 이 훅의 나머지 로직이 실 서버/목업을 구분할 필요가 없다.
function wrapNativeSocket(ws: WebSocket): ChatTransport {
  const transport: ChatTransport = {
    send: (data) => ws.send(data),
    close: () => ws.close(),
    onopen: null,
    onmessage: null,
    onclose: null,
    onerror: null,
  };
  ws.onopen = () => transport.onopen?.();
  ws.onmessage = (event) => transport.onmessage?.(String(event.data));
  ws.onclose = () => transport.onclose?.();
  ws.onerror = (event) => transport.onerror?.(event);
  return transport;
}

function resolveWsUrl(ticket: string): string {
  const configured = import.meta.env.VITE_WS_BASE_URL as string | undefined;
  const base = configured ?? `${window.location.protocol === "https:" ? "wss:" : "ws:"}//${window.location.host}`;
  return `${base}/ws/ai/chat?ticket=${encodeURIComponent(ticket)}`;
}

interface UseWebSocketChatOptions {
  /** 패널이 열려 있을 때만 연결한다 — 닫혀 있으면 소켓을 만들지 않는다. */
  enabled: boolean;
}

interface ServerEnvelope {
  success: boolean;
  data?: { type?: string; state?: ChatRoomState; messages?: ChatMessage[] };
  error?: { code: string; message: string } | null;
}

export function useWebSocketChat({ enabled }: UseWebSocketChatOptions) {
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>("DISCONNECTED");
  const [chatState, setChatState] = useState<ChatRoomState>("BOT");
  const [messages, setMessages] = useState<ChatMessage[]>([]);

  const transportRef = useRef<ChatTransport | null>(null);
  const seenSeqRef = useRef<Set<number>>(new Set());
  const reconnectAttemptsRef = useRef(0);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const closedByUserRef = useRef(false);

  const appendMessage = useCallback((message: ChatMessage) => {
    setMessages((prev) => {
      // 재접속 시 "구독 먼저, 전사 조회 나중" 순서라 겹쳐 받을 수 있다 — seq로 중복 제거한다.
      if (seenSeqRef.current.has(message.seq)) return prev;
      seenSeqRef.current.add(message.seq);
      return [...prev, message].sort((a, b) => a.seq - b.seq);
    });
  }, []);

  const handleRawFrame = useCallback(
    (raw: string) => {
      let frame: ServerEnvelope | (ChatMessage & { type: string });
      try {
        frame = JSON.parse(raw);
      } catch {
        return;
      }

      // 두 종류의 프레임이 같은 채널로 온다: JOINED/NO_AGENT 등은 ApiResponse 봉투에
      // 담겨 오고(success 필드 있음), MESSAGE는 Redis 팬아웃이 원본 그대로 보낸다.
      if ("success" in frame) {
        if (!frame.success) {
          console.warn("[chat] server error", frame.error);
          return;
        }
        const data = frame.data;
        if (data?.type === "JOINED") {
          if (data.state) setChatState(data.state);
          data.messages?.forEach(appendMessage);
        } else if (data?.type === "NO_AGENT") {
          setChatState("CLOSED");
        }
        return;
      }

      if (frame.type === "MESSAGE") {
        appendMessage(frame);
        if (frame.role === "AGENT") setChatState("LIVE");
      }
    },
    [appendMessage],
  );

  const disconnect = useCallback(() => {
    closedByUserRef.current = true;
    if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
    transportRef.current?.close();
    transportRef.current = null;
  }, []);

  const connect = useCallback(async () => {
    closedByUserRef.current = false;
    setConnectionStatus("CONNECTING");
    try {
      const { ticket } = await issueChatTicket();
      // 대기하는 동안 패널이 닫혔을 수 있다 — 이미 취소된 연결을 뒤늦게 이어가지 않는다.
      if (closedByUserRef.current) return;

      const transport = USE_MOCK ? createMockChatSocket() : wrapNativeSocket(new WebSocket(resolveWsUrl(ticket)));
      transportRef.current = transport;

      transport.onopen = () => {
        reconnectAttemptsRef.current = 0;
        setConnectionStatus("CONNECTED");
      };
      transport.onmessage = handleRawFrame;
      transport.onclose = () => {
        setConnectionStatus("DISCONNECTED");
        if (closedByUserRef.current) return;
        if (reconnectAttemptsRef.current >= MAX_RECONNECT_ATTEMPTS) return;
        reconnectAttemptsRef.current += 1;
        const delay = Math.min(1000 * 2 ** reconnectAttemptsRef.current, 8000);
        // connect의 의존성(handleRawFrame -> appendMessage)이 전부 빈 배열로 고정돼 있어
        // connect 자체가 컴포넌트 생애주기 동안 재생성되지 않는다 — 재귀 참조가 안전하다.
        // eslint-disable-next-line react-hooks/immutability
        reconnectTimerRef.current = setTimeout(connect, delay);
      };
    } catch {
      setConnectionStatus("DISCONNECTED");
    }
  }, [handleRawFrame]);

  useEffect(() => {
    if (!enabled) return;
    reconnectAttemptsRef.current = 0;
    // effect 본문에서 곧장 부르지 않고 마이크로태스크로 미룬다 — connect는 상태를 동기적으로
    // 바꾸는데, effect 본문에서 곧바로 그런 함수를 부르면 렌더 중 연쇄 렌더 경고에 걸린다.
    queueMicrotask(() => connect());
    return disconnect;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled]);

  const sendFrame = useCallback((frame: ChatOutgoingFrame) => {
    if (!transportRef.current) return;
    transportRef.current.send(JSON.stringify(frame));
  }, []);

  const sendMessage = useCallback(
    (text: string) => {
      const trimmed = text.trim();
      if (!trimmed || connectionStatus !== "CONNECTED" || chatState === "CLOSED") return;
      sendFrame(chatState === "LIVE" ? { type: "SAY", text: trimmed } : { type: "ASK", text: trimmed });
    },
    [chatState, connectionStatus, sendFrame],
  );

  const escalate = useCallback(() => {
    if (chatState !== "BOT" || connectionStatus !== "CONNECTED") return;
    setChatState("WAITING");
    sendFrame({ type: "ESCALATE" });
  }, [chatState, connectionStatus, sendFrame]);

  const closeChat = useCallback(() => {
    if (connectionStatus !== "CONNECTED") return;
    sendFrame({ type: "CLOSE" });
    setChatState("CLOSED");
  }, [connectionStatus, sendFrame]);

  const reconnect = useCallback(() => {
    reconnectAttemptsRef.current = 0;
    connect();
  }, [connect]);

  return {
    connectionStatus,
    chatState,
    messages,
    sendMessage,
    escalate,
    closeChat,
    reconnect,
  };
}
