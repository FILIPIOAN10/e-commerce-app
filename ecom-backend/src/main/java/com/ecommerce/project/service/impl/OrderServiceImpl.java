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
import com.ecommerce.project.service.NotificationService;
import com.ecommerce.project.service.OrderService;
import com.ecommerce.project.service.UserActivityLogService;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.model.Coupon;
import com.ecommerce.project.repository.CouponRepository;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

        // Update product stock
        cart.getCartItems().forEach(item -> {
            int quantity = item.getQuantity();
            Product product = item.getProduct();

            if (product.getQuantity() < quantity) {
                throw new APIException("Insufficient stock for product: "
                        + product.getProductName()
                        + ". Available: " + product.getQuantity()
                        + ", requested: " + quantity);
            }

            product.setQuantity(product.getQuantity() - quantity);
            productRepository.save(product);
            cartService.deleteProductFromCart(cart.getCartId(), item.getProduct().getProductId());
        });

        // Send back the order summary
        OrderDTO orderDTO = buildOrderDTO(savedOrder, orderItems, addressId, totalAmount);
        emailService.sendOrderConfirmationEmail(emailId, orderDTO);
        notificationService.notifyAdminNewOrder(savedOrder.getId(), emailId, totalAmount);
        userActivityLogService.log(emailId, "PLACE_ORDER", "Order " + savedOrder.getId() + " placed for $" + totalAmount);
        return orderDTO;
    }

    @Override
    public OrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {

        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber,pageSize,sortByAndOrder);
        Page<Order> pageOrders= orderRepository.findAll(pageDetails);

        // Got the order details
        List<Order> orders = pageOrders.getContent();

        List<OrderDTO> orderDTOS = orders.stream()
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
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);

        User seller = authUtil.loggedInUser();

        Page<Order> pageOrders = orderRepository.findOrdersBySellerId(seller.getUserId(), pageDetails);

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

    @Override
    public OrderResponse getLoggedInUserOrders(String email, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);

        // Apelăm metoda nouă din repository filtrată după email
        Page<Order> pageOrders = orderRepository.findByEmail(email, pageDetails);

        List<Order> orders = pageOrders.getContent();

        List<OrderDTO> orderDTOS = orders.stream()
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

    @Override
    public OrderDTO getOrderById(Long orderId, String email) {
        Order order = orderRepository.findById(orderId)
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
    public byte[] exportOrdersToCsv() {
        List<Order> orders = orderRepository.findAll(Sort.by(Sort.Direction.DESC, "orderDate"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos));

        writer.println("Order ID,Email,Order Date,Total Amount,Status,Payment Method");

        for (Order order : orders) {
            String paymentMethod = order.getPayment() != null ? order.getPayment().getPaymentMethod() : "N/A";
            writer.printf("%d,%s,%s,%.2f,%s,%s%n",
                    order.getId(),
                    escapeCsv(order.getEmail()),
                    order.getOrderDate(),
                    order.getTotalAmount(),
                    order.getOrderStatus(),
                    escapeCsv(paymentMethod));
        }

        writer.flush();
        return baos.toByteArray();
    }

    @Override
    public byte[] exportOrdersToPdf() {
        List<Order> orders = orderRepository.findAll(Sort.by(Sort.Direction.DESC, "orderDate"));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, baos);
            document.open();

            document.add(new Paragraph("Orders Report"));
            document.add(new Paragraph("Generated: " + LocalDate.now()));
            document.add(new Paragraph("Total Orders: " + orders.size()));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);

            String[] headers = {"Order ID", "Email", "Date", "Total ($)", "Status", "Payment"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header));
                cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
                table.addCell(cell);
            }

            for (Order order : orders) {
                table.addCell(String.valueOf(order.getId()));
                table.addCell(order.getEmail() != null ? order.getEmail() : "");
                table.addCell(order.getOrderDate() != null ? order.getOrderDate().toString() : "");
                table.addCell(String.format("%.2f", order.getTotalAmount()));
                table.addCell(order.getOrderStatus() != null ? order.getOrderStatus() : "");
                table.addCell(order.getPayment() != null && order.getPayment().getPaymentMethod() != null
                        ? order.getPayment().getPaymentMethod() : "N/A");
            }

            document.add(table);
            document.close();

            return baos.toByteArray();
        } catch (Exception e) {
            throw new APIException("Failed to generate PDF: " + e.getMessage());
        }
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

            if (!coupon.getActive()) {
                throw new APIException("Coupon is not active: " + code);
            }
            if (coupon.getExpiryDate().isBefore(LocalDate.now())) {
                throw new APIException("Coupon has expired: " + code);
            }
            if (coupon.getUsedCount() >= coupon.getMaxUses()) {
                throw new APIException("Coupon usage limit reached: " + code);
            }

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

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
