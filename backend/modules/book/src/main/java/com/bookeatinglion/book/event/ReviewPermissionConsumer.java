package com.bookeatinglion.book.event;

import com.bookeatinglion.book.domain.ReviewPermission;
import com.bookeatinglion.book.repository.ReviewPermissionRepository;
import com.bookeatinglion.common.event.ReviewPermissionGranted;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * order-service 가 발행한 리뷰 권한을 자기 DB(catalog_db)에 적재한다.
 *
 * Phase 1.5 통합 게이트의 기준은 "주문 확정 → 리뷰 작성 가능까지 5초 이내"다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewPermissionConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final ReviewPermissionRepository reviewPermissionRepository;

    @Override
    @Transactional
    public void onMessage(MapRecord<String, String, String> record) {
        ReviewPermissionGranted event = ReviewPermissionGranted.fromMap(record.getValue());

        // 재전송(at-least-once)에 대비해 멱등 처리한다. PK 가 (memberId, orderItemId) 라
        // 이미 있으면 그대로 두면 된다 — 스냅샷이므로 덮어쓸 이유도 없다.
        boolean exists = reviewPermissionRepository.existsById(
                new com.bookeatinglion.book.domain.ReviewPermissionId(event.memberId(), event.orderItemId()));

        if (exists) {
            log.debug("이미 적재된 리뷰 권한 — 건너뜀: orderItemId={}", event.orderItemId());
            return;
        }

        reviewPermissionRepository.save(new ReviewPermission(
                event.memberId(),
                event.orderItemId(),
                event.bookId(),
                event.nickname(),
                LocalDateTime.parse(event.grantedAt())));

        log.info("리뷰 권한 적재: memberId={}, bookId={}", event.memberId(), event.bookId());
    }
}
