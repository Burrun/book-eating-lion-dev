import { createContext, useCallback, useContext, useState } from "react";
import { createPortal } from "react-dom";
import { AnimatePresence, motion } from "framer-motion";
import { CheckCircle2, XCircle, Info, X } from "lucide-react";

const ToastContext = createContext(null);

const ICONS = { success: CheckCircle2, error: XCircle, info: Info };
const ICON_STYLES = {
  success: "bg-[var(--color-honey)] text-[var(--color-forest)]",
  error: "bg-[var(--color-coral)] text-white",
  info: "bg-[var(--color-forest)] text-[var(--color-paper)]",
};

let idCounter = 0;

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const dismiss = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const show = useCallback(
    (message, { type = "info", duration = 3000 } = {}) => {
      const id = ++idCounter;
      setToasts((prev) => [...prev, { id, message, type }]);
      if (duration > 0) {
        setTimeout(() => dismiss(id), duration);
      }
      return id;
    },
    [dismiss]
  );

  const toast = {
    show,
    success: (message, opts) => show(message, { ...opts, type: "success" }),
    error: (message, opts) => show(message, { ...opts, type: "error" }),
    info: (message, opts) => show(message, { ...opts, type: "info" }),
    dismiss,
  };

  return (
    <ToastContext.Provider value={toast}>
      {children}
      {createPortal(
        <div className="pointer-events-none fixed inset-x-0 bottom-6 z-[200] flex flex-col items-center gap-2 px-4 sm:right-6 sm:left-auto sm:items-end sm:px-0">
          <AnimatePresence>
            {toasts.map((t) => {
              const Icon = ICONS[t.type] ?? Info;
              return (
                <motion.div
                  key={t.id}
                  layout
                  initial={{ opacity: 0, y: 16, scale: 0.95 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.95, transition: { duration: 0.15 } }}
                  transition={{ duration: 0.25, ease: "easeOut" }}
                  className="pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-xl bg-white px-4 py-3 shadow-[0_12px_24px_rgba(27,59,54,0.18)]"
                >
                  <span
                    className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full ${ICON_STYLES[t.type] ?? ICON_STYLES.info}`}
                  >
                    <Icon size={16} />
                  </span>
                  <p className="flex-1 pt-0.5 text-sm text-[var(--color-ink)]">{t.message}</p>
                  <button
                    type="button"
                    aria-label="닫기"
                    onClick={() => dismiss(t.id)}
                    className="text-[var(--color-ink)]/40 transition-colors hover:text-[var(--color-ink)]"
                  >
                    <X size={16} />
                  </button>
                </motion.div>
              );
            })}
          </AnimatePresence>
        </div>,
        document.body
      )}
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error("useToast must be used within a ToastProvider");
  }
  return ctx;
}
