package com.bookeatinglion.ai.wiki.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.ai.client.EmbeddingClient;
import com.bookeatinglion.ai.lion.domain.GrowthStage;
import com.bookeatinglion.ai.lion.domain.Lion;
import com.bookeatinglion.ai.lion.repository.LionRepository;
import com.bookeatinglion.ai.wiki.domain.FedBook;
import com.bookeatinglion.ai.wiki.port.VectorIndexPort;
import com.bookeatinglion.ai.wiki.port.VectorIndexPort.VectorRecord;
import com.bookeatinglion.ai.wiki.repository.FedBookRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FeedServiceTest {

    private static final String MEMBER_ID = "test-cognito-sub-1";
    private static final Long BOOK_ID = 10L;
    private static final String BOOK_TITLE = "책 제목";

    private FedBookRepository fedBookRepository;
    private LionRepository lionRepository;
    private FedBookCache fedBookCache;
    private EmbeddingClient embedding;
    private VectorIndexPort vectorIndex;
    private FeedService service;
    private Lion lion;

    @BeforeEach
    void setUp() {
        fedBookRepository = mock(FedBookRepository.class);
        lionRepository = mock(LionRepository.class);
        fedBookCache = mock(FedBookCache.class);
        embedding = mock(EmbeddingClient.class);
        vectorIndex = mock(VectorIndexPort.class);
        lion = new Lion(MEMBER_ID);

        when(lionRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(lion));
        when(embedding.embed(anyString())).thenReturn(new float[EmbeddingClient.DIMENSION]);

        service = new FeedService(fedBookRepository, lionRepository, fedBookCache, embedding, vectorIndex);
    }

    @Test
    void 처음_먹이면_경험치가_오르고_캐시가_갱신된다() {
        when(fedBookRepository.existsById(any())).thenReturn(false);
        when(fedBookRepository.countByMemberId(MEMBER_ID)).thenReturn(1L);

        FeedService.LionStatus result = service.feed(MEMBER_ID, BOOK_ID, BOOK_TITLE, "이 책의 요약");

        assertThat(result.exp()).isEqualTo(Lion.EXP_PER_FEED);
        assertThat(result.fedBookCount()).isEqualTo(1L);
        verify(fedBookRepository).save(any(FedBook.class));
        verify(fedBookCache).add(MEMBER_ID, BOOK_ID);
    }

    /** 메모는 임베딩 1회 + 결정적 키(회원×도서)로 바로 적재된다 — 책 본문과 달리 배치가 없다. */
    @Test
    void 먹이면_메모_텍스트가_회원별_결정적_키로_적재된다() {
        when(fedBookRepository.existsById(any())).thenReturn(false);

        service.feed(MEMBER_ID, BOOK_ID, BOOK_TITLE, "이 책의 요약");

        ArgumentCaptor<List<VectorRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorIndex).put(captor.capture());
        VectorRecord record = captor.getValue().getFirst();
        assertThat(record.key()).isEqualTo(VectorIndexPort.memoKey(BOOK_ID, MEMBER_ID));
        assertThat(record.sourceType()).isEqualTo(VectorIndexPort.SOURCE_USER_SUMMARY);
        assertThat(record.memberId()).isEqualTo(MEMBER_ID);
        assertThat(record.text()).isEqualTo("이 책의 요약");
        assertThat(record.bookTitle()).isEqualTo(BOOK_TITLE);
    }

    /** 재시도나 더블클릭으로 레벨이 오르면 버그가 아니라 사고다. */
    @Test
    void 같은_책을_다시_먹여도_경험치가_오르지_않는다() {
        lion.gainExp(Lion.EXP_PER_FEED);
        when(fedBookRepository.existsById(any())).thenReturn(true);
        when(fedBookRepository.countByMemberId(MEMBER_ID)).thenReturn(1L);

        FeedService.LionStatus result = service.feed(MEMBER_ID, BOOK_ID, BOOK_TITLE, "이 책의 요약");

        assertThat(result.exp()).isEqualTo(Lion.EXP_PER_FEED);
        verify(fedBookRepository, never()).save(any());
        verify(fedBookCache, never()).add(any(), any());
    }

    /**
     * 메모를 고쳐 쓴 뒤 다시 먹이면(이미 먹인 상태) EXP는 안 오르지만, RAG가 아는 내용은
     * 최신이어야 하므로 벡터는 매번 다시 적재된다 — 결정적 키라 재적재가 곧 갱신이다.
     */
    @Test
    void 이미_먹인_메모를_재작성해도_벡터는_매번_갱신된다() {
        when(fedBookRepository.existsById(any())).thenReturn(true);

        service.feed(MEMBER_ID, BOOK_ID, BOOK_TITLE, "고친 요약");

        verify(vectorIndex, times(1)).put(any());
    }

    /** 계약 예시(3권 → exp 120, level 2)와 어긋나면 프론트의 레벨 표시가 틀어진다. */
    @Test
    void 세_권을_먹이면_레벨이_2_가_된다() {
        lion.gainExp(Lion.EXP_PER_FEED * 3);

        assertThat(lion.getExp()).isEqualTo(120);
        assertThat(lion.getLevel()).isEqualTo(2);
        assertThat(lion.getGrowthStage()).isEqualTo(GrowthStage.BABY);
    }

    @Test
    void 사자_상태를_정상_조회한다() {
        lion.gainExp(Lion.EXP_PER_FEED);
        when(fedBookRepository.countByMemberId(MEMBER_ID)).thenReturn(2L);

        FeedService.LionStatus status = service.getLionStatus(MEMBER_ID);

        assertThat(status.level()).isEqualTo(1);
        assertThat(status.exp()).isEqualTo(Lion.EXP_PER_FEED);
        assertThat(status.growthStage()).isEqualTo(GrowthStage.BABY);
        assertThat(status.fedBookCount()).isEqualTo(2L);
    }

    /** 조회가 상태를 만들면 안 된다 — GET 한 번에 lions 행이 생기는 것을 막는 회귀 테스트다. */
    @Test
    void 사자가_없으면_기본값을_주고_저장하지_않는다() {
        when(lionRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());
        when(fedBookRepository.countByMemberId(MEMBER_ID)).thenReturn(0L);

        FeedService.LionStatus status = service.getLionStatus(MEMBER_ID);

        assertThat(status.level()).isEqualTo(1);
        assertThat(status.exp()).isZero();
        assertThat(status.growthStage()).isEqualTo(GrowthStage.BABY);
        assertThat(status.fedBookCount()).isZero();
        verify(lionRepository, never()).save(any());
    }

    /** 레벨 2까지는 아직 아기 사자 구간이다(getLionTier와 동일한 경계). */
    @Test
    void 경험치_100_이상_200_미만이면_레벨_2이고_BABY_구간이다() {
        lion.gainExp(120); // level = 1 + 120/100 = 2

        assertThat(lion.getLevel()).isEqualTo(2);
        assertThat(lion.getGrowthStage()).isEqualTo(GrowthStage.BABY);
    }

    @Test
    void 레벨_3_4는_CUB_구간이다() {
        lion.gainExp(200); // level = 1 + 200/100 = 3

        assertThat(lion.getLevel()).isEqualTo(3);
        assertThat(lion.getGrowthStage()).isEqualTo(GrowthStage.CUB);
    }

    @Test
    void 레벨_5_이상은_ADULT_구간이다() {
        lion.gainExp(400); // level = 1 + 400/100 = 5

        assertThat(lion.getLevel()).isEqualTo(5);
        assertThat(lion.getGrowthStage()).isEqualTo(GrowthStage.ADULT);
    }
}
