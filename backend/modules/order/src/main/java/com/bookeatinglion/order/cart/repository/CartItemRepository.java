package com.bookeatinglion.order.cart.repository;

import com.bookeatinglion.order.cart.domain.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByMemberId(Long memberId);

    Optional<CartItem> findByMemberIdAndBookId(Long memberId, Long bookId);
}
