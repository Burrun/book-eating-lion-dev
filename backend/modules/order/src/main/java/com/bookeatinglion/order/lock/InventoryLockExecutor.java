package com.bookeatinglion.order.lock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 다중 bookId 재고 차감을 위한 분산 락. bookId 오름차순으로 정렬한 뒤 순서대로 락을 획득한다 —
 * 두 주문이 [1,2] 와 [2,1] 순서로 락을 걸면 데드락이 나므로, 항상 같은 순서로 걸어야 한다.
 *
 * 단일 Redis 인스턴스 기준 락이다(RedissonConfig). "Redlock" 이라 부르지만 다중 노드 쿼럼을
 * 요구하는 원 알고리즘의 엄밀한 구현은 아니다 — 이 프로젝트의 실제 인프라(ElastiCache 단일
 * 클러스터)와 일치시킨 선택이다.
 */
@Component
@RequiredArgsConstructor
public class InventoryLockExecutor {

    private static final String KEY_PREFIX = "inventory:lock:";
    private static final long WAIT_SECONDS = 5;
    private static final long LEASE_SECONDS = 10;

    private final RedissonClient redissonClient;

    public <T> T executeWithLock(List<Long> bookIds, Supplier<T> action) {
        List<Long> sortedBookIds = bookIds.stream().distinct().sorted().toList();
        List<RLock> acquiredLocks = new ArrayList<>();

        try {
            for (Long bookId : sortedBookIds) {
                RLock lock = redissonClient.getLock(KEY_PREFIX + bookId);
                boolean acquired = lock.tryLock(WAIT_SECONDS, LEASE_SECONDS, TimeUnit.SECONDS);
                if (!acquired) {
                    throw new InventoryLockAcquisitionException(bookId);
                }
                acquiredLocks.add(lock);
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InventoryLockAcquisitionException(sortedBookIds);
        } finally {
            for (int i = acquiredLocks.size() - 1; i >= 0; i--) {
                RLock lock = acquiredLocks.get(i);
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }
}
