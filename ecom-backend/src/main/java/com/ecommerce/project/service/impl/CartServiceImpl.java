package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Bundle;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.CartDTO;
import com.ecommerce.project.payload.CartItemDTO;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.repository.BundleRepository;
import com.ecommerce.project.repository.CartItemRepository;
import com.ecommerce.project.repository.CartRepository;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.service.CartService;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.ProductMapper;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {


    private final CartRepository cartRepository;

    private final AuthUtil authUtil;
    private final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;
    private final BundleRepository bundleRepository;
    private final ModelMapper modelMapper;
    private final ProductMapper productMapper;



    @Override
    @Transactional
    public CartDTO addProductToCart(Long productId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new APIException("Quantity must be greater than zero");
        }
        //  Find existing cart or create one
        Cart cart = createCart();
        // Retrieve Product Details
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
        // Perform Validations

        CartItem cartItem = cartItemRepository.findCartItemByProductProductIdAndCartId(cart.getCartId(), productId);

        if (cartItem != null) {
            throw new APIException("Product" + product.getProductName() + " already exists in the cart ");
        }

        if (product.getQuantity() == 0) {
            throw new APIException(product.getProductName() + " is not available");
        }

        if (product.getQuantity() < quantity) {
            throw new APIException("Please, make an order of the " + product.getProductName() + " less than or equal to the quantity " + product.getQuantity() + ".");
        }

        // Create Cart Item
        CartItem newCartItem = new CartItem();
        newCartItem.setProduct(product);
        newCartItem.setCart(cart);
        newCartItem.setQuantity(quantity);
        newCartItem.setDiscount(product.getDiscount());
        newCartItem.setProductPrice(product.getSpecialPrice());
        // Save Cart Item
        cartItemRepository.save(newCartItem);
        List<CartItem> currentCartItems = cartItemRepository.findByCartCartId(cart.getCartId());

        double newTotalPrice = currentCartItems.stream()
                .mapToDouble(item -> item.getProductPrice() * item.getQuantity())
                .sum();
        cart.setTotalPrice(newTotalPrice);
        cartRepository.save(cart);

        // Return updated cart

        return mapToCartDTO(cart);
    }

    @Override
    public List<CartDTO> getAllCarts() {
        return cartRepository.findAll().stream()
                .map(this::mapToCartDTO)
                .collect(Collectors.toList());
    }

    @Override
    public CartDTO getOrCreateCartForCurrentUser() {
        Cart cart = createCart();
        return mapToCartDTO(cart);
    }

    @Override
    public CartDTO getCart(String emailId, Long cartId) {
        Cart cart = cartRepository.findCartByEmailAndCartId(emailId, cartId);
        if (cart == null) {
            throw new ResourceNotFoundException("Cart", "cartId", cartId);
        }
        return mapToCartDTO(cart);
    }

    @Override
    @Transactional
    public CartDTO updateProductQuantityInCart(Long productId, Integer quantity) {

        String emailId = authUtil.loggedInEmail();
        Cart cart = cartRepository.findCartByEmail(emailId);
        if (cart == null) {
            throw new ResourceNotFoundException("Cart", "email", emailId);
        }
        Long cartId = cart.getCartId();

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        if (product.getQuantity() == 0) {
            throw new APIException(product.getProductName() + " is not available");
        }

        CartItem cartItem = cartItemRepository.findCartItemByProductProductIdAndCartId(cartId, productId);
        if (cartItem == null) {
            throw new APIException("Product " + product.getProductName() + " not available in the cart!!!");
        }

        int newQuantity = cartItem.getQuantity() + quantity;

        if (newQuantity < 0) {
            throw new APIException("The resulting quantity cannot be negative");
        }

        // Validate the RESULTING quantity against stock, not just the delta.
        // Checking only the delta let a client keep incrementing past available stock.
        if (newQuantity > product.getQuantity()) {
            throw new APIException("Please, make an order of the " + product.getProductName()
                    + " less than or equal to the quantity " + product.getQuantity() + ".");
        }

        if (newQuantity == 0) {
            deleteProductFromCart(cartId, productId);
        } else {
            cartItem.setProductPrice(product.getSpecialPrice());
            cartItem.setQuantity(cartItem.getQuantity() + quantity);
            cartItem.setDiscount(product.getDiscount());
            cart.setTotalPrice(cart.getTotalPrice() + (cartItem.getProductPrice() * quantity));
            cartRepository.save(cart);
        }

        CartItem updatedItem = cartItemRepository.save(cartItem);

        if (updatedItem.getQuantity() == 0) {
            cartItemRepository.deleteById(updatedItem.getCartItemId());
        }

        return mapToCartDTO(cart);
    }

    @Override
    @Transactional
    public String deleteProductFromCart(Long cartId, Long productId) {

        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "cartId", cartId));
        assertCartOwnedByCurrentUser(cart);

        CartItem cartItem = cartItemRepository.findCartItemByProductProductIdAndCartId(cartId, productId);
        if (cartItem == null) {
            throw new ResourceNotFoundException("Product", "productId", productId);
        }
        cart.setTotalPrice(cart.getTotalPrice() - (cartItem.getProductPrice() * cartItem.getQuantity()));
        cartItemRepository.deleteCartItemByProductIdAndCartId(cartId, productId);


        return "Product " + cartItem.getProduct().getProductName() + " removed from cart !!!";
    }

    @Override
    public void updateProductsInCarts(Long cartId, Long productId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "cartId", cartId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        CartItem cartItem = cartItemRepository.findCartItemByProductProductIdAndCartId(cartId, productId);

        if (cartItem == null) {
            throw new APIException("Product " + product.getProductName() + " not available in the cart!!!");
        }

        double cartPrice = cart.getTotalPrice() -
                (cartItem.getProductPrice() * cartItem.getQuantity());
        cartItem.setProductPrice(product.getSpecialPrice());
        cart.setTotalPrice(cartPrice + (cartItem.getProductPrice() * cartItem.getQuantity()));
        cartItemRepository.save(cartItem);
    }

    @Transactional
    @Override
    public String createOrUpdateCartWithItems(List<CartItemDTO> cartItems) {
        // Get user's email

        String emailId = authUtil.loggedInEmail();

        // Check if an existing cart is available or create a new one
        Cart existingCart = cartRepository.findCartByEmail(emailId);
        if(existingCart == null) {
            existingCart = new Cart();
            existingCart.setTotalPrice(0.00);
            existingCart.setUser(authUtil.loggedInUser());
            existingCart = cartRepository.save(existingCart);

        } else {
            // Clear all current items in the existing cart
            cartItemRepository.deleteAllByCartId(existingCart.getCartId());

        }

        double totalPrice = 0.00;
        // Process each item in the request to add to the cart

        for (CartItemDTO cartItemDTO : cartItems) {
            Long productId =cartItemDTO.getProductId();
            Integer quantity = cartItemDTO.getQuantity();

            if (quantity == null || quantity <= 0) {
                throw new APIException("Quantity must be greater than zero for product " + productId);
            }

            // Find the product by ID
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

            if (product.getQuantity() == null || product.getQuantity() <= 0) {
                throw new APIException(product.getProductName() + " is not available");
            }

            // The client controls this payload, so stock must be validated server-side.
            if (quantity > product.getQuantity()) {
                throw new APIException("Insufficient stock for " + product.getProductName()
                        + ". Available: " + product.getQuantity()
                        + ", requested: " + quantity);
            }

            totalPrice += product.getSpecialPrice() * quantity;
            // Create and save cart item
            CartItem cartItem = new CartItem();
            cartItem.setProduct(product);
            cartItem.setCart(existingCart);
            cartItem.setQuantity(quantity);
            cartItem.setProductPrice(product.getSpecialPrice());
            cartItem.setDiscount(product.getDiscount());
            cartItemRepository.save(cartItem);
        }
        // Update the cart's total prince and save

        existingCart.setTotalPrice(totalPrice);
        cartRepository.save(existingCart);
        return "Cart created/updated with the new items successfully!!!";
    }

    @Override
    @Transactional
    public CartDTO saveItemForLater(Long cartItemId) {
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "cartItemId", cartItemId));
        assertCartOwnedByCurrentUser(cartItem.getCart());
        cartItem.setSavedForLater(true);
        cartItemRepository.save(cartItem);
        Cart cart = cartItem.getCart();
        recalculateCartTotal(cart);
        return mapToCartDTO(cart);
    }

    @Override
    @Transactional
    public CartDTO moveItemToCart(Long cartItemId) {
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "cartItemId", cartItemId));
        assertCartOwnedByCurrentUser(cartItem.getCart());
        cartItem.setSavedForLater(false);
        cartItemRepository.save(cartItem);
        Cart cart = cartItem.getCart();
        recalculateCartTotal(cart);
        return mapToCartDTO(cart);
    }

    private void recalculateCartTotal(Cart cart) {
        double newTotal = cart.getCartItems().stream()
                .filter(item -> Boolean.FALSE.equals(item.getSavedForLater()))
                .mapToDouble(item -> item.getProductPrice() * item.getQuantity())
                .sum();
        cart.setTotalPrice(newTotal);
        cartRepository.save(cart);
    }

    private void assertCartOwnedByCurrentUser(Cart cart) {
        String email = authUtil.loggedInEmail();
        if (cart.getUser() == null || !email.equalsIgnoreCase(cart.getUser().getEmail())) {
            throw new APIException("You are not allowed to modify this cart");
        }
    }

    @Override
    @Transactional
    public CartDTO addBundleToCart(Long bundleId) {
        Cart cart = createCart();
        Bundle bundle = bundleRepository.findById(bundleId)
                .orElseThrow(() -> new ResourceNotFoundException("Bundle", "bundleId", bundleId));

        if (!Boolean.TRUE.equals(bundle.getActive())) {
            throw new APIException("Bundle is no longer available");
        }

        if (bundle.getProducts() == null || bundle.getProducts().isEmpty()) {
            throw new APIException("Bundle has no products");
        }

        double discountRate = (bundle.getDiscountPercentage() != null ? bundle.getDiscountPercentage() : 0.0) / 100.0;

        for (Product product : bundle.getProducts()) {
            if (product.getQuantity() == null || product.getQuantity() <= 0) {
                throw new APIException("Product " + product.getProductName() + " is not available");
            }

            CartItem existingItem = cartItemRepository.findCartItemByProductProductIdAndCartId(cart.getCartId(), product.getProductId());
            if (existingItem != null) {
                existingItem.setQuantity(existingItem.getQuantity() + 1);
                double bundlePrice = product.getSpecialPrice() * (1 - discountRate);
                existingItem.setProductPrice(bundlePrice);
                existingItem.setDiscount(product.getDiscount() + bundle.getDiscountPercentage());
                cartItemRepository.save(existingItem);
            } else {
                CartItem newCartItem = new CartItem();
                newCartItem.setProduct(product);
                newCartItem.setCart(cart);
                newCartItem.setQuantity(1);
                double bundlePrice = product.getSpecialPrice() * (1 - discountRate);
                newCartItem.setProductPrice(bundlePrice);
                newCartItem.setDiscount(product.getDiscount() + bundle.getDiscountPercentage());
                cartItemRepository.save(newCartItem);
            }
        }

        recalculateCartTotal(cart);
        return mapToCartDTO(cart);
    }

    private Cart createCart() {
        //  Find existing cart or create one
        Cart userCart = cartRepository.findCartByEmail(authUtil.loggedInEmail());
        if (userCart != null) {
            return userCart;
        }
        Cart cart = new Cart();
        cart.setTotalPrice(0.00);
        cart.setUser(authUtil.loggedInUser());
        Cart newCart = cartRepository.save(cart);
        return newCart;
    }

    private CartDTO mapToCartDTO(Cart cart){
        CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);
        cartDTO.setProducts(productMapper.mapCartItemsToProductDTOs(cart.getCartItems()));
        return cartDTO;
    }
}
