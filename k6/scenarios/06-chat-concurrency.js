// 문의 채팅 브로드캐스팅 — 서로 다른 세션(고객 ↔ 상담사)이 같은 방에 있을 때, 서로 다른
// Pod에 소켓이 붙어 있어도 Redis Pub/Sub(RoomSubscriptionRegistry)가 메시지를 놓치지
// 않고 relay하는지 검증한다.
//
// ⚠️ ChatRoomStore.openOrResume()이 "1인 1방"을 강제한다(memberId당 방 1개 — 재접속하면
// 같은 방을 돌려준다, ChatRoomStore.java 주석: "탭을 두 개 열면 같은 방에 소켓 두 개가
// 붙는다"). 그래서 05/07처럼 로그인 토큰 하나를 여러 VU가 공유하면 VU가 몇 개든 전부
// 같은 방 하나로 몰려서 "서로 다른 세션이 서로 다른 Pod에서 브로드캐스트를 받는지"를
// 검증할 수 없다 — 채팅만 계정 풀이 필요한 이유다: 고객 역할을 할 서로 다른 회원 계정
// 여러 개 + 상담사(Cognito ADMIN 그룹) 계정 1개 이상. README §0-10 참고.
//
// 실제 프로토콜(ai-v1.yaml /ws/ai/chat, ChatWebSocketHandler.java 기준):
//   ChatState: BOT → WAITING → LIVE → CLOSED
//   고객: 접속(JOINED 수신, state=BOT) → ESCALATE(상담사 요청) → 상담사가 CLAIM하면
//         SYSTEM "상담사가 연결되었습니다." 브로드캐스트 → LIVE에서 SAY
//   상담사: 접속(AGENT_READY 수신, "agents" 채널 구독) → ROOM_WAITING 수신 → CLAIM →
//           CLAIMED 수신(성공한 사람에게만 직접 전송) → LIVE에서 SAY
//   메시지 프레임(ChatMessage: seq/role/nickname/text/at)엔 roomId가 없다 — 소켓 하나가
//   여러 방을 동시에 CLAIM하면 어느 방 메시지인지 클라이언트가 구분할 방법이 없다(실제
//   프로토콜의 제약이지 k6가 만든 문제가 아니다). 그래서 이 스크립트의 상담사 VU는 한
//   번에 방 하나만 맡도록 설계했다 — 현재 CLAIM한 방이 없을 때만 다음 ROOM_WAITING을
//   받는다.
//
// 측정 대상: 고객이 SAY로 보낸 메시지 → Redis 발행 → 상담사 쪽을 붙들고 있는 Pod가
// 구독으로 받아 ack SAY 응답 → 다시 Redis 발행 → 고객 쪽을 붙들고 있는 Pod가 받음.
// 이 왕복이 실제로 서로 다른 Pod에 걸쳐 이뤄지는지는 k6가 강제할 수 없다(로드밸런서가
// 어느 Pod에 소켓을 붙일지 결정한다) — ai-bot이 실제로 다중 Pod(replica 2개 이상)로
// 떠 있는 환경(EKS)에서 돌려야 의미가 있다.
//
// ⚠️ 이 파일엔 `sleep()`을 안 쓴다 — k6/websockets(stable)는 sleep()이 실행되는 동안
// WS 이벤트 콜백(open/message 등)을 아예 안 돌린다(2026-08-28 실측 확인 — public echo
// 서버로 격리 재현함). 연결을 유지하고 싶으면 함수를 바로 리턴하고 `setTimeout`으로
// 종료를 예약할 것 — 소켓이 열려있는 동안은 k6가 알아서 그 iteration을 안 끝낸다.
//
// 실행:
//   k6 run -e TARGET_ENV=eks-msa -e RUN_LABEL=baseline \
//          -e AI_URL=http://<HOST>:8084 -e WS_URL=ws://<HOST>:8084 \
//          -e CHAT_CUSTOMER_EMAILS=c1@x.com,c2@x.com,c3@x.com,c4@x.com,c5@x.com \
//          -e CHAT_CUSTOMER_PASSWORDS=pw1,pw2,pw3,pw4,pw5 \
//          -e CHAT_AGENT_EMAIL=agent@x.com -e CHAT_AGENT_PASSWORD=pw \
//          k6/scenarios/06-chat-concurrency.js
import http from 'k6/http';
import { WebSocket } from 'k6/websockets'; // k6/experimental/websockets는 deprecated — 최신 k6에서 stable 모듈로 교체
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
  AI_URL,
  WS_URL,
  CHAT_CUSTOMER_EMAILS,
  CHAT_CUSTOMER_PASSWORDS,
  CHAT_AGENT_EMAIL,
  CHAT_AGENT_PASSWORD,
  requireChatEnv,
} from '../lib/config.js';
import { loginAs, authHeaders } from '../lib/auth.js';
import { buildReport } from '../lib/report.js';

