package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.*;
import com.ecommerce.project.payload.CartItemDTO;
import com.ecommerce.project.payload.GuestCheckoutRequestDTO;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderItemDTO;
import com.ecommerce.project.payload.OrderResponse;
import com.ecommerce.project.payload.OrderSummaryDTO;
import com.ecommerce.project.repository.*;
import com.ecommerce.project.service.CartService;
import com.ecommerce.project.service.CouponService;
import com.ecommerce.project.service.EmailService;
import com.ecommerce.project.service.InventoryReservationService;
import com.ecommerce.project.service.NotificationService;
import com.ecommerce.project.service.OrderService;
import com.ecommerce.project.service.StripeService;
import com.ecommerce.project.service.UserActivityLogService;
import com.ecommerce.project.service.order.OrderStatus;
import com.ecommerce.project.service.pricing.Money;
import com.ecommerce.project.service.pricing.ShippingCalculator;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.PaginationUtil;
import com.ecommerce.project.util.SortWhitelist;
import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.model.Coupon;
import com.ecommerce.project.repository.CouponRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.stripe.model.PaymentIntent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final CartService cartService;
    private final CouponService couponService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final UserActivityLogService userActivityLogService;
    private final InventoryReservationService inventoryReservationService;
    private final ModelMapper modelMapper;
    private final CouponRepository couponRepository;

    private final AuthUtil authUtil;
    private final StripeService stripeService;
    private final ShippingCalculator shippingCalculator;
    private final ApplicationEventPublisher eventPublisher;


    @Override
    @Transactional // everything in this method successfully finishes or nothing finish
    public OrderDTO placeOrder(String emailId, Long addressId, String paymentMethod, String pgName, String pgPaymentId, String pgStatus, String pgResponseMessage, List<String> couponCodes) {

        Cart cart = cartRepository.findCartByEmail(emailId);
        if (cart == null) {
            throw new ResourceNotFoundException("Cart", "email", emailId);
        }

        List<CartItem> cartItems = cart.getCartItems();
        if (cartItems.isEmpty()) {
            throw new APIException("Cart is Empty");
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

        double subtotal = cart.getTotalPrice();

        CouponApplicationResult couponResult = calculateDiscount(couponCodes, subtotal);
        double totalAfterDiscount = subtotal - couponResult.getDiscountAmount();
        double shippingCost = calculateShippingCost(address, totalAfterDiscount);
        double totalAmount = totalAfterDiscount + shippingCost;

        verifyPayment(paymentMethod, pgName, pgPaymentId, totalAmount);

        // Coupon usage is only consumed once the order is actually being placed.
        consumeCoupons(couponResult);

        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(couponResult.getDiscountAmount());
        order.setShippingCost(shippingCost);
        order.setAppliedCoupons(String.join(",", couponResult.getAppliedCodes()));

        Payment payment = new Payment(paymentMethod, pgPaymentId, pgStatus, pgResponseMessage, pgName);
        payment.setOrder(order);
        payment = paymentRepository.save(payment);
        order.setPayment(payment);
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
        cartItemRepository.deleteAllByCartId(cart.getCartId());
        cart.setTotalPrice(0.0);
        cartRepository.save(cart);

        // Send back the order summary
        OrderDTO orderDTO = buildOrderDTO(savedOrder, orderItems, addressId, totalAmount);
        eventPublisher.publishEvent(new OrderPlacedEvent(emailId, savedOrder.getId(), totalAmount, orderDTO));
        return orderDTO;
    }

    @Override
    public OrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {

        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder,
                AppConstants.SORT_ORDERS_BY, SortWhitelist.ORDER);
        Page<Order> pageOrders = orderRepository.findAllWithDetails(pageDetails);

        return buildOrderResponse(pageOrders);
    }

    @Override
    @Transactional
    public OrderDTO updateOrder(Long orderId, String status) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));

        String previousStatus = order.getOrderStatus();
        OrderStatus.assertTransitionAllowed(previousStatus, status);

        if (OrderStatus.releasesStock(previousStatus, status)) {
            restockOrderItems(order);
        }

        order.setOrderStatus(status);
        orderRepository.save(order);
        OrderDTO orderDTO = modelMapper.map(order, OrderDTO.class);
        eventPublisher.publishEvent(new OrderStatusUpdatedEvent(orderId, order.getEmail(), status, orderDTO));
        return orderDTO;
    }

    /**
     * Returns the stock held by an order back to inventory. Called when an order
     * moves to a stock-releasing status (Cancelled / Returned) so that cancelled
     * orders no longer silently destroy inventory.
     */
    private void restockOrderItems(Order order) {
        if (order.getOrderItems() == null) {
            return;
        }
        for (OrderItem item : order.getOrderItems()) {
            if (item.getProduct() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                continue;
            }
            productRepository.incrementStock(item.getProduct().getProductId(), item.getQuantity());
        }
    }

    @Override
    public OrderResponse getAllSellerOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder,
                AppConstants.SORT_ORDERS_BY, SortWhitelist.ORDER);

        User seller = authUtil.loggedInUser();

        Page<Order> pageOrders = orderRepository.findOrdersBySellerIdWithDetails(seller.getUserId(), pageDetails);

        return buildOrderResponse(pageOrders);
    }

    @Override
    public OrderResponse getLoggedInUserOrders(String email, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder,
                AppConstants.SORT_ORDERS_BY, SortWhitelist.ORDER);

        Page<Order> pageOrders = orderRepository.findByEmailWithDetails(email, pageDetails);

        return buildOrderResponse(pageOrders);
    }

    @Override
    public OrderDTO getOrderById(Long orderId, String email) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));

        // Case-insensitive to match the comparison used when the order is placed.
        if (order.getEmail() == null || !order.getEmail().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("You can only access your own orders");
        }

        OrderDTO orderDTO = modelMapper.map(order, OrderDTO.class);
        orderDTO.setAddressId(order.getAddress().getAddressId());

        List<OrderItemDTO> itemDTOs = order.getOrderItems().stream()
                .map(item -> modelMapper.map(item, OrderItemDTO.class))
                .toList();
        orderDTO.setItems(itemDTOs);

        return orderDTO;
    }


    @Override
    public double calculateShippingCost(Long addressId, double cartTotal) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));
        return calculateShippingCost(address, cartTotal);
    }

    private double calculateShippingCost(Address address, double cartTotal) {
        return shippingCalculator.calculate(address, cartTotal);
    }

    @Override
    public OrderSummaryDTO previewOrder(String emailId, Long addressId, List<String> couponCodes) {
        Cart cart = cartRepository.findCartByEmail(emailId);
        if (cart == null || cart.getCartItems().isEmpty()) {
            throw new APIException("Cart is Empty");
        }

        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));
        if (address.getUser() == null || !emailId.equalsIgnoreCase(address.getUser().getEmail())) {
            throw new APIException("Address does not belong to the current user");
        }

        // Reserve stock for 10 minutes (TTL) to prevent race conditions at checkout
        inventoryReservationService.reserveCartItems(cart.getCartId(), cart.getCartItems());

        double subtotal = cart.getTotalPrice();
        // Preview must not mutate coupon usage counters.
        CouponApplicationResult couponResult = calculateDiscount(couponCodes, subtotal);
        double totalAfterDiscount = subtotal - couponResult.getDiscountAmount();
        double shippingCost = calculateShippingCost(address, totalAfterDiscount);
        double totalAmount = totalAfterDiscount + shippingCost;

        OrderSummaryDTO summary = new OrderSummaryDTO();
        summary.setEmail(emailId);
        summary.setAddressId(addressId);
        summary.setSubtotal(subtotal);
        summary.setDiscountAmount(couponResult.getDiscountAmount());
        summary.setShippingCost(shippingCost);
        summary.setTotalAmount(totalAmount);
        summary.setAppliedCoupons(couponResult.getAppliedCodes());
        return summary;
    }

    @Override
    @Transactional
    public OrderDTO placeGuestOrder(GuestCheckoutRequestDTO request) {
        Address address = modelMapper.map(request.getAddress(), Address.class);
        address = addressRepository.save(address);

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new APIException("Cart is Empty");
        }

        double subtotal = 0.0;
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

            double price = product.getSpecialPrice();
            subtotal += price * dto.getQuantity();

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setQuantity(dto.getQuantity());
            orderItem.setDiscount(product.getDiscount());
            orderItem.setOrderedProductPrice(price);
            orderItem.setOrder(order);
            orderItems.add(orderItem);

            // Atomic conditional decrement: 0 rows updated means another request
            // took the remaining stock between our read and this write.
            if (productRepository.decrementStock(product.getProductId(), dto.getQuantity()) == 0) {
                throw new APIException("Insufficient stock for product: "
                        + product.getProductName()
                        + ". Requested: " + dto.getQuantity());
            }
        }

        CouponApplicationResult couponResult = calculateDiscount(request.getCouponCodes(), subtotal);
        double totalAfterDiscount = subtotal - couponResult.getDiscountAmount();
        double shippingCost = calculateShippingCost(address, totalAfterDiscount);
        double totalAmount = totalAfterDiscount + shippingCost;

        verifyPayment(request.getPaymentMethod(), request.getPgName(),
                request.getPgPaymentId(), totalAmount);

        consumeCoupons(couponResult);

        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(couponResult.getDiscountAmount());
        order.setShippingCost(shippingCost);
        order.setAppliedCoupons(String.join(",", couponResult.getAppliedCodes()));

        Payment payment = new Payment(request.getPaymentMethod(), request.getPgPaymentId(), request.getPgStatus(), request.getPgResponseMessage(), request.getPgName());
        payment.setOrder(order);
        payment = paymentRepository.save(payment);
        order.setPayment(payment);
        Order savedOrder = orderRepository.save(order);

        orderItems.forEach(item -> item.setOrder(savedOrder));
        List<OrderItem> savedOrderItems = orderItemRepository.saveAll(orderItems);

        OrderDTO guestOrderDTO = buildOrderDTO(savedOrder, savedOrderItems, address.getAddressId(), totalAmount);
        eventPublisher.publishEvent(new OrderPlacedEvent(request.getEmail(), savedOrder.getId(), totalAmount, guestOrderDTO));

        return guestOrderDTO;
    }

    private OrderResponse buildOrderResponse(Page<Order> pageOrders) {
        List<OrderDTO> orderDTOS = pageOrders.getContent().stream()
                .map(order -> modelMapper.map(order, OrderDTO.class))
                .toList();
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setContent(orderDTOS);
        orderResponse.setPageNumber(pageOrders.getNumber());
        orderResponse.setPageSize(pageOrders.getSize());
        orderResponse.setTotalElements(pageOrders.getTotalElements());
        orderResponse.setTotalPages(pageOrders.getTotalPages());
        orderResponse.setLastPage(pageOrders.isLast());
        return orderResponse;
    }

    /**
     * Pure calculation: resolves and validates coupons and computes the discount
     * without mutating any coupon usage counters. Safe to call from read-only
     * paths such as {@code previewOrder}.
     */
    private CouponApplicationResult calculateDiscount(List<String> couponCodes, double subtotal) {
        double totalAfterDiscount = subtotal;
        double totalDiscount = 0.0;
        List<String> appliedCodes = new ArrayList<>();
        List<Long> appliedIds = new ArrayList<>();

        if (couponCodes == null || couponCodes.isEmpty()) {
            return new CouponApplicationResult(totalDiscount, appliedCodes, appliedIds);
        }

        for (String code : couponCodes) {
            if (code == null || code.isBlank()) continue;

            Coupon coupon = couponRepository.findByCode(code.toUpperCase())
                    .orElseThrow(() -> new APIException("Invalid coupon code: " + code));

            couponService.validateCouponState(coupon, code);

            double discount = totalAfterDiscount * coupon.getDiscountPercent() / 100.0;
            totalAfterDiscount -= discount;
            totalDiscount += discount;
            appliedCodes.add(coupon.getCode());
            appliedIds.add(coupon.getId());
        }

        return new CouponApplicationResult(totalDiscount, appliedCodes, appliedIds);
    }

    /**
     * Atomically consumes one use of each applied coupon. The conditional UPDATE
     * enforces the usage limit in the database, so concurrent checkouts can never
     * push {@code usedCount} past {@code maxUses}.
     */
    private void consumeCoupons(CouponApplicationResult result) {
        List<Long> ids = result.getAppliedCouponIds();
        List<String> codes = result.getAppliedCodes();
        for (int i = 0; i < ids.size(); i++) {
            if (couponRepository.tryConsume(ids.get(i)) == 0) {
                throw new APIException("Coupon usage limit reached: " + codes.get(i));
            }
        }
    }

    private OrderDTO buildOrderDTO(Order order, List<OrderItem> orderItems, Long addressId, double totalAmount) {
        OrderDTO orderDTO = modelMapper.map(order, OrderDTO.class);
        orderDTO.setTotalAmount(totalAmount);
        orderDTO.setDiscountAmount(order.getDiscountAmount());
        orderDTO.setShippingCost(order.getShippingCost());
        orderDTO.setAddressId(addressId);
        orderDTO.setAppliedCoupons(order.getAppliedCoupons() != null
                ? List.of(order.getAppliedCoupons().split(","))
                : List.of());

        List<OrderItemDTO> itemDTOs = orderItems.stream()
                .map(item -> modelMapper.map(item, OrderItemDTO.class))
                .toList();
        orderDTO.setItems(itemDTOs);

        return orderDTO;
    }

    private static class CouponApplicationResult {
        private final double discountAmount;
        private final List<String> appliedCodes;
        private final List<Long> appliedCouponIds;

        CouponApplicationResult(double discountAmount, List<String> appliedCodes, List<Long> appliedCouponIds) {
            this.discountAmount = discountAmount;
            this.appliedCodes = appliedCodes;
            this.appliedCouponIds = appliedCouponIds;
        }

        public double getDiscountAmount() {
            return discountAmount;
        }

        public List<String> getAppliedCodes() {
            return appliedCodes;
        }

        public List<Long> getAppliedCouponIds() {
            return appliedCouponIds;
        }
    }

    private boolean isStripePayment(String paymentMethod, String pgName) {
        return "STRIPE".equalsIgnoreCase(paymentMethod)
                || "online".equalsIgnoreCase(paymentMethod)
                || "Stripe".equalsIgnoreCase(pgName);
    }

    private void verifyPayment(String paymentMethod, String pgName,
                               String pgPaymentId, double expectedTotal) {
        if (pgPaymentId == null || pgPaymentId.isBlank()) {
            return;
        }

        paymentRepository.findByPgPaymentId(pgPaymentId).ifPresent(existing -> {
            throw new APIException("This payment has already been used for order "
                    + existing.getOrder().getId());
        });

        if (isStripePayment(paymentMethod, pgName)) {
            PaymentIntent intent = stripeService.retrievePaymentIntent(pgPaymentId);
            if (!"succeeded".equals(intent.getStatus())) {
                throw new APIException("Payment has not succeeded");
            }

            long expectedCents = Money.toCents(expectedTotal);
            if (intent.getAmount() == null || intent.getAmount() != expectedCents) {
                throw new APIException("Payment amount does not match order total");
            }
        }
    }

    public record OrderPlacedEvent(String email, Long orderId, double totalAmount, OrderDTO orderDTO) {
    }

    public record OrderStatusUpdatedEvent(Long orderId, String email, String status, OrderDTO orderDTO) {
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        emailService.sendOrderConfirmationEmail(event.email(), event.orderDTO());
        notificationService.notifyAdminNewOrder(event.orderId(), event.email(), event.totalAmount());
        userActivityLogService.log(event.email(), "PLACE_ORDER", "Order " + event.orderId() + " placed for $" + event.totalAmount());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusUpdated(OrderStatusUpdatedEvent event) {
        emailService.sendOrderStatusUpdateEmail(event.email(), event.orderDTO());
        notificationService.notifyUserOrderStatusChanged(event.orderId(), event.email(), event.status());
    }

}
