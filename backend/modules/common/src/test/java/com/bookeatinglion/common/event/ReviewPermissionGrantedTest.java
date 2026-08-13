package com.bookeatinglion.common.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ReviewPermissionGrantedTest {

    @Test
    void Cognito_sub를_문자열_회원_ID로_직렬화하고_복원한다() {
        ReviewPermissionGranted event = new ReviewPermissionGranted(
                "34589d0c-c0f1-702d-0477-6afacc060eda", 10L, 20L, "테스트유저", "2026-08-12T15:00:00");

        Map<String, String> payload = event.toMap();
        ReviewPermissionGranted restored = ReviewPermissionGranted.fromMap(payload);

        assertThat(payload.get("memberId")).isEqualTo("34589d0c-c0f1-702d-0477-6afacc060eda");
        assertThat(restored).isEqualTo(event);
    }
}
