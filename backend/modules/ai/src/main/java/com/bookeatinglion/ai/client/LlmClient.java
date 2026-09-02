package com.bookeatinglion.ai.client;

/** 문의봇·라이언 응답 생성용 LLM 호출. 외부 API 이므로 호출당 1~3초의 I/O 대기가 발생한다. */
public interface LlmClient {

    String complete(String systemPrompt, String userPrompt);
}
