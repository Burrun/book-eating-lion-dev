package com.bookeatinglion.ai.bot.chat;

/** 발화 주체. 화면에서 말풍선을 가르는 기준이자, 상담사가 합류했을 때 앞선 대화를 읽는 근거다. */
public enum ChatRole {
    USER,
    BOT,
    AGENT,
    /** 상태 안내(상담사 연결됨, 상담사 없음 등). 서버가 만든 문장이다. */
    SYSTEM
}