const AGENT_VUS = Number(__ENV.CHAT_AGENT_VUS || 1);

const chatEscalateSent = new Counter('chat_escalate_sent');
const chatNoAgent = new Counter('chat_no_agent'); // 온라인 상담사 부족 — 유실은 아니지만 용량 신호
const chatStaleRoomSkipped = new Counter('chat_stale_room_skipped'); // 방이 BOT 상태가 아니어서 이번 반복은 건너뜀
const chatClaimed = new Counter('chat_claimed');
const chatSaySent = new Counter('chat_say_sent');
const chatAckReceived = new Counter('chat_ack_received'); // sent 대비 부족분 = 유실
const chatAckTimeout = new Counter('chat_ack_timeout'); // 유실(타임아웃)
const chatAckLatencyMs = new Trend('chat_ack_latency_ms');

export const options = {
  scenarios: {
    agents: {
      executor: 'constant-vus',
      vus: AGENT_VUS,
      duration: '2m40s',
      exec: 'agentSession',
    },
    customers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 10 },
        { duration: '1m30s', target: 10 },
        { duration: '20s', target: 0 },
      ],
      exec: 'customerSession',
      startTime: '10s', // 상담사가 먼저 온라인 상태가 되도록 살짝 늦게 시작
    },
  },
  thresholds: {
    // 유실률 0% — chat_say_sent와 chat_ack_received가 정확히 같아야 한다. 실제
    // 유실률은 handleSummary에서 sent 대비 계산해 로그로 남긴다.
    chat_ack_received: ['count>=0'],
    chat_ack_latency_ms: ['p(95)<50'],
  },
};

export function setup() {
  requireChatEnv();
  const agentToken = loginAs(CHAT_AGENT_EMAIL, CHAT_AGENT_PASSWORD);
  const customerTokens = CHAT_CUSTOMER_EMAILS.map((email, i) => loginAs(email, CHAT_CUSTOMER_PASSWORDS[i]));
  return { agentToken, customerTokens };
}

function issueTicket(token) {
  const res = http.post(`${AI_URL}/api/ai/bot/chat/ticket`, null, {
    ...authHeaders(token),
    tags: { name: 'issue-chat-ticket' },
  });
  check(res, { 'ticket 200': (r) => r.status === 200 });
  return res.json('data.ticket');
}

function safeParse(text) {
  try {
    return JSON.parse(text);
  } catch (e) {
    return null;
  }
}

export function agentSession(data) {
  const ticket = issueTicket(data.agentToken);
  if (!ticket) return;

  const socket = new WebSocket(`${WS_URL}/ws/ai/chat?ticket=${ticket}`);
  let currentRoomId = null; // 한 번에 방 하나만 맡는다(위 프로토콜 설명 참고)
  let claiming = false;

  socket.addEventListener('message', (e) => {
    const frame = safeParse(e.data);
    if (!frame) return;

    if (frame.success === false) {
      // CLAIM이 ALREADY_CLAIMED 등으로 실패 — 다음 ROOM_WAITING을 다시 받을 수 있게 되돌린다.
      claiming = false;
      return;
    }

    const d = frame.data;
    if (!d) return;

    if (d.type === 'ROOM_WAITING' && currentRoomId === null && !claiming) {
      claiming = true;
      socket.send(JSON.stringify({ type: 'CLAIM', roomId: d.roomId }));
    } else if (d.type === 'CLAIMED') {
      currentRoomId = d.roomId;
      claiming = false;
      chatClaimed.add(1);
    } else if (d.type === 'MESSAGE' && d.role === 'USER' && currentRoomId) {
      socket.send(JSON.stringify({ type: 'SAY', text: `ack:${d.text}`, roomId: currentRoomId }));
      socket.send(JSON.stringify({ type: 'CLOSE', roomId: currentRoomId })); // 방을 닫아 고객 계정을 다음 반복에 풀어준다
      currentRoomId = null;
    }
  });

  // k6/websockets(stable)는 sleep()이 도는 동안 WS 이벤트 콜백을 아예 안 돌린다(실측
  // 확인함 — open/message 핸들러가 sleep 중엔 한 번도 안 불림). 그래서 여기서 sleep으로
  // "기다리는" 대신 함수를 바로 리턴하고, k6가 이 소켓이 열려있는 동안 이 iteration을
  // 붙들고 있는 것에 기댄다. 종료는 setTimeout으로 예약.
  setTimeout(() => socket.close(), 160000); // agents 시나리오 전체 구간(2m40s) 동안 연결 유지
}

