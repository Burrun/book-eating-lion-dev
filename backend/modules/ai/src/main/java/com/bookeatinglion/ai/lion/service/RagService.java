package com.bookeatinglion.ai.lion.service;

import com.bookeatinglion.ai.client.EmbeddingClient;
import com.bookeatinglion.ai.client.LlmClient;
import com.bookeatinglion.ai.lion.domain.LionMemory;
import com.bookeatinglion.ai.lion.repository.LionMemoryRepository;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RAG 파이프라인. 단계별로 부하가 어디서 나는지가 다르다(판단 ④):
 *
 *   ① 질의 임베딩 생성 — 외부 API(Bedrock)  → I/O 바운드
 *   ② 벡터 검색       — Aurora(pgvector)   → Pod 밖. Pod CPU 와 무관
 *   ③ 컨텍스트 조립    — 이 Pod            → 중간
 *   ④ LLM 호출        — 외부 API           → I/O 바운드
 *
 * ②가 애초에 Pod 밖이라는 게 핵심이다. 벡터 저장소를 무엇으로 바꾸든 이 Pod 의
 * CPU 는 줄지 않는다. 부하는 ①과 ③에서 나오고, ①이 외부 API 라 결국 I/O 바운드다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RagService {

    private static final int TOP_K = 5;

    private final LionMemoryRepository lionMemoryRepository;
    private final EmbeddingClient embeddingClient;
    private final LlmClient llmClient;

    /**
     * Bulkhead 로 외부 호출 스레드를 격리한다. 이게 없으면 문의봇이 폭주할 때
     * 스레드를 전부 뺏겨 RAG 가 같이 죽는다 — 판단 ④가 "필수"로 못박은 항목이다.
     */
    @Bulkhead(name = "aiExternalApi")
    public String ask(Long lionId, String question) {
        String queryEmbedding = embeddingClient.embed(question);

        List<LionMemory> similar = lionMemoryRepository.findSimilar(lionId, queryEmbedding, TOP_K);

        String context = similar.stream()
                .map(m -> "- [%s] %s".formatted(m.getBookTitle(), m.getMemo()))
                .collect(Collectors.joining("\n"));

        return llmClient.complete("너는 사용자의 독서 기록을 기억하는 라이언이다. 아래 기록만 근거로 답하라.\n" + context, question);
    }

    @Transactional
    @Bulkhead(name = "aiExternalApi")
    public LionMemory remember(LionMemory memory) {
        String text = (memory.getMemo() == null ? "" : memory.getMemo()) + "\n"
                + (memory.getQuoteText() == null ? "" : memory.getQuoteText());

        memory.attachEmbedding(embeddingClient.embed(text), EmbeddingClient.MODEL_ID, EmbeddingClient.DIMENSION);

        // 기록 저장과 임베딩 저장이 같은 트랜잭션이다. 벡터를 별도 저장소에 뒀다면
        // 여기서 Saga 가 필요했을 것이다(판단 ②의 공통 기각 사유).
        return lionMemoryRepository.save(memory);
    }
}
