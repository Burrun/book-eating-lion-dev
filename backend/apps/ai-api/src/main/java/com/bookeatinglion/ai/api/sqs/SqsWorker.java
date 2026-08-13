package com.bookeatinglion.ai.api.sqs;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * SQS 롱폴링 루프. 큐가 여럿이어도 위험한 부분은 하나여야 해서 여기로 모았다.
 *
 * <p>🔴 <b>핸들러가 정상 반환했을 때만 메시지를 지운다.</b> 실패했는데 지우면 그 이벤트는
 * 영원히 사라진다 — 구매 이벤트라면 사용자가 산 책을 못 읽고, 인제스트라면 그 책이 영영
 * 검색되지 않는다. 지우지 않으면 가시성 시간 뒤 재배달되고, {@code maxReceiveCount} 를
 * 넘기면 DLQ 로 간다. 파싱 실패처럼 영원히 성공할 수 없는 메시지도 같은 경로로 DLQ 에
 * 쌓이는 게 맞다 — 조용히 버리면 아무도 모른다.
 *
 * <p>🔴 <b>폴링 예외로 루프를 끝내지 않는다.</b> 여기서 죽으면 파드는 살아 있는데 소비만
 * 조용히 멈춘다. 헬스체크도 통과하고 로그도 잠잠해서 가장 늦게 발견되는 고장이다.
 *
 * <p>상속용 베이스 클래스가 아니라 조립용 부품이다. 큐 URL·메시지 타입·동시성은 큐마다
 * 다르고(인제스트는 임베딩 스로틀링 때문에 1건씩, 구매는 더 올려도 된다) 그걸 공통화하면
 * 오히려 틀린다.
 */
public final class SqsWorker implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SqsWorker.class);

    /** 폴링 자체가 실패했을 때 쉬는 시간. 즉시 재시도하면 장애 중에 요청 과금만 늘어난다. */
    private static final int RETRY_DELAY_SECONDS = 5;

    private final SqsClient sqs;
    private final String name;
    private final String queueUrl;
    private final int waitTimeSeconds;
    private final int maxMessages;
    private final Consumer<Message> handler;

    private final ExecutorService worker;
    private volatile boolean running = true;

    public SqsWorker(
            SqsClient sqs,
            String name,
            String queueUrl,
            int waitTimeSeconds,
            int maxMessages,
            Consumer<Message> handler) {
        this.sqs = sqs;
        this.name = name;
        this.queueUrl = queueUrl;
        this.waitTimeSeconds = waitTimeSeconds;
        this.maxMessages = maxMessages;
        this.handler = handler;
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sqs-" + name);
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        log.info("SQS 리스너 시작 [{}]: {}", name, queueUrl);
        worker.submit(this::pollLoop);
    }

    private void pollLoop() {
        while (running) {
            try {
                List<Message> messages = sqs.receiveMessage(r -> r.queueUrl(queueUrl)
                                .maxNumberOfMessages(maxMessages)
                                .waitTimeSeconds(waitTimeSeconds))
                        .messages();
                messages.forEach(this::handleOne);
            } catch (RuntimeException e) {
                log.error("SQS 수신 실패 [{}] — 잠시 후 재시도한다", name, e);
                sleep();
            }
        }
    }

    private void handleOne(Message message) {
        try {
            handler.accept(message);
        } catch (Exception e) {
            // 삭제하지 않는다 → 재배달 → maxReceiveCount 초과 시 DLQ.
            log.error("메시지 처리 실패 [{}] — 메시지를 남긴다(재배달 대상). body={}", name, message.body(), e);
            return;
        }
        try {
            sqs.deleteMessage(r -> r.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
        } catch (RuntimeException e) {
            // 처리는 끝났는데 삭제만 실패한 경우다. 재배달되므로 핸들러가 멱등해야 한다 —
            // 두 리스너 모두 멱등하게 짜여 있다(인제스트는 delete-then-put, 구매는 PK 중복 확인).
            log.warn("메시지 삭제 실패 [{}] — 재배달될 수 있다. 핸들러 멱등성에 기댄다.", name, e);
        }
    }

    private static void sleep() {
        try {
            TimeUnit.SECONDS.sleep(RETRY_DELAY_SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        running = false;
        worker.shutdownNow();
    }
}
