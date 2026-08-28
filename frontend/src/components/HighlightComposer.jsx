import { useState } from "react";
import { X } from "lucide-react";

/**
 * 뷰어에서 긁은 문장에 메모를 붙이는 패널.
 *
 * 선택 위치에 떠 있는 팝오버가 아니라 뷰어 하단에 고정한다 — 본문은 iframe 안이라 선택
 * 좌표를 바깥 문서 좌표로 옮기려면 iframe 오프셋 계산이 붙는데, 그 값이 페이지 넘김·창 크기
 * 변경마다 틀어진다. 고정 패널은 그 문제가 없고 모바일에서도 가려지지 않는다.
 */
export default function HighlightComposer({ selectedText, onSave, onCancel }) {
  const [memoText, setMemoText] = useState("");
  const [isSaving, setIsSaving] = useState(false);

  const handleSave = async () => {
    setIsSaving(true);
    try {
      await onSave(memoText.trim());
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="shrink-0 border-t border-[var(--color-forest)]/15 bg-white px-4 py-3 sm:px-6">
      <div className="mx-auto flex max-w-3xl flex-col gap-2.5">
        <div className="flex items-start justify-between gap-3">
          <blockquote className="max-h-24 flex-1 overflow-y-auto border-l-2 border-[var(--color-honey)] pl-3 text-sm leading-relaxed text-[var(--color-ink)]/80">
            {selectedText}
          </blockquote>
          <button
            type="button"
            aria-label="메모 작성 취소"
            onClick={onCancel}
            className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-[var(--color-forest)]/50 transition-colors hover:bg-[var(--color-forest)]/10"
          >
            <X size={16} />
          </button>
        </div>

        <textarea
          value={memoText}
          onChange={(e) => setMemoText(e.target.value)}
          rows={2}
          autoFocus
          placeholder="이 문장에 남길 메모 (비워두면 문장만 저장돼요)"
          className="w-full resize-none rounded-lg border border-[var(--color-forest)]/20 px-3 py-2 text-sm outline-none focus:border-[var(--color-forest)]"
        />

        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            className="rounded-lg px-4 py-2 text-sm font-medium text-[var(--color-ink)]/60 transition hover:bg-[var(--color-forest)]/5"
          >
            취소
          </button>
          <button
            type="button"
            onClick={handleSave}
            disabled={isSaving}
            className="rounded-lg bg-[var(--color-forest)] px-5 py-2 text-sm font-semibold text-[var(--color-paper)] transition hover:bg-[var(--color-forest-light)] disabled:opacity-60"
          >
            {isSaving ? "저장 중..." : "메모 저장"}
          </button>
        </div>
      </div>
    </div>
  );
}
