package com.bookeatinglion.ai.bot.chat;

/**
 * 핸드셰이크 티켓에 담기는 신원.
 *
 * <p>🔴 <b>이 값이 이후 모든 권한 판단의 유일한 근거다.</b> WebSocket 핸들러 안에는
 * SecurityContext 가 없어(스레드가 다르다) {@code SecurityUtils} 를 부를 수 없다.
 * 클라이언트가 프레임에 담아 보낸 필드는 신원으로 쓰지 않는다 —
 * {@code {"type":"SAY","memberId":"남의sub"}} 를 막는 유일한 방법이다.
 *
 * @param memberId Cognito sub. {@code purchased_books.member_id} 와 같은 값이다.
 * @param agent 상담사 여부. 티켓 발급 시점에 JWT 클레임으로 판정해 굳혀 넣는다 —
 *     판정 지점이 둘이면 언젠가 두 곳의 규칙이 갈라진다.
 */
public record ChatIdentity(String memberId, String nickname, boolean agent) {}
