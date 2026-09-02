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

    private static final String MEMBER_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    private static final String OTHER_MEMBER_ID = "b2c3d4e5-f6a7-8901-bcde-f12345678901";

    @Autowired
    private CartItemRepository cartItemRepository;

    @Test
    void 회원_ID로_장바구니_목록을_조회한다() {
        cartItemRepository.save(new CartItem(MEMBER_ID, 100L, 2));
        cartItemRepository.save(new CartItem(MEMBER_ID, 200L, 1));
        cartItemRepository.save(new CartItem(OTHER_MEMBER_ID, 100L, 3));

        List<CartItem> items = cartItemRepository.findByMemberId(MEMBER_ID);

        assertThat(items).hasSize(2).extracting(CartItem::getBookId).containsExactlyInAnyOrder(100L, 200L);
    }

    @Test
    void 회원ID와_도서ID로_장바구니_항목을_조회한다() {
        cartItemRepository.save(new CartItem(MEMBER_ID, 100L, 2));

        Optional<CartItem> result = cartItemRepository.findByMemberIdAndBookId(MEMBER_ID, 100L);

        assertThat(result).isPresent();
        assertThat(result.get().getQuantity()).isEqualTo(2);
    }

    @Test
    void 존재하지_않는_조합은_빈값을_반환한다() {
        Optional<CartItem> result = cartItemRepository.findByMemberIdAndBookId(MEMBER_ID, 999L);

        assertThat(result).isEmpty();
    }

    @Test
    void 같은_회원이_같은_도서를_두번_담으면_유니크_제약에_걸린다() {
        cartItemRepository.save(new CartItem(MEMBER_ID, 100L, 1));

        assertThatThrownBy(() -> cartItemRepository.saveAndFlush(new CartItem(MEMBER_ID, 100L, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 선택한_항목만_삭제하고_나머지는_남긴다() {
        CartItem item1 = cartItemRepository.save(new CartItem(MEMBER_ID, 100L, 1));
        CartItem item2 = cartItemRepository.save(new CartItem(MEMBER_ID, 200L, 1));
        cartItemRepository.save(new CartItem(MEMBER_ID, 300L, 1));

        cartItemRepository.deleteByMemberIdAndIdIn(MEMBER_ID, List.of(item1.getId(), item2.getId()));

        List<CartItem> remaining = cartItemRepository.findByMemberId(MEMBER_ID);
        assertThat(remaining).hasSize(1).extracting(CartItem::getBookId).containsExactly(300L);
    }

    @Test
    void 선택_삭제는_다른_회원의_항목을_건드리지_않는다() {
        CartItem other = cartItemRepository.save(new CartItem(OTHER_MEMBER_ID, 100L, 1));

        cartItemRepository.deleteByMemberIdAndIdIn(MEMBER_ID, List.of(other.getId()));

        assertThat(cartItemRepository.findById(other.getId())).isPresent();
    }

    @Test
    void 회원의_장바구니를_전체_비운다() {
        cartItemRepository.save(new CartItem(MEMBER_ID, 100L, 1));
        cartItemRepository.save(new CartItem(MEMBER_ID, 200L, 1));
        cartItemRepository.save(new CartItem(OTHER_MEMBER_ID, 100L, 1));

        cartItemRepository.deleteByMemberId(MEMBER_ID);

        assertThat(cartItemRepository.findByMemberId(MEMBER_ID)).isEmpty();
        assertThat(cartItemRepository.findByMemberId(OTHER_MEMBER_ID)).hasSize(1);
    }
}
