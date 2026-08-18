import { useState } from "react";
import { Link } from "react-router-dom";
import { AnimatePresence, motion } from "framer-motion";
import { MessageCircle, X } from "lucide-react";
import { useAuth } from "../../context/AuthContext.jsx";
import { useWebSocketChat } from "../../hooks/useWebSocketChat.ts";
import ChatHeader from "./ChatHeader.tsx";
import ChatMessageList from "./ChatMessageList.tsx";
import ChatInputBar from "./ChatInputBar.tsx";

// 사이트 전역에 뜨는 1:1 실시간 상담 위젯. 패널이 열려 있을 때만 소켓을 연결한다.
export default function ChatContainer() {
  const { isAuthenticated } = useAuth();
  const [isOpen, setIsOpen] = useState(false);
  const [draft, setDraft] = useState("");

  const { connectionStatus, chatState, messages, sendMessage, escalate, closeChat, reconnect } =
    useWebSocketChat({ enabled: isOpen && isAuthenticated });

  function handleSend() {
    if (!draft.trim()) return;
    sendMessage(draft);
    setDraft("");
  }

  // WAITING 구간엔 ASK(봇 전용)도 SAY(LIVE 전용)도 서버가 받아주지 않는다 — 입력을 막아둔다.
  const inputDisabled =
    connectionStatus !== "CONNECTED" || chatState === "CLOSED" || chatState === "WAITING";

  return (
    <div className="fixed right-5 bottom-5 z-[90] flex flex-col items-end gap-3 sm:right-6 sm:bottom-6">
      <AnimatePresence>
        {isOpen && (
          <motion.div
            role="dialog"
            aria-label="책 먹는 사자 상담 채팅"
            initial={{ opacity: 0, y: 16, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 16, scale: 0.97, transition: { duration: 0.15 } }}
            transition={{ duration: 0.22, ease: "easeOut" }}
            className="flex h-[min(640px,calc(100vh-7rem))] w-[min(380px,calc(100vw-2.5rem))] flex-col overflow-hidden rounded-2xl bg-[var(--color-paper)] shadow-[0_24px_48px_rgba(27,59,54,0.25)]"
          >
            {!isAuthenticated ? (
              <LoginPrompt onNavigate={() => setIsOpen(false)} />
            ) : (
              <>
                <ChatHeader
                  connectionStatus={connectionStatus}
                  chatState={chatState}
                  onEscalate={escalate}
                  onEndChat={closeChat}
                  onClose={() => setIsOpen(false)}
                />
                <ChatMessageList
                  messages={messages}
                  connectionStatus={connectionStatus}
                  chatState={chatState}
                  onQuickReply={sendMessage}
                  onReconnect={reconnect}
                />
                <ChatInputBar
                  value={draft}
                  onChange={setDraft}
                  onSend={handleSend}
                  disabled={inputDisabled}
                  placeholder={
                    chatState === "CLOSED"
                      ? "상담이 종료됐어요"
                      : chatState === "WAITING"
                        ? "상담사를 연결하는 중이에요..."
                        : "메시지를 입력하세요"
                  }
                />
              </>
            )}
          </motion.div>
        )}
      </AnimatePresence>

      <motion.button
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        aria-label={isOpen ? "상담 채팅 닫기" : "상담 채팅 열기"}
        whileTap={{ scale: 0.94 }}
        className="flex h-14 w-14 items-center justify-center rounded-full bg-[var(--color-honey)] text-[var(--color-forest)] shadow-[0_12px_24px_rgba(27,59,54,0.25)] transition-transform hover:scale-105"
      >
        {isOpen ? <X size={22} /> : <MessageCircle size={22} />}
      </motion.button>
    </div>
  );
}

function LoginPrompt({ onNavigate }: { onNavigate: () => void }) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 p-8 text-center">
      <span className="text-4xl">🦁</span>
      <p className="text-sm font-medium text-[var(--color-ink)]">
        실시간 상담은 로그인 후 이용할 수 있어요
      </p>
      <Link
        to="/login"
        onClick={onNavigate}
        className="rounded-full bg-[var(--color-forest)] px-5 py-2 text-sm font-semibold text-[var(--color-paper)] transition hover:bg-[var(--color-forest-light)]"
      >
        로그인하러 가기
      </Link>
    </div>
  );
}
