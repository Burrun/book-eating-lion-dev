import { useEffect, useRef, useState } from "react";
import { motion } from "framer-motion";
import { Send } from "lucide-react";
import Button from "./Button.jsx";
import { askLion } from "../api/mypage.js";

// citations[].score 는 0~1 이고 클수록 유사하다. 화면은 백분율 하나만 쓰므로
// 가장 가까운 근거를 대표값으로 삼는다. 근거가 없으면(grounded: false) 0 이다.
function toSimilarityPercent(citations) {
  if (!citations?.length) return 0;
  return Math.round(Math.max(...citations.map((c) => c.score)) * 100);
}

/**
 * 사자 RAG 질의 입력창 + 답변 영역. 카드 테두리/제목 같은 껍데기는 호출부가 각자 두른다
 * — 마이페이지는 섹션 카드, EPUB 뷰어는 오버레이라 공유할 게 없다.
 *
 * bookIds 를 주면 그 책들로만 검색을 좁힌다(서버가 구매 목록과 교집합을 낸다).
 * 생략하면 구매한 책 전체가 대상이다.
 */
export default function LionAskPanel({ bookIds, placeholder }) {
  const [question, setQuestion] = useState("");
  const [status, setStatus] = useState("idle"); // idle | loading | streaming | done
  const [displayedAnswer, setDisplayedAnswer] = useState("");
  const [similarity, setSimilarity] = useState(null);

  // 타자 연출은 setInterval 이라 스트리밍 중에 패널이 사라지면(뷰어 닫기 등) 그대로 남는다.
  // 마이페이지에선 언마운트가 드물어 문제가 안 됐지만 뷰어에선 흔하다.
  const intervalRef = useRef(null);
  useEffect(() => () => clearInterval(intervalRef.current), []);

  const handleAsk = async () => {
    if (!question.trim() || status === "loading" || status === "streaming") return;
    setStatus("loading");
    setDisplayedAnswer("");

    const result = await askLion(question, bookIds);
    setSimilarity(toSimilarityPercent(result.citations));
    setStatus("streaming");

    const full = result.answer;
    let i = 0;
    clearInterval(intervalRef.current);
    intervalRef.current = setInterval(() => {
      i += 2;
      setDisplayedAnswer(full.slice(0, i));
      if (i >= full.length) {
        clearInterval(intervalRef.current);
        setStatus("done");
      }
    }, 25);
  };

  const isBusy = status === "loading" || status === "streaming";

  return (
    <>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          handleAsk();
        }}
        className="flex gap-2"
      >
        <input
          type="text"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder={placeholder}
          className="w-full rounded-xl border border-[var(--color-forest)]/20 px-3.5 py-2.5 text-sm focus:border-[var(--color-honey)] focus:outline-none"
        />
        <Button
          type="submit"
          variant="primary"
          shimmer={false}
          disabled={isBusy}
          className="shrink-0 px-4"
        >
          <Send size={15} />
        </Button>
      </form>

      {status !== "idle" && (
        <div className="mt-4 rounded-xl bg-[var(--color-forest)]/5 p-4">
          <div className="flex items-start gap-2.5">
            <span className="text-lg leading-none">🦁</span>
            {status === "loading" ? (
              <span className="flex items-center gap-1 pt-1.5">
                {[0, 1, 2].map((i) => (
                  <motion.span
                    key={i}
                    className="h-1.5 w-1.5 rounded-full bg-[var(--color-forest)]/40"
                    animate={{ opacity: [0.3, 1, 0.3] }}
                    transition={{ duration: 1, repeat: Infinity, delay: i * 0.15 }}
                  />
                ))}
              </span>
            ) : (
              <p className="text-sm text-[var(--color-ink)]">
                <span className="font-medium text-[var(--color-forest)]">사자 사서 답변: </span>
                {displayedAnswer}
                {status === "streaming" && <span className="animate-pulse">▌</span>}
                {status === "done" && (
                  <span className="ml-2 inline-block rounded-full bg-[var(--color-honey)]/20 px-2 py-0.5 text-[11px] font-medium text-[var(--color-forest)]">
                    유사도 {similarity}%
                  </span>
                )}
              </p>
            )}
          </div>
        </div>
      )}
    </>
  );
}