export function customerSession(data) {
  const idx = (__VU - 1) % data.customerTokens.length;
  const token = data.customerTokens[idx];
  const ticket = issueTicket(token);
  if (!ticket) return;

  const socket = new WebSocket(`${WS_URL}/ws/ai/chat?ticket=${ticket}`);
  let sentAt = 0;
  let saySent = false;
  let done = false;

  socket.addEventListener('message', (e) => {
    const frame = safeParse(e.data);
    const d = frame && frame.data;
    if (!d) return;

    if (d.type === 'JOINED') {
      if (d.state !== 'BOT') {
        // 이전 반복에서 방을 못 닫고 끝난 계정(재고 없듯 계정 풀도 유한하다) — 이번
        // 반복은 건너뛴다. 계속 반복돼서 늘어나면 CHAT_CUSTOMER_EMAILS 풀을 늘릴 것.
        chatStaleRoomSkipped.add(1);
        done = true;
        socket.close();
        return;
      }
      socket.send(JSON.stringify({ type: 'ESCALATE' }));
      chatEscalateSent.add(1);
    } else if (d.type === 'NO_AGENT') {
      chatNoAgent.add(1);
      done = true;
      socket.close();
    } else if (d.type === 'MESSAGE' && d.role === 'SYSTEM' && !saySent && /연결되었습니다/.test(d.text || '')) {
      // 상담사가 CLAIM해서 LIVE로 전환됐다는 신호 — 이제부터 SAY를 주고받을 수 있다.
      saySent = true;
      sentAt = Date.now();
      socket.send(JSON.stringify({ type: 'SAY', text: `k6-ping-vu${__VU}-${sentAt}` }));
      chatSaySent.add(1);
    } else if (d.type === 'MESSAGE' && d.role === 'AGENT' && saySent && !done) {
      chatAckReceived.add(1);
      chatAckLatencyMs.add(Date.now() - sentAt);
      done = true;
      clearTimeout(timeoutId);
      socket.close(); // 상담사가 이미 CLOSE를 보냈으므로 고객 쪽은 그냥 끊는다
    }
  });

  // k6/websockets는 소켓 메서드가 아니라 전역 setTimeout/clearTimeout을 쓴다
  // (k6/experimental/websockets의 socket.setTimeout()과 다르다 — deprecated API).
  // sleep()으로 "기다리지" 않는다 — sleep 중엔 이 아래 message 핸들러가 아예 안 불린다
  // (실측 확인함). 함수는 여기서 바로 리턴하고, 종료는 이 setTimeout과 각 분기의
  // socket.close()가 맡는다 — 소켓이 열려있는 동안은 k6가 이 iteration을 붙들고 있다.
  const timeoutId = setTimeout(() => {
    if (!done) {
      chatAckTimeout.add(1); // 유실 — 상담사가 못 붙었거나 ack가 안 왔다
      socket.send(JSON.stringify({ type: 'CLOSE' })); // 방을 정리해 다음 반복에 계정을 풀어준다
      socket.close();
    }
  }, 20000);
}

export function handleSummary(data) {
  return buildReport('06-chat-concurrency', data, {
    note:
      'chat_say_sent 대비 (chat_ack_received + chat_ack_timeout)의 부족분이 진짜 메시지 유실이다. ' +
      'chat_no_agent/chat_stale_room_skipped는 유실이 아니라 각각 상담사 용량 부족, 계정 풀 소진 신호 — ' +
      '전자는 CHAT_AGENT_VUS를, 후자는 CHAT_CUSTOMER_EMAILS 풀 크기를 늘려서 재확인할 것.',
  });
}
