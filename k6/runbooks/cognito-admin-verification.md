# Cognito ADMIN 권한 상승 → AI 모듈 인식 검증

## 이게 뭔지

`06-chat-concurrency.js`(그리고 `09-hpa-metric-comparison.js`의 전제)는 특정 Cognito
유저가 `ADMIN` 그룹에 있으면 AI 상담 채팅 모듈이 그 유저를 "상담사"로 인식한다고
**가정**한다. 이 가정이 실제로 맞는지 확인하는 절차가 지금까지 어디에도 없었다 —
`06-chat-concurrency.js`는 이 판정이 이미 성립돼 있다는 전제로 짜여 있고, 판정
자체가 깨져도 결과만 봐선 구분이 안 된다(예: `chat_claimed`가 0이면 "상담사 계정 수가
부족해서"인지 "ADMIN 인식이 안 돼서"인지 못 가른다).

**대상 코드**: `backend/modules/ai/.../ChatTicketController.java`

```java
private static boolean isAgent(Jwt jwt) {
    List<String> groups = jwt.getClaimAsStringList("cognito:groups");
    return groups != null && groups.contains("ADMIN");
}
```

이 판정은 티켓 발급 시점(`POST /api/ai/bot/chat/ticket`)에 한 번 확정돼서 티켓 안에
고정된다. WebSocket 연결 시점엔 다시 판정하지 않는다 — 그래서 검증도 "티켓 발급 →
WS 연결 → 첫 응답 프레임 확인" 순서로 해야 한다.

## 사전 정보 (dev, 2026-08-28 확인)

- User Pool: `lion-team3-dev` (`ap-northeast-2_NO7UZFNVw`)
- `ADMIN` 그룹은 이미 존재함(`aws cognito-idp list-groups`로 확인)

## 검증 절차

### 1. 대상 유저를 ADMIN 그룹에 추가

```bash
aws cognito-idp admin-add-user-to-group --region ap-northeast-2 \
  --user-pool-id ap-northeast-2_NO7UZFNVw \
  --username <유저 username(sub) 또는 email> \
  --group-name ADMIN
```

### 2. 로그인 → 티켓 발급

```bash
TOKEN=$(curl -s -X POST https://dev.ajttk.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"<유저>","password":"<비번>"}' | jq -r .data.accessToken)

curl -s -X POST https://dev.ajttk.com/api/ai/bot/chat/ticket \
  -H "Authorization: Bearer $TOKEN" | jq
```

### 3. WebSocket 연결 → 첫 프레임으로 판정 확인

`isAgent` 결과는 응답 바디에 안 보이므로(티켓 안에만 있음), 실제 판정은 WS 연결
직후 서버가 먼저 보내는 프레임으로 확인한다(`ChatWebSocketHandler.afterConnectionEstablished`):

| 받은 프레임 | 의미 |
|---|---|
| `{"type":"AGENT_READY","waiting":[...]}` | **상담사로 인식됨** — ADMIN 그룹 반영 정상 |
| `{"type":"JOINED","roomId":...,"state":"BOT",...}` | 아직 일반 고객으로 인식됨 — 실패 |

k6 없이 확인하려면 `websocat`이나 브라우저 개발자도구로 붙어봐도 되고, k6로 하려면
`06-chat-concurrency.js`의 `agentSession()`에 있는 `issueTicket()` + `new WebSocket(...)`
패턴을 그대로 재사용하면 된다.

### 4. 네거티브 테스트 — 반드시 같이 할 것

ADMIN 그룹이 **없는** 유저로 1~3번을 반복해서 `JOINED`가 나오는지 확인한다.
"권한 상승된 유저가 인식된다"만 확인하고 "권한 없는 유저는 안 되는지"를 안 보면
반쪽짜리 검증이다 — 권한 상승 로직이 아니라 아예 전원이 상담사로 취급되는 버그일
수도 있다.

### 5. 원복

테스트 끝나면 그룹에서 빼둘 것:

```bash
aws cognito-idp admin-remove-user-from-group --region ap-northeast-2 \
  --user-pool-id ap-northeast-2_NO7UZFNVw \
  --username <유저> \
  --group-name ADMIN
```

## 현재 상태 — ✅ 검증 완료 (2026-08-28, dev)

`tools/verify-agent-connect.js`로 실행, 결과:

- **AGENT_READY 수신(ADMIN 권한 인식): PASS**
- **상담사 연결 완료(ESCALATE→CLAIM): PASS**

전체 흐름(로그인 → 티켓 발급 → 상담사 AGENT_READY → 고객 JOINED/ESCALATE →
상담사 ROOM_WAITING/CLAIM → 고객 "상담사가 연결되었습니다")이 실제로 확인됨.

이 검증을 하는 과정에서 별개의 실제 버그(§0-14 — `k6/websockets`가 `sleep()` 중
WS 이벤트 콜백을 안 돌리는 문제)를 발견해서 `06-chat-concurrency.js`도 같이
고쳤다. 이제 06번을 실행해도 될 상태다(단 §0-12 WAF rate-limit은 여전히 별개로
해결돼야 함).

**부수 관찰(문제는 아님)**: 상담사가 CLAIM 직후 직접 받아야 할 `CLAIMED`
프레임(대화 이력 포함)이 이 실행에선 안 왔고 `ROOM_CLAIMED`(에이전트 채널
브로드캐스트)만 왔다. 검증 스크립트가 "연결됨" 신호를 받자마자 소켓을 바로 닫아서
타이밍이 겹쳤을 가능성 — 테스트 결과 자체엔 영향 없음.
