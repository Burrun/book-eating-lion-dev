import type { ChatMessage, ChatRole, ChatRoomState, ChatTransport } from "../types/chat.ts";

let ticketSeq = 0;

// POST /api/ai/bot/chat/ticket 목업
export function mockIssueChatTicket() {
  return { ticket: `mock-ticket-${++ticketSeq}`, expiresInSeconds: 60 };
}

function nowLabel(): string {
  return new Date().toLocaleTimeString("ko-KR", { hour12: false });
}

const FAQ_ANSWERS: { keywords: string[]; answer: string }[] = [
  {
    keywords: ["배송"],
    answer: "일반 배송은 결제 완료 후 1~2일 내 출고되고, 평균 2~3일 안에 도착해요 📦",
  },
  {
    keywords: ["반품", "교환", "환불"],
    answer: "받으신 날로부터 7일 이내라면 마이페이지 > 취소/교환/반품에서 바로 신청할 수 있어요.",
  },
  {
    keywords: ["구독"],
    answer: "월 구독을 시작하면 신간 웹툰 요약 컷과 무제한 ebook 열람이 열려요!",
  },
  {
    keywords: ["쿠폰"],
    answer: "보유 쿠폰은 마이페이지 > 쿠폰 현황에서 확인할 수 있고, 결제 단계에서 자동 적용돼요.",
  },
];

function pickBotAnswer(question: string): string {
  const hit = FAQ_ANSWERS.find((faq) => faq.keywords.some((keyword) => question.includes(keyword)));
  return (
    hit?.answer ??
    "정확한 답변을 드리기 어려운 질문이에요. 아래 '상담사 연결'을 눌러주시면 상담사가 직접 도와드릴게요!"
  );
}

const AGENT_REPLIES = [
  "네, 확인해드릴게요. 잠시만 기다려주세요!",
  "말씀하신 내용 접수했습니다. 처리 후 다시 안내드릴게요.",
  "추가로 궁금하신 점 있으실까요?",
];

/**
 * 실제 백엔드(ChatWebSocketHandler) 없이도 채팅 UI를 확인할 수 있도록 하는 목업 소켓.
 * VITE_USE_MOCK=true일 때 useWebSocketChat이 실제 WebSocket 대신 이 객체를 사용한다.
 * 클라이언트 -> 서버 프레임을 받아, 실 서버와 같은 프레임 모양(JOINED/MESSAGE/NO_AGENT)으로 응답한다.
 */
export function createMockChatSocket(): ChatTransport {
  let seq = 1;
  let roomState: ChatRoomState = "BOT";
  let agentReplyIndex = 0;
  const roomId = "mock-room";
  const timers: ReturnType<typeof setTimeout>[] = [];

  const socket: ChatTransport = {
    onopen: null,
    onmessage: null,
    onclose: null,
    onerror: null,
    send(raw: string) {
      let frame: { type?: string; text?: string };
      try {
        frame = JSON.parse(raw);
      } catch {
        return;
      }
      handleFrame(frame);
    },
    close() {
      timers.forEach(clearTimeout);
      schedule(() => socket.onclose?.(), 0);
    },
  };

  function schedule(fn: () => void, delay: number) {
    timers.push(setTimeout(fn, delay));
  }

  function emit(payload: unknown, delay = 250 + Math.random() * 250) {
    schedule(() => socket.onmessage?.(JSON.stringify(payload)), delay);
  }

  function emitMessage(role: ChatRole, text: string, nickname: string | null, delay?: number) {
    seq += 1;
    emit({ type: "MESSAGE", seq, role, nickname, text, at: nowLabel() }, delay);
  }

  function handleFrame(frame: { type?: string; text?: string }) {
    switch (frame.type) {
      case "ASK": {
        if (roomState !== "BOT" || !frame.text) return;
        emitMessage("USER", frame.text, null, 0);
        emitMessage("BOT", pickBotAnswer(frame.text), "AI 상담원", 700 + Math.random() * 500);
        return;
      }
      case "SAY": {
        if (roomState !== "LIVE" || !frame.text) return;
        emitMessage("USER", frame.text, null, 0);
        const reply = AGENT_REPLIES[agentReplyIndex % AGENT_REPLIES.length];
        agentReplyIndex += 1;
        emitMessage("AGENT", reply, "라이언 상담사", 900 + Math.random() * 500);
        return;
      }
      case "ESCALATE": {
        if (roomState !== "BOT") return;
        emitMessage("SYSTEM", "상담사를 연결하는 중입니다.", null, 0);
        emitMessage("SYSTEM", "상담사가 연결되었습니다.", null, 1200);
        emitMessage(
          "AGENT",
          "안녕하세요, 상담사입니다. 무엇을 도와드릴까요?",
          "라이언 상담사",
          1700,
        );
        roomState = "LIVE";
        return;
      }
      case "CLOSE": {
        emitMessage("SYSTEM", "상담이 종료되었습니다.", null, 0);
        roomState = "CLOSED";
        return;
      }
      default:
        return;
    }
  }

  schedule(() => {
    socket.onopen?.();
    schedule(() => {
      // 실 서버(ChatWebSocketHandler#send)는 JOINED를 ApiResponse 봉투에 담아 보낸다.
      // MESSAGE(위 emitMessage)는 Redis Lua 스크립트가 직접 발행해 봉투가 없다 — 이 차이를
      // useWebSocketChat이 success 필드 유무로 구분하므로 목업도 그대로 흉내낸다.
      emit(
        {
          success: true,
          message: "SUCCESS",
          data: {
            type: "JOINED",
            roomId,
            state: roomState,
            messages: [
              {
                seq: 1,
                role: "SYSTEM",
                nickname: null,
                text: "안녕하세요! 책 먹는 사자 상담이에요. 무엇을 도와드릴까요? 🦁",
                at: nowLabel(),
              } satisfies ChatMessage,
            ],
          },
          error: null,
        },
        0,
      );
    }, 150);
  }, 300);

  return socket;
}
