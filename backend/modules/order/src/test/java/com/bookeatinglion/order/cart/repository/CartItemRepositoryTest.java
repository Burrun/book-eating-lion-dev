package com.bookeatinglion.order.cart.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookeatinglion.order.OrderModuleTestApplication;
import com.bookeatinglion.order.cart.domain.CartItem;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = OrderModuleTestApplication.class)
class CartItemRepositoryTest {

    @Autowired
    private CartItemRepository cartItemRepository;

    @Test
    void 회원_ID로_장바구니_목록을_조회한다() {
        cartItemRepository.save(new CartItem(1L, 100L, 2));
        cartItemRepository.save(new CartItem(1L, 200L, 1));
        cartItemRepository.save(new CartItem(2L, 100L, 3));

        List<CartItem> items = cartItemRepository.findByMemberId(1L);

        assertThat(items).hasSize(2).extracting(CartItem::getBookId).containsExactlyInAnyOrder(100L, 200L);
    }

    @Test
    void 회원ID와_도서ID로_장바구니_항목을_조회한다() {
        cartItemRepository.save(new CartItem(1L, 100L, 2));

        Optional<CartItem> result = cartItemRepository.findByMemberIdAndBookId(1L, 100L);

        assertThat(result).isPresent();
        assertThat(result.get().getQuantity()).isEqualTo(2);
    }

    @Test
    void 존재하지_않는_조합은_빈값을_반환한다() {
        Optional<CartItem> result = cartItemRepository.findByMemberIdAndBookId(1L, 999L);

        assertThat(result).isEmpty();
    }

    @Test
    void 같은_회원이_같은_도서를_두번_담으면_유니크_제약에_걸린다() {
        cartItemRepository.save(new CartItem(1L, 100L, 1));

        assertThatThrownBy(() -> cartItemRepository.saveAndFlush(new CartItem(1L, 100L, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
