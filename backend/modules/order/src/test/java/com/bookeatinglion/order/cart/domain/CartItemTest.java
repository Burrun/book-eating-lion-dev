package com.bookeatinglion.order.cart.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookeatinglion.order.cart.exception.InvalidCartQuantityException;
import org.junit.jupiter.api.Test;

/**
 * DTO 단의 @Min(1) 검증에만 기대지 않는다 — 어떤 경로로 호출되든(향후 배치 작업, 다른
 * 엔트리포인트 등) 도메인 스스로 불변식을 지켜야 CartExceptionHandler 가 400 으로 정확히
 * 응답할 수 있다. IllegalArgumentException 이었다면 CartDomainException 이 아니라서
 * 잡히지 않고 500 으로 샜다.
 */
class CartItemTest {

    @Test
    void 생성시_수량이_1_미만이면_CartDomainException_계열을_던진다() {
        assertThatThrownBy(() -> new CartItem("a1b2c3d4-e5f6-7890-abcd-ef1234567890", 100L, 0))
                .isInstanceOf(InvalidCartQuantityException.class);
    }

    @Test
    void 수량_증가시_delta가_1_미만이면_CartDomainException_계열을_던진다() {
        CartItem cartItem = new CartItem("a1b2c3d4-e5f6-7890-abcd-ef1234567890", 100L, 1);

        assertThatThrownBy(() -> cartItem.increaseQuantity(0)).isInstanceOf(InvalidCartQuantityException.class);
    }

    @Test
    void 수량_변경시_1_미만이면_CartDomainException_계열을_던진다() {
        CartItem cartItem = new CartItem("a1b2c3d4-e5f6-7890-abcd-ef1234567890", 100L, 1);

        assertThatThrownBy(() -> cartItem.changeQuantity(-1)).isInstanceOf(InvalidCartQuantityException.class);
    }
}
