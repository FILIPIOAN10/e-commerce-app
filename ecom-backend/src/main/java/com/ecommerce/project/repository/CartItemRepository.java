package com.ecommerce.project.repository;

import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


@Repository
public interface CartItemRepository extends JpaRepository<CartItem,Long> {

    @Query("SELECT ci FROM CartItem ci WHERE ci.cart.cartId = ?1 AND ci.product.productId = ?2")
    CartItem findCartItemByProductProductIdAndCartId(Long cartId, Long productId);

    List<CartItem> findByCartCartId(Long cartId);

    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.cart.cartId = ?1 AND ci.product.productId = ?2")
    void deleteCartItemByProductIdAndCartId(Long cartId, Long productId);

    @Modifying
    @Query("DELETE FROM CartItem  ci WHERE ci.cart.cartId=?1")
    void deleteAllByCartId(Long cartId);
    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.cart.cartId = :cartId AND (ci.savedForLater = false OR ci.savedForLater IS NULL)")
    void deleteByCartIdAndSavedForLaterFalseOrNull(@Param("cartId") Long cartId);

}
