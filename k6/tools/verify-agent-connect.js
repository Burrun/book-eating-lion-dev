// 1회성 수동 검증 도구 — 부하테스트가 아니다(vus=1, iterations=1).
// 목적: (1) ADMIN 그룹 상승이 AI 모듈에서 인식되는지, (2) 고객 ESCALATE → 상담사 CLAIM
// → "상담사가 연결되었습니다" 상태까지 실제로 도달하는지 확인.
// 화면(프론트)은 아직 없어서 API/WebSocket을 직접 열어 콘솔 로그로 확인한다
// (runbooks/cognito-admin-verification.md 참고).
//
// ⚠️ sleep()을 안 쓴다 — k6/websockets(stable)는 sleep() 도는 동안 WS 이벤트 콜백을
// 아예 안 돌린다(2026-08-28 실측 확인). 그래서 흐름 전체를 setTimeout 기반으로 짰다:
// default()는 핸들러만 걸고 바로 리턴하고, 종료는 성공/실패 판정이 나는 순간이나
// 20초 타임아웃 중 먼저 오는 쪽이 처리한다.
//
// 실행:
//   k6 run -e AI_URL=https://dev.ajttk.com -e WS_URL=wss://dev.ajttk.com \
//          -e CUSTOMER_EMAIL=... -e CUSTOMER_PASSWORD=... \
//          -e AGENT_EMAIL=...    -e AGENT_PASSWORD=... \
//          k6/tools/verify-agent-connect.js
import http from 'k6/http';
import { WebSocket } from 'k6/websockets';

const AI_URL = __ENV.AI_URL;
const WS_URL = __ENV.WS_URL;
const CUSTOMER_EMAIL = __ENV.CUSTOMER_EMAIL;
const CUSTOMER_PASSWORD = __ENV.CUSTOMER_PASSWORD;
const AGENT_EMAIL = __ENV.AGENT_EMAIL;
const AGENT_PASSWORD = __ENV.AGENT_PASSWORD;

if (!AI_URL || !WS_URL || !CUSTOMER_EMAIL || !CUSTOMER_PASSWORD || !AGENT_EMAIL || !AGENT_PASSWORD) {
  throw new Error('AI_URL/WS_URL/CUSTOMER_EMAIL/CUSTOMER_PASSWORD/AGENT_EMAIL/AGENT_PASSWORD 전부 -e로 넘길 것.');
}

export const options = { vus: 1, iterations: 1 };

function login(email, password) {
  const res = http.post(`${AI_URL}/api/auth/login`, JSON.stringify({ email, password }), {
    headers: { 'Content-Type': 'application/json' },
  });
  if (res.status !== 200) throw new Error(`로그인 실패(${email}): ${res.status} ${res.body}`);
  return res.json('data.accessToken');
}

function issueTicket(token) {
  const res = http.post(`${AI_URL}/api/ai/bot/chat/ticket`, null, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (res.status !== 200) throw new Error(`티켓 발급 실패: ${res.status} ${res.body}`);
  return res.json('data.ticket');
}

export default function () {
  let agentRecognized = false;
  let connected = false;
  let finished = false;

  console.log('1. 로그인...');
  const customerToken = login(CUSTOMER_EMAIL, CUSTOMER_PASSWORD);
  const agentToken = login(AGENT_EMAIL, AGENT_PASSWORD);
  console.log('   OK');

  console.log('2. 티켓 발급...');
  const customerTicket = issueTicket(customerToken);
  const agentTicket = issueTicket(agentToken);
  console.log('   OK');

  console.log('3. 상담사 소켓 먼저 연결 (대기방 알림을 받아야 하니 고객보다 먼저)...');
  const agentSocket = new WebSocket(`${WS_URL}/ws/ai/chat?ticket=${agentTicket}`);
  let customerSocket = null;

  function finish() {
    if (finished) return;
    finished = true;
    console.log('=== 결과 ===');
    console.log('AGENT_READY 수신(ADMIN 권한 인식): ' + (agentRecognized ? 'PASS' : 'FAIL'));
    console.log('상담사 연결 완료(ESCALATE→CLAIM): ' + (connected ? 'PASS' : 'FAIL'));
    agentSocket.close();
    if (customerSocket) customerSocket.close();
  }

  agentSocket.addEventListener('message', (e) => {
    console.log('   [상담사 수신] ' + e.data);
    const f = JSON.parse(e.data);
    const d = f.data;
    if (!d) return;
    if (d.type === 'AGENT_READY') {
      agentRecognized = true;
      console.log('   >>> AGENT_READY 수신 — ADMIN 권한 인식됨 (PASS)');
    } else if (d.type === 'ROOM_WAITING') {
      console.log('   >>> 대기방 발견(' + d.roomId + '), CLAIM 전송');
      agentSocket.send(JSON.stringify({ type: 'CLAIM', roomId: d.roomId }));
    } else if (d.type === 'CLAIMED') {
      console.log('   >>> CLAIMED 성공(' + d.roomId + ')');
    }
  });
  agentSocket.addEventListener('error', (e) => console.log('   [상담사 소켓 에러] ' + JSON.stringify(e)));

  // sleep(2) 대신 setTimeout — 상담사가 AGENT_READY를 받을 시간을 준 뒤 고객 소켓을 연다.
  setTimeout(() => {
    console.log('4. 고객 소켓 연결...');
    customerSocket = new WebSocket(`${WS_URL}/ws/ai/chat?ticket=${customerTicket}`);

    customerSocket.addEventListener('message', (e) => {
      console.log('   [고객 수신] ' + e.data);
      const f = JSON.parse(e.data);
      const d = f.data;
      if (!d) return;
      if (d.type === 'JOINED') {
        console.log('   >>> JOINED(state=' + d.state + '), ESCALATE 전송');
        customerSocket.send(JSON.stringify({ type: 'ESCALATE' }));
      } else if (d.type === 'NO_AGENT') {
        console.log('   >>> NO_AGENT — 상담사가 온라인으로 안 잡힘 (FAIL, AGENT_READY 못 받았을 가능성)');
        finish();
      } else if (d.role === 'SYSTEM' && /연결되었습니다/.test(d.text || '')) {
        connected = true;
        console.log('   >>> 상담사 연결 확인됨 (PASS)');
        finish();
      }
    });
    customerSocket.addEventListener('error', (e) => console.log('   [고객 소켓 에러] ' + JSON.stringify(e)));
  }, 2000);

  setTimeout(finish, 20000); // 안전장치 — 20초 안에 못 끝나면 여기서 결과를 출력하고 정리한다
}
