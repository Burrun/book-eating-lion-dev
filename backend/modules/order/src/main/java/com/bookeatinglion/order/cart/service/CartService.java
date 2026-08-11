package com.bookeatinglion.order.cart.service;

import com.bookeatinglion.order.cart.domain.CartItem;
import com.bookeatinglion.order.cart.dto.CartItemView;
import com.bookeatinglion.order.cart.dto.CartResponse;
import com.bookeatinglion.order.cart.exception.CartItemNotFoundException;
import com.bookeatinglion.order.cart.exception.UnauthorizedCartAccessException;
import com.bookeatinglion.order.cart.repository.CartItemRepository;
import com.bookeatinglion.order.client.CatalogClient;
import com.bookeatinglion.order.client.CatalogClient.BookView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이 서비스가 order-service 의 첫 outbound 동기 호출(CatalogClient)을 갖는다(§7.6 예외).
 *
 * §7.6 은 "order 가 죽으면 어차피 구매도 불가능하니 order → 밖으로 나가는 호출은 만들지 않는다"는
 * 원칙이었다. Cart 는 반대 방향이다 — order_db 가 소유한 (memberId, bookId, quantity) 만으로는
 * 화면을 그릴 수 없고, 도서 제목/가격/이미지는 catalog_db 에만 있다. 이 조합을 프론트나 별도
 * BFF 로 미루는 대신 이 서비스가 맡되, catalog-service 장애가 장바구니 자체를 막지 않도록
 * CatalogClientFallback 으로 항목별 degrade 를 강제한다 — Delivery 가 "호출을 없앤" 것과 달리
 * Cart 는 "호출을 죽지 않게" 만드는 쪽을 택했다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final CatalogClient catalogClient;

    public CartResponse getCart(Long memberId) {
        var items = cartItemRepository.findByMemberId(memberId).stream()
                .map(this::toView)
                .toList();
        return CartResponse.of(items);
    }

    @Transactional
    public CartItemView addItem(Long memberId, Long bookId, int quantity) {
        CartItem cartItem = cartItemRepository
                .findByMemberIdAndBookId(memberId, bookId)
                .map(existing -> {
                    existing.increaseQuantity(quantity);
                    return existing;
                })
                .orElseGet(() -> cartItemRepository.save(new CartItem(memberId, bookId, quantity)));

        return toView(cartItem);
    }

    @Transactional
    public CartItemView changeQuantity(Long memberId, Long cartItemId, int quantity) {
        CartItem cartItem = getOwnedCartItem(memberId, cartItemId);
        cartItem.changeQuantity(quantity);
        return toView(cartItem);
    }

    @Transactional
    public void removeItem(Long memberId, Long cartItemId) {
        CartItem cartItem = getOwnedCartItem(memberId, cartItemId);
        cartItemRepository.delete(cartItem);
    }

    private CartItem getOwnedCartItem(Long memberId, Long cartItemId) {
        CartItem cartItem =
                cartItemRepository.findById(cartItemId).orElseThrow(() -> new CartItemNotFoundException(cartItemId));

        if (!cartItem.isOwnedBy(memberId)) {
            throw new UnauthorizedCartAccessException(cartItemId);
        }
        return cartItem;
    }

    private CartItemView toView(CartItem cartItem) {
        BookView book = catalogClient.getBook(cartItem.getBookId()).data();
        return CartItemView.of(cartItem, book);
    }
}
