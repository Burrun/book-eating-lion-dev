package com.bookeatinglion.order.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.order.cart.domain.CartItem;
import com.bookeatinglion.order.cart.dto.CartItemView;
import com.bookeatinglion.order.cart.dto.CartResponse;
import com.bookeatinglion.order.cart.exception.CartItemNotFoundException;
import com.bookeatinglion.order.cart.exception.UnauthorizedCartAccessException;
import com.bookeatinglion.order.cart.repository.CartItemRepository;
import com.bookeatinglion.order.client.CatalogClient;
import com.bookeatinglion.order.client.CatalogClient.BookDetailEnvelope;
import com.bookeatinglion.order.client.CatalogClient.BookView;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final String MEMBER_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    private static final String OTHER_MEMBER_ID = "b2c3d4e5-f6a7-8901-bcde-f12345678901";

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private CatalogClient catalogClient;

    @InjectMocks
    private CartService cartService;

    private CartItem cartItem(Long id, String memberId, Long bookId, int quantity) {
        CartItem cartItem = new CartItem(memberId, bookId, quantity);
        ReflectionTestUtils.setField(cartItem, "id", id);
        return cartItem;
    }

    private BookDetailEnvelope bookEnvelope(Long bookId, String title, int price) {
        return new BookDetailEnvelope(true, new BookView(bookId, title, price, "http://img/" + bookId));
    }

    @Test
    void 장바구니_목록을_도서정보와_함께_조회한다() {
        when(cartItemRepository.findByMemberId(MEMBER_ID))
                .thenReturn(List.of(cartItem(1L, MEMBER_ID, 100L, 2), cartItem(2L, MEMBER_ID, 200L, 1)));
        when(catalogClient.getBook(100L)).thenReturn(bookEnvelope(100L, "책1", 10000));
        when(catalogClient.getBook(200L)).thenReturn(bookEnvelope(200L, "책2", 5000));

        CartResponse response = cartService.getCart(MEMBER_ID);

        assertThat(response.items()).hasSize(2);
        assertThat(response.totalQuantity()).isEqualTo(3);
        assertThat(response.totalPrice()).isEqualTo(25000L); // 10000*2 + 5000*1
    }

    @Test
    void 처음_담는_도서는_새_항목을_생성한다() {
        when(cartItemRepository.findByMemberIdAndBookId(MEMBER_ID, 100L)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> {
            CartItem saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
        when(catalogClient.getBook(100L)).thenReturn(bookEnvelope(100L, "책1", 10000));

        CartItemView view = cartService.addItem(MEMBER_ID, 100L, 2);

        assertThat(view.quantity()).isEqualTo(2);
        assertThat(view.subtotal()).isEqualTo(20000L);
    }

    @Test
    void 이미_담긴_도서는_수량을_누적한다() {
        CartItem existing = cartItem(1L, MEMBER_ID, 100L, 2);
        when(cartItemRepository.findByMemberIdAndBookId(MEMBER_ID, 100L)).thenReturn(Optional.of(existing));
        when(catalogClient.getBook(100L)).thenReturn(bookEnvelope(100L, "책1", 10000));

        CartItemView view = cartService.addItem(MEMBER_ID, 100L, 3);

        assertThat(view.quantity()).isEqualTo(5);
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void 본인_항목의_수량을_변경한다() {
        CartItem existing = cartItem(1L, MEMBER_ID, 100L, 2);
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(catalogClient.getBook(100L)).thenReturn(bookEnvelope(100L, "책1", 10000));

        CartItemView view = cartService.changeQuantity(MEMBER_ID, 1L, 5);

        assertThat(view.quantity()).isEqualTo(5);
    }

    @Test
    void 타인의_항목_수량을_변경하면_예외를_던진다() {
        CartItem existing = cartItem(1L, MEMBER_ID, 100L, 2);
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> cartService.changeQuantity(OTHER_MEMBER_ID, 1L, 5))
                .isInstanceOf(UnauthorizedCartAccessException.class);
    }

    @Test
    void 존재하지_않는_항목_수량을_변경하면_예외를_던진다() {
        when(cartItemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.changeQuantity(MEMBER_ID, 999L, 5))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    @Test
    void 본인_항목을_삭제한다() {
        CartItem existing = cartItem(1L, MEMBER_ID, 100L, 2);
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(existing));

        cartService.removeItem(MEMBER_ID, 1L);

        verify(cartItemRepository).delete(existing);
    }

    @Test
    void 타인의_항목을_삭제하면_예외를_던진다() {
        CartItem existing = cartItem(1L, MEMBER_ID, 100L, 2);
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> cartService.removeItem(OTHER_MEMBER_ID, 1L))
                .isInstanceOf(UnauthorizedCartAccessException.class);

        verify(cartItemRepository, never()).delete(any());
    }
}
