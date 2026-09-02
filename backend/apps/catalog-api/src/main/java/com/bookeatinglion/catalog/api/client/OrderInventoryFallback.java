package com.bookeatinglion.catalog.api.client;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * order-service 가 죽어도 도서 목록·상세는 뜬다. 재고 영역만 degrade 된다(§5 대응).
 *
 * 이게 재고 소유권을 order 로 옮긴 대가이고, 동시에 그 대가가 감당 가능한 수준임을
 * 보여주는 자리다 — order 가 죽으면 어차피 구매도 불가능하므로, 재고 숫자가 잠깐
 * 안 보이는 것은 치명적이지 않다.
 */
@Slf4j
@Component
public class OrderInventoryFallback implements OrderInventoryClient {

    @Override
    public List<InventoryView> findByBookIds(List<Long> bookIds) {
        log.warn("order-service 재고 조회 실패 — 재고 표시를 degrade 합니다. bookIds={}", bookIds);
        return List.of();
    }

    @Override
    public InventoryView restock(Long bookId, RestockRequest request) {
        // 입고는 degrade 하면 안 된다. 조용히 성공한 척하면 재고가 영영 안 들어간다.
        // 관리자에게 에러를 돌려주고 재시도하게 한다(판단 ③ ①).
        throw new IllegalStateException("order-service 에 입고를 반영하지 못했습니다. 재시도하세요. bookId=" + bookId);
    }
}
