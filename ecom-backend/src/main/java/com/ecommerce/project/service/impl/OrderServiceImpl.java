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
import com.ecommerce.project.service.InventoryReservationService;
import com.ecommerce.project.service.OrderService;
import com.ecommerce.project.service.order.OrderStatus;
import com.ecommerce.project.service.order.event.OrderPlacedEvent;
import com.ecommerce.project.service.order.event.OrderStatusUpdatedEvent;
import com.ecommerce.project.service.payment.PaymentAttempt;
import com.ecommerce.project.service.payment.PaymentGatewayRegistry;
import com.ecommerce.project.service.payment.PaymentVerification;
import com.ecommerce.project.service.pricing.PriceBreakdown;
import com.ecommerce.project.service.pricing.PricingContext;
import com.ecommerce.project.service.pricing.PricingPipeline;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.PaginationUtil;
import com.ecommerce.project.util.SortWhitelist;
import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.repository.CouponRepository;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.Map;
import java.util.Objects;
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
    private final InventoryReservationService inventoryReservationService;
    private final ModelMapper modelMapper;
    private final CouponRepository couponRepository;

    private final AuthUtil authUtil;
    private final PaymentGatewayRegistry paymentGatewayRegistry;
    private final PricingPipeline pricingPipeline;
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

        PriceBreakdown pricing = pricingPipeline.price(
                PricingContext.of(cart.getTotalPrice(), address, couponCodes));

        verifyPayment(paymentMethod, pgName, pgPaymentId, pricing.total());

        // Coupon usage is only consumed once the order is actually being placed.
        consumeCoupons(pricing.appliedCouponIds(), pricing.appliedCouponCodes());

        order.setTotalAmount(pricing.total());
        order.setDiscountAmount(pricing.discountTotal());
        order.setShippingCost(pricing.shippingTotal());
        order.setAppliedCoupons(String.join(",", pricing.appliedCouponCodes()));

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
        OrderDTO orderDTO = buildOrderDTO(savedOrder, orderItems, addressId, pricing.total());
        eventPublisher.publishEvent(new OrderPlacedEvent(emailId, savedOrder.getId(), pricing.total(), orderDTO));
        return orderDTO;
    }

    @Override
    public OrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {

        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder,
                AppConstants.SORT_ORDERS_BY, SortWhitelist.ORDER);

        return buildOrderResponse(orderRepository.findAllIds(pageDetails));
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

        return buildOrderResponse(orderRepository.findIdsBySellerId(seller.getUserId(), pageDetails));
    }

    @Override
    public OrderResponse getLoggedInUserOrders(String email, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder,
                AppConstants.SORT_ORDERS_BY, SortWhitelist.ORDER);

        return buildOrderResponse(orderRepository.findIdsByEmail(email, pageDetails));
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
        return pricingPipeline.price(PricingContext.of(cartTotal, address, List.of()))
                .shippingTotal();
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

        // The pipeline is pure, so preview does not touch coupon usage counters.
        PriceBreakdown pricing = pricingPipeline.price(
                PricingContext.of(cart.getTotalPrice(), address, couponCodes));

        OrderSummaryDTO summary = new OrderSummaryDTO();
        summary.setEmail(emailId);
        summary.setAddressId(addressId);
        summary.setSubtotal(pricing.subtotal());
        summary.setDiscountAmount(pricing.discountTotal());
        summary.setShippingCost(pricing.shippingTotal());
        summary.setTotalAmount(pricing.total());
        summary.setAppliedCoupons(pricing.appliedCouponCodes());
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

        PriceBreakdown pricing = pricingPipeline.price(
                PricingContext.of(subtotal, address, request.getCouponCodes()));

        verifyPayment(request.getPaymentMethod(), request.getPgName(),
                request.getPgPaymentId(), pricing.total());

        consumeCoupons(pricing.appliedCouponIds(), pricing.appliedCouponCodes());

        order.setTotalAmount(pricing.total());
        order.setDiscountAmount(pricing.discountTotal());
        order.setShippingCost(pricing.shippingTotal());
        order.setAppliedCoupons(String.join(",", pricing.appliedCouponCodes()));

        Payment payment = new Payment(request.getPaymentMethod(), request.getPgPaymentId(), request.getPgStatus(), request.getPgResponseMessage(), request.getPgName());
        payment.setOrder(order);
        payment = paymentRepository.save(payment);
        order.setPayment(payment);
        Order savedOrder = orderRepository.save(order);

        orderItems.forEach(item -> item.setOrder(savedOrder));
        List<OrderItem> savedOrderItems = orderItemRepository.saveAll(orderItems);

        OrderDTO guestOrderDTO = buildOrderDTO(savedOrder, savedOrderItems, address.getAddressId(), pricing.total());
        eventPublisher.publishEvent(new OrderPlacedEvent(request.getEmail(), savedOrder.getId(), pricing.total(), guestOrderDTO));

        return guestOrderDTO;
    }

    /**
     * Phase 2 of two-phase pagination: hydrates one page of order IDs into full
     * order graphs, preserving the ordering established by the ID query.
     */
    private OrderResponse buildOrderResponse(Page<Long> idPage) {
        List<Long> ids = idPage.getContent();

        List<OrderDTO> orderDTOS;
        if (ids.isEmpty()) {
            orderDTOS = List.of();
        } else {
            Map<Long, Order> byId = orderRepository.findByIdInWithDetails(ids).stream()
                    .collect(Collectors.toMap(Order::getId, order -> order));
            orderDTOS = ids.stream()
                    .map(byId::get)
                    .filter(Objects::nonNull)
                    .map(order -> modelMapper.map(order, OrderDTO.class))
                    .toList();
        }

        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setContent(orderDTOS);
        orderResponse.setPageNumber(idPage.getNumber());
        orderResponse.setPageSize(idPage.getSize());
        orderResponse.setTotalElements(idPage.getTotalElements());
        orderResponse.setTotalPages(idPage.getTotalPages());
        orderResponse.setLastPage(idPage.isLast());
        return orderResponse;
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

    private void verifyPayment(String paymentMethod, String pgName,
                               String pgPaymentId, double expectedTotal) {
        PaymentAttempt attempt = new PaymentAttempt(paymentMethod, pgName, pgPaymentId, expectedTotal);
        if (!attempt.hasReference()) {
            return;
        }

        // Gateway-agnostic: a payment reference may back exactly one order.
        paymentRepository.findByPgPaymentId(pgPaymentId).ifPresent(existing -> {
            throw new APIException("This payment has already been used for order "
                    + existing.getOrder().getId());
        });

        // Gateway-specific: confirm the payment succeeded for the expected amount.
        // No matching gateway (e.g. cash on delivery) means nothing to verify here.
        paymentGatewayRegistry.select(attempt).ifPresent(gateway -> {
            PaymentVerification result = gateway.verify(attempt);
            if (!result.verified()) {
                throw new APIException(result.reason());
            }
        });
    }

}
