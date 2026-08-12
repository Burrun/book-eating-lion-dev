package com.bookeatinglion.order.cart.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import com.bookeatinglion.order.cart.dto.AddCartItemRequest;
import com.bookeatinglion.order.cart.dto.CartItemView;
import com.bookeatinglion.order.cart.dto.CartResponse;
import com.bookeatinglion.order.cart.dto.ChangeCartItemQuantityRequest;
import com.bookeatinglion.order.cart.dto.RemoveSelectedCartItemsRequest;
import com.bookeatinglion.order.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ApiResponse<CartResponse> getCart() {
        String memberId = SecurityUtils.currentMemberSub();
        return ApiResponse.success(cartService.getCart(memberId));
    }

    @PostMapping
    public ApiResponse<CartItemView> addItem(@Valid @RequestBody AddCartItemRequest request) {
        String memberId = SecurityUtils.currentMemberSub();
        int quantity = request.quantity() != null ? request.quantity() : 1;
        return ApiResponse.success(cartService.addItem(memberId, request.bookId(), quantity));
    }

    @PatchMapping("/{cartItemId}")
    public ApiResponse<CartItemView> changeQuantity(
            @PathVariable Long cartItemId, @Valid @RequestBody ChangeCartItemQuantityRequest request) {
        String memberId = SecurityUtils.currentMemberSub();
        return ApiResponse.success(cartService.changeQuantity(memberId, cartItemId, request.quantity()));
    }

    @DeleteMapping("/{cartItemId}")
    public ResponseEntity<Void> removeItem(@PathVariable Long cartItemId) {
        String memberId = SecurityUtils.currentMemberSub();
        cartService.removeItem(memberId, cartItemId);
        return ResponseEntity.noContent().build();
    }

    /** 정적 경로("selected")가 {cartItemId} 패턴보다 우선 매칭되므로 순서와 무관하게 안전하다. */
    @DeleteMapping("/selected")
    public ResponseEntity<Void> removeSelectedItems(@Valid @RequestBody RemoveSelectedCartItemsRequest request) {
        String memberId = SecurityUtils.currentMemberSub();
        cartService.removeSelectedItems(memberId, request.cartItemIds());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Void> clearCart() {
        String memberId = SecurityUtils.currentMemberSub();
        cartService.clearCart(memberId);
        return ResponseEntity.noContent().build();
    }
}
