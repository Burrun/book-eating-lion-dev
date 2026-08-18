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
  // 백엔드 계약(backend/contracts/ai-v1.yaml /ws/ai/chat): 티켓은 query parameter로 전달한다.
  // WebSocket 핸드셰이크는 커스텀 헤더를 못 붙이므로 URL 파라미터가 유일한 실용적 방법이다.
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

  // 연결 시도 세대 번호. StrictMode는 개발 모드에서 effect를 mount -> cleanup -> mount로
  // 두 번 돌리는데, 그때마다 connect()가 그대로 불리면 티켓이 두 번 발급되고 소켓도 두 개
  // 열릴 수 있다. connect()가 매번 이 값을 스스로 증가시켜 자기 세대를 갖고, disconnect()도
  // 증가시켜 진행 중이던 연결을 무효화한다 — await 이후 자기 세대가 최신이 아니면 그 결과를
  // 버린다. 클로저가 살아있는 상태값(closedByUserRef 같은 단일 boolean)보다 이 방식이 안전한
  // 이유는, 이후에 시작된 새 connect()가 옛 시도를 확실히 덮어쓰기 때문이다(단순 boolean은
  // "누가 마지막으로 껐/켰는지"만 알 뿐 "이 시도가 몇 번째인지"는 모른다).
  const epochRef = useRef(0);

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
        // 서버는 상담사 배정을 별도 프레임으로 알리지 않는다 — AGENT 발화가 오는 순간이
        // 곧 LIVE 전환의 유일한 신호다(backend ChatWebSocketHandler#onClaim 참고).
        if (frame.role === "AGENT") setChatState("LIVE");
      }
    },
    [appendMessage],
  );

  const disconnect = useCallback(() => {
    epochRef.current += 1;
    if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
    transportRef.current?.close();
    transportRef.current = null;
  }, []);

  const connect = useCallback(async () => {
    const myEpoch = ++epochRef.current;
    setConnectionStatus("CONNECTING");
    try {
      const { ticket } = await issueChatTicket();
      // 대기하는 동안 이 시도가 disconnect()나 더 최신 connect()로 무효화됐을 수 있다.
      if (epochRef.current !== myEpoch) return;

      const transport = USE_MOCK ? createMockChatSocket() : wrapNativeSocket(new WebSocket(resolveWsUrl(ticket)));
      transportRef.current = transport;

      transport.onopen = () => {
        if (epochRef.current !== myEpoch) return;
        reconnectAttemptsRef.current = 0;
        setConnectionStatus("CONNECTED");
      };
      transport.onmessage = handleRawFrame;
      transport.onclose = () => {
        if (epochRef.current !== myEpoch) return;
        setConnectionStatus("DISCONNECTED");
        if (reconnectAttemptsRef.current >= MAX_RECONNECT_ATTEMPTS) return;
        reconnectAttemptsRef.current += 1;
        const delay = Math.min(1000 * 2 ** reconnectAttemptsRef.current, 8000);
        // connect의 의존성(handleRawFrame -> appendMessage)이 전부 빈 배열로 고정돼 있어
        // connect 자체가 컴포넌트 생애주기 동안 재생성되지 않는다 — 재귀 참조가 안전하다.
        // eslint-disable-next-line react-hooks/immutability
        reconnectTimerRef.current = setTimeout(connect, delay);
      };
    } catch {
      if (epochRef.current !== myEpoch) return;
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
      // WAITING 구간엔 ASK(BOT 전용)도 SAY(LIVE 전용)도 서버가 받아주지 않는다.
      if (!trimmed || connectionStatus !== "CONNECTED" || chatState === "CLOSED" || chatState === "WAITING") {
        return;
      }
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

  /**
   * BOT 모드로 즉시 복귀한다 — WAITING 중 "대기 취소"와 CLOSED 이후 "새 대화 시작"이 같은
   * 동작이다. 서버엔 "봇으로 되돌리기" 프레임이 없다(ChatWebSocketHandler 참고: BOT/WAITING/
   * LIVE/CLOSED 뿐이고 CLOSED에서만 벗어날 수 있다) — CLOSED가 되면 ChatRoomStore#close가
   * memberKey 매핑을 지우므로, 다음 접속의 openOrResume이 새 roomId로 새 방을 연다. 즉
   * "봇과 계속 대화"는 실제로는 새 방이다. 그래서 seq가 1부터 다시 시작하는 새 방과 섞이지
   * 않도록 로컬 상태(메시지/중복제거 캐시)도 함께 비운다.
   */
  const restartChat = useCallback(() => {
    if (connectionStatus === "CONNECTED" && chatState !== "CLOSED") {
      sendFrame({ type: "CLOSE" });
    }
    disconnect();
    seenSeqRef.current.clear();
    setMessages([]);
    setChatState("BOT");
    reconnectAttemptsRef.current = 0;
    connect();
  }, [chatState, connect, connectionStatus, disconnect, sendFrame]);

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
    restartChat,
    reconnect,
  };
}
