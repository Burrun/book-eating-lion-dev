package com.bookeatinglion.order.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class InventoryLockExecutorTest {

    @Mock
    private RedissonClient redissonClient;

    @InjectMocks
    private InventoryLockExecutor inventoryLockExecutor;

    @Test
    void bookId_오름차순으로_락을_획득하고_역순으로_해제한다() throws InterruptedException {
        RLock lock1 = mockLock();
        RLock lock2 = mockLock();
        RLock lock3 = mockLock();
        when(redissonClient.getLock("inventory:lock:1")).thenReturn(lock1);
        when(redissonClient.getLock("inventory:lock:2")).thenReturn(lock2);
        when(redissonClient.getLock("inventory:lock:3")).thenReturn(lock3);

        String result = inventoryLockExecutor.executeWithLock(List.of(3L, 1L, 2L), () -> "done");

        assertThat(result).isEqualTo("done");
        InOrder inOrder = inOrder(lock1, lock2, lock3);
        inOrder.verify(lock1).tryLock(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any(TimeUnit.class));
        inOrder.verify(lock2).tryLock(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any(TimeUnit.class));
        inOrder.verify(lock3).tryLock(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any(TimeUnit.class));
        inOrder.verify(lock3).unlock();
        inOrder.verify(lock2).unlock();
        inOrder.verify(lock1).unlock();
    }

    @Test
    void 락_획득에_실패하면_예외를_던지고_이미_잡은_락을_해제한다() throws InterruptedException {
        RLock lock1 = mockLock();
        RLock lock2 = mock(RLock.class);
        when(redissonClient.getLock("inventory:lock:1")).thenReturn(lock1);
        when(redissonClient.getLock("inventory:lock:2")).thenReturn(lock2);
        when(lock2.tryLock(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any(TimeUnit.class)))
                .thenReturn(false);

        assertThatThrownBy(() -> inventoryLockExecutor.executeWithLock(List.of(1L, 2L), () -> "unreachable"))
                .isInstanceOf(InventoryLockAcquisitionException.class);

        InOrder inOrder = inOrder(lock1);
        inOrder.verify(lock1).unlock();
    }

    private RLock mockLock() throws InterruptedException {
        RLock lock = mock(RLock.class);
        when(lock.tryLock(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any(TimeUnit.class)))
                .thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        return lock;
    }
}
