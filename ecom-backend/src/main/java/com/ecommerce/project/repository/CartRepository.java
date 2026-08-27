package com.ecommerce.project.repository;

import com.ecommerce.project.model.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CartRepository extends JpaRepository<Cart,Long> {


    //Filter this cart where this associated user's email matches the given parameter okay
    @Query("SELECT DISTINCT c FROM Cart c " +
           "JOIN FETCH c.user u " +
           "LEFT JOIN FETCH c.cartItems ci " +
           "LEFT JOIN FETCH ci.product p " +
           "WHERE u.email = ?1")
    Cart findCartByEmail(String email);


    @Query("SELECT c FROM Cart c WHERE c.user.email = ?1 AND c.cartId = ?2")
    Cart findCartByEmailAndCartId(String emailId, Long cartId);

    @Query("SELECT c FROM Cart c JOIN  FETCH c.cartItems ci JOIN  FETCH  ci.product p WHERE  p.productId = ?1")
    List<Cart> findCartsByProductId(Long productId);


}
