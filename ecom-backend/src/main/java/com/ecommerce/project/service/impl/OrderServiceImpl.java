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
import com.ecommerce.project.service.UserActivityLogService;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.PaginationUtil;
import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.model.Coupon;
import com.ecommerce.project.repository.CouponRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final CartRepository cartRepository;
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

        CouponApplicationResult couponResult = applyCoupons(couponCodes, subtotal);
        double totalAfterDiscount = subtotal - couponResult.getDiscountAmount();
        double shippingCost = calculateShippingCost(addressId, subtotal);
        double totalAmount = totalAfterDiscount + shippingCost;

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
        cart.getCartItems().forEach(item ->
            cartService.deleteProductFromCart(cart.getCartId(), item.getProduct().getProductId())
        );

        // Send back the order summary
        OrderDTO orderDTO = buildOrderDTO(savedOrder, orderItems, addressId, totalAmount);
        emailService.sendOrderConfirmationEmail(emailId, orderDTO);
        notificationService.notifyAdminNewOrder(savedOrder.getId(), emailId, totalAmount);
        userActivityLogService.log(emailId, "PLACE_ORDER", "Order " + savedOrder.getId() + " placed for $" + totalAmount);
        return orderDTO;
    }

    @Override
    public OrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {

        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder, AppConstants.SORT_ORDERS_BY);
        Page<Order> pageOrders = orderRepository.findAllWithDetails(pageDetails);

        return buildOrderResponse(pageOrders);
    }

    private static final List<String> VALID_STATUSES = List.of(
            "Placed", "Packed", "Shipped", "Delivered", "Cancelled",
            "Return Requested", "Returned", "Refunded"
    );
    @Override
    public OrderDTO updateOrder(Long orderId, String status) {
        if (status == null || !VALID_STATUSES.contains(status)) {
            throw new APIException("Invalid order status: " + status
                    + ". Valid statuses: " + VALID_STATUSES);
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));
        order.setOrderStatus(status);
        orderRepository.save(order);
        OrderDTO orderDTO = modelMapper.map(order, OrderDTO.class);
        emailService.sendOrderStatusUpdateEmail(order.getEmail(), orderDTO);
        notificationService.notifyUserOrderStatusChanged(orderId, order.getEmail(), status);
        return orderDTO;
    }

    @Override
    public OrderResponse getAllSellerOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder, AppConstants.SORT_ORDERS_BY);

        User seller = authUtil.loggedInUser();

        Page<Order> pageOrders = orderRepository.findOrdersBySellerIdWithDetails(seller.getUserId(), pageDetails);

        return buildOrderResponse(pageOrders);
    }

    @Override
    public OrderResponse getLoggedInUserOrders(String email, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder, AppConstants.SORT_ORDERS_BY);

        // Apelăm metoda nouă din repository filtrată după email
        Page<Order> pageOrders = orderRepository.findByEmailWithDetails(email, pageDetails);

        return buildOrderResponse(pageOrders);
    }

    @Override
    public OrderDTO getOrderById(Long orderId, String email) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));

        if (!order.getEmail().equals(email)) {
            throw new APIException("You can only track your own orders");
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
        double baseCost = 5.0;
        if ("RO".equalsIgnoreCase(address.getCountry()) || "Romania".equalsIgnoreCase(address.getCountry())) {
            baseCost = 3.0;
        }
        if (cartTotal >= 100.0) {
            return 0.0;
        }
        return baseCost;
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
        CouponApplicationResult couponResult = applyCoupons(couponCodes, subtotal);
        double totalAfterDiscount = subtotal - couponResult.getDiscountAmount();
        double shippingCost = calculateShippingCost(addressId, totalAfterDiscount);
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

            product.setQuantity(product.getQuantity() - dto.getQuantity());
            productRepository.save(product);
        }

        CouponApplicationResult couponResult = applyCoupons(request.getCouponCodes(), subtotal);
        double totalAfterDiscount = subtotal - couponResult.getDiscountAmount();
        double shippingCost = calculateShippingCost(address.getAddressId(), totalAfterDiscount);
        double totalAmount = totalAfterDiscount + shippingCost;

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

        emailService.sendOrderConfirmationEmail(request.getEmail(), buildOrderDTO(savedOrder, savedOrderItems, address.getAddressId(), totalAmount));
        notificationService.notifyAdminNewOrder(savedOrder.getId(), request.getEmail(), totalAmount);

        return buildOrderDTO(savedOrder, savedOrderItems, address.getAddressId(), totalAmount);
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

    private CouponApplicationResult applyCoupons(List<String> couponCodes, double subtotal) {
        double totalAfterDiscount = subtotal;
        double totalDiscount = 0.0;
        List<String> appliedCodes = new ArrayList<>();

        if (couponCodes == null || couponCodes.isEmpty()) {
            return new CouponApplicationResult(totalDiscount, appliedCodes);
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

            coupon.setUsedCount(coupon.getUsedCount() + 1);
            couponRepository.save(coupon);
        }

        return new CouponApplicationResult(totalDiscount, appliedCodes);
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

        CouponApplicationResult(double discountAmount, List<String> appliedCodes) {
            this.discountAmount = discountAmount;
            this.appliedCodes = appliedCodes;
        }

        public double getDiscountAmount() {
            return discountAmount;
        }

        public List<String> getAppliedCodes() {
            return appliedCodes;
        }
    }

}
