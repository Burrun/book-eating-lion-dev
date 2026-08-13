package com.bookeatinglion.ai.bot.chat;

/** 방 해시의 매핑. 필드가 없으면 null 이다 — 상담사가 아직 없으면 agentId 가 비어 있다. */
public record ChatRoom(
        String roomId, ChatState state, String memberId, String nickname, String agentId, String agentNickname) {}
