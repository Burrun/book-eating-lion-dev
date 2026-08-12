import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { ReactReader } from "react-reader";
import { X } from "lucide-react";

/**
 * 전자책(EPUB) 전체화면 뷰어.
 *
 * `url`은 로컬 정적 경로(/ebooks/xxx.epub)든 S3 presigned URL이든 그대로 넘기면 된다.
 * 실 API(GET /api/catalog/books/{bookId}/ebook) 연동 시에도 이 컴포넌트는 그대로 두고
 * 호출부(ProductDetailPage)에서 응답으로 받은 URL을 넘기기만 하면 된다.
 */
export default function EbookViewer({ isOpen, onClose, url, title }) {
  const [location, setLocation] = useState(null);

  useEffect(() => {
    if (!isOpen) return;
    // 뷰어를 새로 열 때마다 처음 위치로 초기화한다. (이어읽기 저장은 스코프 밖)
    setLocation(null);

    const handleKeyDown = (e) => e.key === "Escape" && onClose?.();
    document.addEventListener("keydown", handleKeyDown);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = prevOverflow;
    };
  }, [isOpen, onClose]);

  if (!isOpen || !url) return null;

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title ? `${title} 전자책 뷰어` : "전자책 뷰어"}
      className="fixed inset-0 z-[100] flex flex-col bg-[var(--color-paper)]"
    >
      <div className="flex shrink-0 items-center justify-between border-b border-[var(--color-forest)]/10 bg-white px-4 py-3 sm:px-6">
        <h2 className="line-clamp-1 font-display text-base text-[var(--color-forest)] sm:text-lg">
          {title ?? "전자책 뷰어"}
        </h2>
        <button
          type="button"
          aria-label="닫기"
          onClick={() => onClose?.()}
          className="ml-4 flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-[var(--color-forest)]/60 transition-colors hover:bg-[var(--color-forest)]/10 hover:text-[var(--color-forest)]"
        >
          <X size={20} />
        </button>
      </div>

      {/* react-reader는 root에 height:100%를 쓰므로, 부모가 flex 레이아웃 안에서
          정의된 높이를 갖도록 relative + flex-1 + min-h-0을 함께 둔다. */}
      <div className="relative min-h-0 flex-1">
        <ReactReader
          url={url}
          title={title}
          location={location}
          locationChanged={(cfi) => setLocation(cfi)}
          showToc
        />
      </div>
    </div>,
    document.body,
  );
}
