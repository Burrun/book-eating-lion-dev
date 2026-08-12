package com.bookeatinglion.order.cart.repository;

import com.bookeatinglion.order.cart.domain.CartItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByMemberId(String memberId);

    Optional<CartItem> findByMemberIdAndBookId(String memberId, Long bookId);

    /** 주문 완료(승인) 후 방금 산 도서를 장바구니에서 지운다. */
    void deleteByMemberIdAndBookIdIn(String memberId, Collection<Long> bookIds);
}
