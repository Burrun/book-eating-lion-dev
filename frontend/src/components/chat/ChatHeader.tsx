import { Bot, Headset, X } from "lucide-react";
import type { ChatRoomState, ConnectionStatus } from "../../types/chat.ts";

const STATE_LABEL: Record<ChatRoomState, string> = {
  BOT: "AI 상담원과 대화 중",
  WAITING: "상담사를 연결하는 중...",
  LIVE: "상담사와 상담 중",
  CLOSED: "상담이 종료됐어요",
};

const STATUS_LABEL: Record<ConnectionStatus, string> = {
  CONNECTED: "연결됨",
  CONNECTING: "연결 중...",
  DISCONNECTED: "연결 끊김",
};

const STATUS_DOT: Record<ConnectionStatus, string> = {
  CONNECTED: "bg-[var(--color-honey)]",
  CONNECTING: "animate-pulse bg-[var(--color-paper)]/50",
  DISCONNECTED: "bg-[var(--color-coral)]",
};

interface ChatHeaderProps {
  connectionStatus: ConnectionStatus;
  chatState: ChatRoomState;
  onEscalate: () => void;
  onEndChat: () => void;
  onClose: () => void;
}

export default function ChatHeader({
  connectionStatus,
  chatState,
  onEscalate,
  onEndChat,
  onClose,
}: ChatHeaderProps) {
  const OpponentIcon = chatState === "LIVE" ? Headset : Bot;

  return (
    <div className="flex shrink-0 items-center gap-3 rounded-t-2xl bg-[var(--color-forest)] px-4 py-3.5">
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[var(--color-honey)] text-[var(--color-forest)]">
        <OpponentIcon size={19} />
      </span>

      <div className="min-w-0 flex-1">
        <p className="font-display truncate text-sm text-[var(--color-paper)]">책 먹는 사자 상담</p>
        <div className="mt-0.5 flex items-center gap-1.5">
          <span className={`h-1.5 w-1.5 shrink-0 rounded-full ${STATUS_DOT[connectionStatus]}`} />
          <p className="truncate text-xs text-[var(--color-paper)]/70">
            {connectionStatus === "CONNECTED" ? STATE_LABEL[chatState] : STATUS_LABEL[connectionStatus]}
          </p>
        </div>
      </div>

      {chatState === "BOT" && connectionStatus === "CONNECTED" && (
        <button
          type="button"
          onClick={onEscalate}
          className="shrink-0 rounded-full bg-[var(--color-paper)]/10 px-3 py-1.5 text-xs font-medium text-[var(--color-paper)] transition-colors hover:bg-[var(--color-paper)]/20"
        >
          상담사 연결
        </button>
      )}
      {(chatState === "WAITING" || chatState === "LIVE") && (
        <button
          type="button"
          onClick={onEndChat}
          className="shrink-0 rounded-full bg-[var(--color-paper)]/10 px-3 py-1.5 text-xs font-medium text-[var(--color-paper)] transition-colors hover:bg-[var(--color-coral)]/80"
        >
          상담 종료
        </button>
      )}

      <button
        type="button"
        aria-label="채팅창 닫기"
        onClick={onClose}
        className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[var(--color-paper)]/70 transition-colors hover:bg-[var(--color-paper)]/10 hover:text-[var(--color-paper)]"
      >
        <X size={17} />
      </button>
    </div>
  );
}
