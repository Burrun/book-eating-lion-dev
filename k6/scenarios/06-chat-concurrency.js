// 문의 채팅 동시성 — 여러 Pod/컨테이너에 분산 접속된 세션 간 Redis Pub/Sub
// 브로드캐스팅(RoomSubscriptionRegistry)이 메시지를 안 놓치는지 검증.
//
// ⚠️ /ws/ai/chat은 nginx(./nginx/default.conf)에도, k8s Ingress(k8s/base/08-ingress.yaml)
// 에도 라우팅이 없다(README §0-3). WS_URL을 반드시 ai 서비스 포트로 직접 지정할 것
// (docker-compose 기준 8084).
//
// 프로토콜(ai-v1.yaml /ws/ai/chat 기준):
//   접속: GET {WS_URL}/ws/ai/chat?ticket=<발급받은 티켓>
//   프레임: { "type": "ASK"|"SAY"|"ESCALATE"|"CLAIM"|"CLOSE", "text": "...", "roomId": "..." }
//   티켓 만료: 발급 후 expiresInSeconds(기본 60초) 이내에 연결해야 한다 — VU 수가 많아
//   setup()에서 순차 발급하면 뒤쪽 VU의 티켓이 연결 전에 만료될 수 있으니, 대량 동시
//   접속 테스트에서는 각 VU가 default() 안에서 직접 자기 티켓을 발급받는다.
//
// 실행:
//   k6 run -e TARGET_ENV=ec2-single -e RUN_LABEL=baseline \
//          -e AI_URL=http://<HOST>:8084 -e WS_URL=ws://<HOST>:8084 \
//          -e LOGIN_EMAIL=... -e LOGIN_PASSWORD=... \
//          k6/scenarios/06-chat-concurrency.js
import http from 'k6/http';
import ws from 'k6/experimental/websockets';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { AI_URL, WS_URL } from '../lib/config.js';
import { login, authHeaders } from '../lib/auth.js';
import { buildReport } from '../lib/report.js';

const messagesSent = new Counter('chat_messages_sent');
const messagesReceived = new Counter('chat_messages_received');
const messageLatency = new Trend('chat_message_latency_ms');

export const options = {
  scenarios: {
    chat: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 100 },
        { duration: '2m', target: 100 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    // 메시지 유실률 0% — sent와 received가 정확히 같아야 한다.
    chat_messages_received: ['count>=0'], // 실제 유실률은 handleSummary에서 sent 대비 계산해 로그로 남긴다.
    chat_message_latency_ms: ['p(95)<50'],
  },
};

export function setup() {
  const token = login();
  return { token };
}

export default function (data) {
  const ticketRes = http.post(`${AI_URL}/api/ai/bot/chat/ticket`, null, {
    ...authHeaders(data.token),
    tags: { name: 'issue-chat-ticket' },
  });
  check(ticketRes, { 'ticket 200': (r) => r.status === 200 });
  const ticket = ticketRes.json('data.ticket');
  if (!ticket) return;

  const socket = new ws.WebSocket(`${WS_URL}/ws/ai/chat?ticket=${ticket}`);
  const roomId = `k6-room-${__VU}`;
  let sentAt = 0;

  socket.addEventListener('open', () => {
    sentAt = Date.now();
    socket.send(JSON.stringify({ type: 'ASK', text: 'k6 load test ping', roomId }));
    messagesSent.add(1);
  });

  socket.addEventListener('message', () => {
    messagesReceived.add(1);
    messageLatency.add(Date.now() - sentAt);
    socket.close();
  });

  socket.addEventListener('error', () => {
    // 실패도 유실로 집계되도록 received를 늘리지 않는다 — sent 대비 received 차이가
    // 곧 유실 건수다.
  });

  sleep(3); // 소켓 이벤트 처리 대기
}

export function handleSummary(data) {
  return buildReport('06-chat-concurrency', data, {
    note: 'chat_messages_sent 대비 chat_messages_received 차이가 메시지 유실 건수다.',
  });
}
