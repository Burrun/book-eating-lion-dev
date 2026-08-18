import { useRef, type ChangeEvent, type KeyboardEvent } from "react";
import { Send } from "lucide-react";

interface ChatInputBarProps {
  value: string;
  onChange: (value: string) => void;
  onSend: () => void;
  disabled?: boolean;
  placeholder?: string;
}

export default function ChatInputBar({
  value,
  onChange,
  onSend,
  disabled = false,
  placeholder = "메시지를 입력하세요",
}: ChatInputBarProps) {
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  function autoResize() {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 96)}px`;
  }

  function handleInput(e: ChangeEvent<HTMLTextAreaElement>) {
    onChange(e.target.value);
    autoResize();
  }

  // Shift+Enter는 줄바꿈, Enter 단독은 즉시 전송.
  function handleKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }

  function handleSend() {
    if (disabled || !value.trim()) return;
    onSend();
    requestAnimationFrame(autoResize);
  }

  return (
    <div className="flex shrink-0 items-end gap-2 border-t border-[var(--color-forest)]/10 bg-[var(--color-paper)] px-3 py-3">
      <textarea
        ref={textareaRef}
        value={value}
        onChange={handleInput}
        onKeyDown={handleKeyDown}
        disabled={disabled}
        rows={1}
        placeholder={placeholder}
        className="max-h-24 flex-1 resize-none rounded-xl border border-[var(--color-forest)]/20 bg-white px-3.5 py-2.5 text-sm text-[var(--color-ink)] placeholder:text-[var(--color-ink)]/40 focus:border-[var(--color-honey)] focus:outline-none disabled:cursor-not-allowed disabled:opacity-50"
      />
      <button
        type="button"
        aria-label="메시지 전송"
        onClick={handleSend}
        disabled={disabled || !value.trim()}
        className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[var(--color-honey)] text-[var(--color-forest)] transition-all hover:brightness-105 active:scale-[0.96] disabled:cursor-not-allowed disabled:opacity-40"
      >
        <Send size={17} />
      </button>
    </div>
  );
}
