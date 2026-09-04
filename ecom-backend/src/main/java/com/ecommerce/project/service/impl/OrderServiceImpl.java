package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.*;
import com.ecommerce.project.payload.CartItemDTO;
import com.ecommerce.project.payload.GuestCheckoutRequestDTO;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderResponse;
import com.ecommerce.project.payload.OrderSummaryDTO;
import com.ecommerce.project.repository.*;
import com.ecommerce.project.service.InventoryReservationService;
import com.ecommerce.project.service.OrderService;
import com.ecommerce.project.service.order.OrderDtoAssembler;
import com.ecommerce.project.service.order.OrderPaymentHandler;
import com.ecommerce.project.service.order.OrderStatus;
import com.ecommerce.project.service.order.event.OrderPlacedEvent;
import com.ecommerce.project.service.order.event.OrderStatusUpdatedEvent;
import com.ecommerce.project.service.stock.StockLedgerService;
import com.ecommerce.project.service.pricing.PriceBreakdown;
import com.ecommerce.project.service.pricing.PricingContext;
import com.ecommerce.project.service.pricing.PricingPipeline;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.PaginationUtil;
import com.ecommerce.project.util.SortWhitelist;
import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.repository.CouponRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.ecommerce.project.service.pricing.Money;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final InventoryReservationService inventoryReservationService;
    private final StockLedgerService stockLedgerService;
    private final CouponRepository couponRepository;

    private final AuthUtil authUtil;
    private final PricingPipeline pricingPipeline;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderDtoAssembler orderDtoAssembler;
    private final OrderPaymentHandler orderPaymentHandler;


    @Override
    @Transactional // everything in this method successfully finishes or nothing finish
    public OrderDTO placeOrder(String emailId, Long addressId, String paymentMethod, String pgName, String pgPaymentId, String pgStatus, String pgResponseMessage, List<String> couponCodes) {

        Cart cart = cartRepository.findCartByEmail(emailId);
        if (cart == null) {
            throw new ResourceNotFoundException("Cart", "email", emailId);
        }

        // Saved-for-later items are excluded from cart.totalPrice, so they must be
        // excluded from order creation to prevent shipping uncharged items.
        List<CartItem> cartItems = getActiveCartItems(cart);
        if (cartItems.isEmpty()) {
            throw new APIException("Cart has no active items to purchase");
        }
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));

        if (address.getUser() == null || !emailId.equalsIgnoreCase(address.getUser().getEmail())) {
            throw new APIException("Address does not belong to the current user");
        }

        Order order = new Order();
        order.setEmail(emailId);
        order.setOrderDate(LocalDate.now());
        order.setOrderStatus("Placed");
        order.setAddress(address);

        PriceBreakdown pricing = pricingPipeline.price(
                PricingContext.of(cart.getTotalPrice(), address, couponCodes));

        orderPaymentHandler.verify(paymentMethod, pgName, pgPaymentId, pricing.total());

        // Coupon usage is only consumed once the order is actually being placed.
        consumeCoupons(pricing.appliedCouponIds(), pricing.appliedCouponCodes());

        order.setTotalAmount(pricing.total().toBigDecimal());
        order.setDiscountAmount(pricing.discountTotal().toBigDecimal());
        order.setShippingCost(pricing.shippingTotal().toBigDecimal());
        order.setAppliedCoupons(String.join(",", pricing.appliedCouponCodes()));

        order.setPayment(orderPaymentHandler.record(order, paymentMethod, pgPaymentId, pgStatus, pgResponseMessage, pgName));
        Order savedOrder = orderRepository.save(order);

        List<OrderItem> orderItems = new ArrayList<>();
        // for every cart item you creat an order item
        for (CartItem cartItem : cartItems) {


            //Creating an object of order item
            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setDiscount(cartItem.getDiscount());
            orderItem.setOrderedProductPrice(cartItem.getProductPrice());
            orderItem.setOrder(savedOrder);
            orderItems.add(orderItem);
        }

        orderItems = orderItemRepository.saveAll(orderItems);

        // Consume the Redis reservation and deduct the reserved stock in DB
        inventoryReservationService.consumeReservationsForCart(cart.getCartId());

        // Clear the cart after the reservation has been consumed
        // Single bulk delete instead of one transaction per product (was O(n) queries).
        cartItemRepository.deleteByCartIdAndSavedForLaterFalseOrNull(cart.getCartId());
        cart.setTotalPrice(BigDecimal.ZERO);
        cartRepository.save(cart);

        // Send back the order summary
        OrderDTO orderDTO = orderDtoAssembler.forPlacedOrder(savedOrder, orderItems, addressId, pricing.total().toBigDecimal());
        eventPublisher.publishEvent(new OrderPlacedEvent(emailId, savedOrder.getId(), pricing.total().toBigDecimal(), orderDTO));
        return orderDTO;
    }

    @Override
    public OrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {

        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder,
                AppConstants.SORT_ORDERS_BY, SortWhitelist.ORDER);

        return orderDtoAssembler.buildOrderResponse(orderRepository.findAllIds(pageDetails));
    }

    @Override
    @Transactional
    public OrderDTO updateOrder(Long orderId, String status) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));

        String previousStatus = order.getOrderStatus();
        OrderStatus.assertTransitionAllowed(previousStatus, status);

        if (OrderStatus.releasesStock(previousStatus, status)) {
            restockOrderItems(order, status);
        }

        order.setOrderStatus(status);
        orderRepository.save(order);
        OrderDTO orderDTO = orderDtoAssembler.forStatusUpdate(order);
        eventPublisher.publishEvent(new OrderStatusUpdatedEvent(orderId, order.getEmail(), status, orderDTO));
        return orderDTO;
    }

    /**
     * Returns the stock held by an order back to inventory. Called when an order
     * moves to a stock-releasing status (Cancelled / Returned) so that cancelled
     * orders no longer silently destroy inventory.
     *
     * <p>The two routes back are recorded distinctly: goods that were never
     * dispatched are not the same event as goods a customer sent back, and a
     * ledger that called both "stock went up" would lose the difference.
     */
    private void restockOrderItems(Order order, String targetStatus) {
        if (order.getOrderItems() == null) {
            return;
        }
        StockMovementReason reason = OrderStatus.RETURNED.equals(targetStatus)
                ? StockMovementReason.RETURN
                : StockMovementReason.CANCELLATION;

        for (OrderItem item : order.getOrderItems()) {
            if (item.getProduct() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                continue;
            }
            stockLedgerService.applyAndRecord(
                    item.getProduct().getProductId(), item.getQuantity(), reason,
                    "ORDER", order.getId(), "Order moved to " + targetStatus);
        }
    }

    @Override
    public OrderResponse getAllSellerOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder,
                AppConstants.SORT_ORDERS_BY, SortWhitelist.ORDER);

        User seller = authUtil.loggedInUser();

        return orderDtoAssembler.buildOrderResponse(orderRepository.findIdsBySellerId(seller.getUserId(), pageDetails));
    }

    @Override
    public OrderResponse getLoggedInUserOrders(String email, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder,
                AppConstants.SORT_ORDERS_BY, SortWhitelist.ORDER);

        return orderDtoAssembler.buildOrderResponse(orderRepository.findIdsByEmail(email, pageDetails));
    }

    @Override
    public OrderDTO getOrderById(Long orderId, String email) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));

        // Case-insensitive to match the comparison used when the order is placed.
        if (order.getEmail() == null || !order.getEmail().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("You can only access your own orders");
        }

        return orderDtoAssembler.forDetailView(order);
    }


    @Override
    public BigDecimal calculateShippingCost(Long addressId, BigDecimal cartTotal) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));
        return pricingPipeline.price(PricingContext.of(cartTotal, address, List.of()))
                .shippingTotal()
                .toBigDecimal();
    }

    @Override
    public OrderSummaryDTO previewOrder(String emailId, Long addressId, List<String> couponCodes) {
        Cart cart = cartRepository.findCartByEmail(emailId);
        // A cart holding nothing but saved-for-later lines has nothing to preview:
        // checkout would reject it, so preview must agree rather than quote zero.
        if (cart == null || getActiveCartItems(cart).isEmpty()) {
            throw new APIException("Cart has no active items to purchase");
        }

        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));
        if (address.getUser() == null || !emailId.equalsIgnoreCase(address.getUser().getEmail())) {
            throw new APIException("Address does not belong to the current user");
        }

        // Reserve stock for 10 minutes (TTL) to prevent race conditions at checkout
        inventoryReservationService.reserveCartItems(cart.getCartId(), getActiveCartItems(cart));

        // The pipeline is pure, so preview does not touch coupon usage counters.
        PriceBreakdown pricing = pricingPipeline.price(
                PricingContext.of(cart.getTotalPrice(), address, couponCodes));

        OrderSummaryDTO summary = new OrderSummaryDTO();
        summary.setEmail(emailId);
        summary.setAddressId(addressId);
        summary.setSubtotal(pricing.subtotal().toBigDecimal());
        summary.setDiscountAmount(pricing.discountTotal().toBigDecimal());
        summary.setShippingCost(pricing.shippingTotal().toBigDecimal());
        summary.setTotalAmount(pricing.total().toBigDecimal());
        summary.setAppliedCoupons(pricing.appliedCouponCodes());
        return summary;
    }

    @Override
    @Transactional
    public OrderDTO placeGuestOrder(GuestCheckoutRequestDTO request) {
        Address address = orderDtoAssembler.toAddress(request.getAddress());
        address = addressRepository.save(address);

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new APIException("Cart is Empty");
        }

        Money subtotal = Money.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        Order order = new Order();
        order.setEmail(request.getEmail());
        order.setOrderDate(LocalDate.now());
        order.setOrderStatus("Placed");
        order.setAddress(address);

        for (CartItemDTO dto : request.getItems()) {
            if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
                throw new APIException("Quantity must be greater than zero for product " + dto.getProductId());
            }

            Product product = productRepository.findById(dto.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", dto.getProductId()));

            if (product.getQuantity() < dto.getQuantity()) {
                throw new APIException("Insufficient stock for product: "
                        + product.getProductName()
                        + ". Available: " + product.getQuantity()
                        + ", requested: " + dto.getQuantity());
            }

            BigDecimal price = product.getSpecialPrice();
            subtotal = subtotal.add(Money.of(price).times(dto.getQuantity()));

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setQuantity(dto.getQuantity());
            orderItem.setDiscount(product.getDiscount());
            orderItem.setOrderedProductPrice(price);
            orderItem.setOrder(order);
            orderItems.add(orderItem);
        }

        PriceBreakdown pricing = pricingPipeline.price(
                PricingContext.of(subtotal, address, request.getCouponCodes()));

        orderPaymentHandler.verify(request.getPaymentMethod(), request.getPgName(),
                request.getPgPaymentId(), pricing.total());

        consumeCoupons(pricing.appliedCouponIds(), pricing.appliedCouponCodes());

        order.setTotalAmount(pricing.total().toBigDecimal());
        order.setDiscountAmount(pricing.discountTotal().toBigDecimal());
        order.setShippingCost(pricing.shippingTotal().toBigDecimal());
        order.setAppliedCoupons(String.join(",", pricing.appliedCouponCodes()));

        order.setPayment(orderPaymentHandler.record(order, request.getPaymentMethod(),
                request.getPgPaymentId(), request.getPgStatus(), request.getPgResponseMessage(), request.getPgName()));
        Order savedOrder = orderRepository.save(order);

        orderItems.forEach(item -> item.setOrder(savedOrder));
        List<OrderItem> savedOrderItems = orderItemRepository.saveAll(orderItems);

        consumeGuestStock(savedOrderItems, savedOrder.getId());

        OrderDTO guestOrderDTO = orderDtoAssembler.forPlacedOrder(savedOrder, savedOrderItems, address.getAddressId(), pricing.total().toBigDecimal());
        eventPublisher.publishEvent(new OrderPlacedEvent(request.getEmail(), savedOrder.getId(), pricing.total().toBigDecimal(), guestOrderDTO));

        return guestOrderDTO;
    }

    /**
     * Takes the stock a guest order needs, once the order exists to attribute it
     * to.
     *
     * <p>Deliberately after the order is saved rather than inside the item loop:
     * a ledger entry has to say <em>which</em> order consumed the stock, and
     * before the insert there is no id to name. Nothing is lost by waiting —
     * this is all one transaction, and the conditional update inside the ledger
     * is still the authoritative gate, so a race that slipped past the earlier
     * availability read is rejected here and takes the whole order with it.
     */
    private void consumeGuestStock(List<OrderItem> orderItems, Long orderId) {
        for (OrderItem item : orderItems) {
            Product product = item.getProduct();
            if (stockLedgerService.tryApplyAndRecord(
                    product.getProductId(), -item.getQuantity(), StockMovementReason.SALE,
                    "ORDER", orderId, "Guest checkout").isEmpty()) {
                throw new APIException("Insufficient stock for product: "
                        + product.getProductName()
                        + ". Requested: " + item.getQuantity());
            }
        }
    }

    /**
     * Atomically consumes one use of each applied coupon. The conditional UPDATE
     * enforces the usage limit in the database, so concurrent checkouts can never
     * push {@code usedCount} past {@code maxUses}. Called only when an order is
     * actually being placed — {@code previewOrder} never reaches here.
     */
    private void consumeCoupons(List<Long> couponIds, List<String> couponCodes) {
        for (int i = 0; i < couponIds.size(); i++) {
            if (couponRepository.tryConsume(couponIds.get(i)) == 0) {
                throw new APIException("Coupon usage limit reached: " + couponCodes.get(i));
            }
        }
    }
    private List<CartItem> getActiveCartItems(Cart cart) {
        if (cart.getCartItems() == null) {
            return Collections.emptyList();
        }
        return cart.getCartItems().stream()
                .filter(item -> !Boolean.TRUE.equals(item.getSavedForLater()))
                .toList();
    }

}
