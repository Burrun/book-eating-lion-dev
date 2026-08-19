import { useEffect, useRef } from "react";
import { Bot, Headset } from "lucide-react";
import type { ChatMessage, ChatRoomState, ConnectionStatus } from "../../types/chat.ts";

const QUICK_REPLIES = [
  "배송이 얼마나 걸리나요?",
  "반품/교환은 어떻게 하나요?",
  "구독 서비스가 궁금해요",
  "쿠폰은 어디서 확인하나요?",
];

interface ChatMessageListProps {
  messages: ChatMessage[];
  connectionStatus: ConnectionStatus;
  chatState: ChatRoomState;
  onQuickReply: (text: string) => void;
  onReconnect: () => void;
  onCancelWaiting: () => void;
}

export default function ChatMessageList({
  messages,
  connectionStatus,
  chatState,
  onQuickReply,
  onReconnect,
  onCancelWaiting,
}: ChatMessageListProps) {
  const bottomRef = useRef<HTMLDivElement>(null);

  // 새 메시지가 오면 항상 맨 아래로 붙는다.
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages.length]);

  const hasUserSpoken = messages.some((message) => message.role === "USER");
  const showQuickReplies = chatState === "BOT" && connectionStatus === "CONNECTED" && !hasUserSpoken;

  return (
    <div className="flex flex-1 flex-col overflow-y-auto px-4 py-4">
      {connectionStatus === "DISCONNECTED" && messages.length > 0 && (
        <button
          type="button"
          onClick={onReconnect}
          className="mb-3 w-full shrink-0 rounded-lg bg-[var(--color-coral)]/10 px-3 py-2 text-xs font-medium text-[var(--color-coral)] transition-colors hover:bg-[var(--color-coral)]/20"
        >
          연결이 끊어졌어요 · 다시 연결하기
        </button>
      )}

      {chatState === "WAITING" && connectionStatus === "CONNECTED" && (
        <button
          type="button"
          onClick={onCancelWaiting}
          className="mb-3 w-full shrink-0 rounded-lg bg-[var(--color-honey)]/15 px-3 py-2 text-xs font-medium text-[var(--color-forest)] transition-colors hover:bg-[var(--color-honey)]/25"
        >
          상담사를 기다리는 중이에요 · 대기 취소하고 봇과 계속 대화
        </button>
      )}

      {messages.length === 0 && connectionStatus === "CONNECTING" && <ConnectingState />}
      {messages.length === 0 && connectionStatus === "DISCONNECTED" && (
        <DisconnectedState onReconnect={onReconnect} />
      )}

      <div className="flex flex-col gap-3">
        {messages.map((message, index) => (
          <MessageBubble
            key={message.seq}
            message={message}
            showAvatar={message.role !== "SYSTEM" && messages[index - 1]?.role !== message.role}
          />
        ))}
      </div>

      {showQuickReplies && (
        <div className="mt-4 flex flex-wrap gap-2">
          {QUICK_REPLIES.map((question) => (
            <button
              key={question}
              type="button"
              onClick={() => onQuickReply(question)}
              className="rounded-full border border-[var(--color-honey)]/50 bg-[var(--color-honey)]/10 px-3.5 py-1.5 text-xs font-medium text-[var(--color-forest)] transition-colors hover:bg-[var(--color-honey)]/25"
            >
              {question}
            </button>
          ))}
        </div>
      )}

      <div ref={bottomRef} />
    </div>
  );
}

function MessageBubble({ message, showAvatar }: { message: ChatMessage; showAvatar: boolean }) {
  if (message.role === "SYSTEM") {
    return (
      <p className="mx-auto max-w-[85%] rounded-full bg-[var(--color-forest)]/5 px-3 py-1 text-center text-[11px] text-[var(--color-ink)]/50">
        {message.text}
      </p>
    );
  }

  const isMe = message.role === "USER";
  const OpponentIcon = message.role === "AGENT" ? Headset : Bot;

  return (
    <div className={`flex items-end gap-2 ${isMe ? "flex-row-reverse" : ""}`}>
      {!isMe && (
        <span
          className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-[var(--color-honey)]/25 text-[var(--color-forest)] ${showAvatar ? "" : "invisible"}`}
        >
          <OpponentIcon size={14} />
        </span>
      )}
      <div className={`flex max-w-[75%] flex-col gap-1 ${isMe ? "items-end" : "items-start"}`}>
        {showAvatar && !isMe && (
          <span className="ml-1 text-[10px] font-medium text-[var(--color-forest)]/50">
            {message.nickname ?? (message.role === "AGENT" ? "상담사" : "AI 상담원")}
          </span>
        )}
        <div
          className={`px-4 py-2.5 text-sm leading-relaxed whitespace-pre-wrap break-words ${
            isMe
              ? "rounded-2xl rounded-br-sm bg-[var(--color-forest)] text-[var(--color-paper)]"
              : "rounded-2xl rounded-bl-sm border border-[var(--color-forest)]/10 bg-white text-[var(--color-ink)] shadow-[0_1px_3px_rgba(27,59,54,0.08)]"
          }`}
        >
          {message.text}
        </div>
        <span className="px-1 text-[10px] text-[var(--color-ink)]/35">{message.at}</span>
      </div>
    </div>
  );
}

function ConnectingState() {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-3 text-center">
      <span className="flex items-center gap-1">
        {[0, 1, 2].map((i) => (
          <span
            key={i}
            className="h-2 w-2 animate-bounce rounded-full bg-[var(--color-forest)]/30"
            style={{ animationDelay: `${i * 0.15}s` }}
          />
        ))}
      </span>
      <p className="text-sm text-[var(--color-ink)]/50">상담원을 연결하고 있어요...</p>
    </div>
  );
}

function DisconnectedState({ onReconnect }: { onReconnect: () => void }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-3 text-center">
      <p className="text-sm text-[var(--color-ink)]/50">연결이 끊어졌어요.</p>
      <button
        type="button"
        onClick={onReconnect}
        className="rounded-full bg-[var(--color-forest)] px-4 py-2 text-xs font-semibold text-[var(--color-paper)] transition hover:bg-[var(--color-forest-light)]"
      >
        다시 연결하기
      </button>
    </div>
  );
}
