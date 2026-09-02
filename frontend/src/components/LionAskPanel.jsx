import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { Send } from "lucide-react";
import Button from "./Button.jsx";
import { askLion } from "../api/mypage.js";
import { getMySubscription } from "../api/member.ts";
import { subscriptionCheckoutItem } from "../constants/subscription.ts";

// 서버가 일일 상한을 넘겼을 때 주는 코드(AiErrorCode.QUOTA_EXCEEDED, HTTP 429).
// client.ts 의 unwrap 이 ApiResponse.error.code 를 Error.code 로 옮겨준다.
const QUOTA_EXCEEDED = "QUOTA_EXCEEDED";

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
 * bookIds 를 주면 그 책들로만 검색을 좁힌다(서버가 열람 권한 목록과 교집합을 낸다).
 * 생략하면 읽을 수 있는 책(구매 ∪ 구독) 전체가 대상이다.
 */
export default function LionAskPanel({ bookIds, placeholder }) {
  const navigate = useNavigate();
  const [question, setQuestion] = useState("");
  const [status, setStatus] = useState("idle"); // idle | loading | streaming | done | error
  const [displayedAnswer, setDisplayedAnswer] = useState("");
  const [similarity, setSimilarity] = useState(null);
  // 실패 사유. { quotaExceeded, subscribed, message } — 쿼터 초과일 때만 subscribed 를 본다.
  const [error, setError] = useState(null);

  // 타자 연출은 setInterval 이라 스트리밍 중에 패널이 사라지면(뷰어 닫기 등) 그대로 남는다.
  // 마이페이지에선 언마운트가 드물어 문제가 안 됐지만 뷰어에선 흔하다.
  const intervalRef = useRef(null);
  useEffect(() => () => clearInterval(intervalRef.current), []);

  const handleAsk = async () => {
    if (!question.trim() || status === "loading" || status === "streaming") return;
    setStatus("loading");
    setDisplayedAnswer("");
    setError(null);

    // 🔴 try/catch 가 없으면 429/500 에서 status 가 "loading" 에 멈춰 점 세 개가 영원히
    // 깜빡인다. 사용자는 응답이 오는 중인지 실패한 건지 구분할 수 없다.
    let result;
    try {
      result = await askLion(question, bookIds);
    } catch (e) {
      const quotaExceeded = e?.code === QUOTA_EXCEEDED;
      // 🔴 서버는 무료 소진과 구독 소진에 같은 코드를 준다(AiErrorCode 에 등급이 없다).
      // 구분하지 않으면 하루 50회를 다 쓴 구독 회원에게 "무료 5회를 다 썼으니 구독하라"고
      // 권하게 된다 — 이미 낸 사람에게 또 파는 화면이다. 429 는 드문 경로라 그때만 한 번
      // 조회한다. 조회가 실패하면 구독 유도는 하지 않는다(없는 걸 팔지 않는 쪽으로 기운다).
      let subscribed = true;
      if (quotaExceeded) {
        try {
          subscribed = (await getMySubscription())?.isActive ?? false;
        } catch {
          subscribed = true;
        }
      }
      setError({ quotaExceeded, subscribed, message: e?.message });
      setStatus("error");
      return;
    }

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

  // 구독은 결제를 거친다 — 여기서 만들지 않고 /checkout 으로 보낸다(ProductListPage 와 같은 경로).
  const goToSubscriptionCheckout = () =>
    navigate("/checkout", { state: { items: [subscriptionCheckoutItem()] } });

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
            ) : status === "error" ? (
              <div className="text-sm text-[var(--color-ink)]">
                {error?.quotaExceeded && error?.subscribed ? (
                  <>
                    <p>오늘 질의 한도를 모두 사용했어요.</p>
                    <p className="mt-0.5 opacity-60">자정에 초기화됩니다.</p>
                  </>
                ) : error?.quotaExceeded ? (
                  <>
                    <p>오늘 무료 질의를 모두 사용했어요.</p>
                    <p className="mt-0.5 opacity-60">
                      정기구독하면 더 많이 물어볼 수 있어요. 무료 횟수는 자정에 초기화됩니다.
                    </p>
                    <button
                      type="button"
                      onClick={goToSubscriptionCheckout}
                      className="mt-2.5 rounded-full bg-[var(--color-forest)] px-4 py-2 text-sm font-semibold text-[var(--color-paper)] transition hover:opacity-90"
                    >
                      구독하고 계속하기 &gt;
                    </button>
                  </>
                ) : (
                  <p>{error?.message ?? "답변을 가져오지 못했어요. 잠시 후 다시 시도해주세요."}</p>
                )}
              </div>
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
