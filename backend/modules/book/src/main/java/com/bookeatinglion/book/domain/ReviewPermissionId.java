package com.bookeatinglion.book.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewPermissionId implements Serializable {

    @Column(name = "member_id")
    private Long memberId;

    /** order_db.order_items 의 값. FK 가 아니라 출처 추적용이다. */
    @Column(name = "order_item_id")
    private Long orderItemId;

    public ReviewPermissionId(Long memberId, Long orderItemId) {
        this.memberId = memberId;
        this.orderItemId = orderItemId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReviewPermissionId that)) {
            return false;
        }
        return Objects.equals(memberId, that.memberId) && Objects.equals(orderItemId, that.orderItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId, orderItemId);
    }
}
