import { useEffect } from "react";
import { createPortal } from "react-dom";
import { AnimatePresence, motion } from "framer-motion";
import { X } from "lucide-react";

const SIZE_CLASSES = {
  sm: "max-w-sm",
  md: "max-w-md",
  lg: "max-w-lg",
};

export default function Modal({
  isOpen,
  onClose,
  title,
  children,
  footer,
  size = "md",
  closeOnOverlayClick = true,
}) {
  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e) => e.key === "Escape" && onClose?.();
    document.addEventListener("keydown", handleKeyDown);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = prevOverflow;
    };
  }, [isOpen, onClose]);

  return createPortal(
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4">
          <motion.div
            className="absolute inset-0 bg-[var(--color-forest)]/40 backdrop-blur-sm"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => closeOnOverlayClick && onClose?.()}
          />
          <motion.div
            role="dialog"
            aria-modal="true"
            aria-labelledby={title ? "modal-title" : undefined}
            className={`relative flex max-h-[85vh] w-full flex-col overflow-hidden rounded-2xl bg-[var(--color-paper)] shadow-[0_24px_48px_rgba(27,59,54,0.25)] ${SIZE_CLASSES[size]}`}
            initial={{ opacity: 0, scale: 0.95, y: 12 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 12 }}
            transition={{ duration: 0.2, ease: "easeOut" }}
          >
            <div className="flex shrink-0 items-center justify-between border-b border-[var(--color-forest)]/10 px-6 py-4">
              {title && (
                <h2 id="modal-title" className="font-display text-lg text-[var(--color-forest)]">
                  {title}
                </h2>
              )}
              <button
                type="button"
                aria-label="닫기"
                onClick={() => onClose?.()}
                className="ml-auto flex h-8 w-8 items-center justify-center rounded-full text-[var(--color-forest)]/60 transition-colors hover:bg-[var(--color-forest)]/10 hover:text-[var(--color-forest)]"
              >
                <X size={18} />
              </button>
            </div>

            <div className="overflow-y-auto px-6 py-5 text-[var(--color-ink)]">{children}</div>

            {footer && (
              <div className="flex shrink-0 justify-end gap-2 border-t border-[var(--color-forest)]/10 px-6 py-4">
                {footer}
              </div>
            )}
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body,
  );
}
