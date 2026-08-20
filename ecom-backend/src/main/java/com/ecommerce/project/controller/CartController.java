package com.ecommerce.project.controller;


import com.ecommerce.project.model.Cart;
import com.ecommerce.project.payload.CartDTO;
import com.ecommerce.project.payload.CartItemDTO;
import com.ecommerce.project.repository.CartRepository;
import com.ecommerce.project.service.CartService;
import com.ecommerce.project.util.AuthUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CartController {

    private CartService cartService;

    private AuthUtil authUtil;

    private CartRepository cartRepository;

    public CartController(CartService cartService, AuthUtil authUtil, CartRepository cartRepository) {
        this.cartService = cartService;
        this.authUtil = authUtil;
        this.cartRepository = cartRepository;
    }



    @Tag(name = "Cart")
    @PostMapping("/cart/create")
    public ResponseEntity<String> createOrUpdateCart(@RequestBody List<CartItemDTO> cartItems) {
        String response =cartService.createOrUpdateCartWithItems(cartItems);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    @Tag(name = "Cart")
    @PostMapping("/carts/bundles/{bundleId}")
    public ResponseEntity<CartDTO> addBundleToCart(@PathVariable Long bundleId) {
        CartDTO cartDTO = cartService.addBundleToCart(bundleId);
        return new ResponseEntity<>(cartDTO, HttpStatus.CREATED);
    }

    @Tag(name = "Cart")
    @PostMapping("/carts/products/{productId}/quantity/{quantity}")
    public ResponseEntity<CartDTO> addProductToCart(@PathVariable Long productId,
                                                    @PathVariable Integer quantity) {


        CartDTO cartDTO = cartService.addProductToCart(productId, quantity);
        return new ResponseEntity<CartDTO>(cartDTO, HttpStatus.CREATED);
    }


    @Tag(name = "Cart")
    @GetMapping("/carts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CartDTO>> getCarts() {
        List<CartDTO> cartDTOS = cartService.getAllCarts();
        return new ResponseEntity<List<CartDTO>>(cartDTOS, HttpStatus.FOUND);
    }

    @Tag(name = "Cart")
    @GetMapping("/carts/users/cart")

    public ResponseEntity<CartDTO> getCartById() {

        // get the email from user section
        String emailId = authUtil.loggedInEmail();
        // getting the cart of the user from db
        Cart cart = cartRepository.findCartByEmail(emailId);
        // getting the cartId from the cart

        Long cartId = cart.getCartId();
        CartDTO cartDTO = cartService.getCart(emailId, cartId);
        return new ResponseEntity<CartDTO>(cartDTO, HttpStatus.OK);
    }



    @Tag(name = "Cart")
    @PutMapping("/cart/products/{productId}/quantity/{operation}")
    public ResponseEntity<CartDTO> updateCartProduct(@PathVariable Long productId,
                                                     @PathVariable String operation) {
        int delta = switch (operation.toLowerCase()) {
            case "plus" -> 1;
            case "minus" -> -1;
            case "delete" -> -1;
            default -> 0;
        };
        CartDTO cartDTO = cartService.updateProductQuantityInCart(productId, delta);
        return new ResponseEntity<CartDTO>(cartDTO, HttpStatus.OK);
    }

    @Tag(name = "Cart")
    @DeleteMapping("/carts/{cartId}/product/{productId}")
    public ResponseEntity<String> deleteProductFromCart(@PathVariable Long cartId,
                                                        @PathVariable Long productId) {

        String status = cartService.deleteProductFromCart(cartId, productId);

        return new ResponseEntity<String>(status, HttpStatus.OK);

    }

    @Tag(name = "Cart")
    @PutMapping("/cart/items/{cartItemId}/save-for-later")
    public ResponseEntity<CartDTO> saveItemForLater(@PathVariable Long cartItemId) {
        CartDTO cartDTO = cartService.saveItemForLater(cartItemId);
        return new ResponseEntity<CartDTO>(cartDTO, HttpStatus.OK);
    }

    @Tag(name = "Cart")
    @PutMapping("/cart/items/{cartItemId}/move-to-cart")
    public ResponseEntity<CartDTO> moveItemToCart(@PathVariable Long cartItemId) {
        CartDTO cartDTO = cartService.moveItemToCart(cartItemId);
        return new ResponseEntity<CartDTO>(cartDTO, HttpStatus.OK);
    }

}
