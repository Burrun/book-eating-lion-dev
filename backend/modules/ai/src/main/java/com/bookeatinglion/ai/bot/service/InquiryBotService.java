package com.bookeatinglion.ai.bot.service;

import com.bookeatinglion.ai.bot.domain.Faq;
import com.bookeatinglion.ai.bot.repository.FaqRepository;
import com.bookeatinglion.ai.client.LlmClient;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문의봇. faqs 를 근거로 LLM 이 1차 응답한다.
 *
 * <p>이 서비스가 CPU HPA 로 확장되지 않는 대표 사례다 — 요청 100건이 동시에 들어와도
 * 전부 외부 API 응답 대기로 블록되므로 CPU 는 5% 언저리에 머문다. CPU 70% 기준
 * HPA 는 영원히 트리거되지 않고 요청만 큐에 쌓이다 타임아웃된다.
 * 그래서 ai-bot 의 HPA 는 동시 요청 수 기준이고(k8s/ai/hpa-bot.yaml),
 * 그와 별개로 Bulkhead 스레드풀 격리는 무조건 적용한다.
 *
 * <p>1:1 문의 등록/답변 기능은 없앴다. 상담사가 없을 때는 문의 게시판으로 안내만 하고,
 * 문의 자체는 이 서비스가 보관하지 않는다 — 답변할 수단(관리자 화면)이 없는 채로
 * 데이터만 쌓으면 사용자는 답을 기다리는데 아무도 그 존재를 모른다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryBotService {

    private final FaqRepository faqRepository;
    private final LlmClient llmClient;

    @Bulkhead(name = "aiExternalApi")
    public String answer(String question) {
        List<Faq> faqs = faqRepository.findAllByOrderBySortOrderAsc();

        String knowledge = faqs.stream()
                .map(f -> "Q. %s\nA. %s".formatted(f.getQuestion(), f.getAnswer()))
                .collect(Collectors.joining("\n\n"));

        return llmClient.complete("아래 FAQ 만 근거로 답하고, 근거가 없으면 상담원 연결을 안내하라.\n" + knowledge, question);
    }
}
