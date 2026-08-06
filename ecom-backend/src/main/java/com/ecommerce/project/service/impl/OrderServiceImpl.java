package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.*;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderItemDTO;
import com.ecommerce.project.payload.OrderResponse;
import com.ecommerce.project.repository.*;
import com.ecommerce.project.service.CartService;
import com.ecommerce.project.service.CouponService;
import com.ecommerce.project.service.EmailService;
import com.ecommerce.project.service.OrderService;
import com.ecommerce.project.util.AuthUtil;
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
    private final ModelMapper modelMapper;
    private final CouponRepository couponRepository;

    private final AuthUtil authUtil;


    @Override
    @Transactional // everything in this method successfully finishes or nothing finish
    public OrderDTO placeOrder(String emailId, Long addressId, String paymentMethod, String pgName, String pgPaymentId, String pgStatus, String pgResponseMessage, String couponCode) {

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

        double totalAmount = cart.getTotalPrice();

        if (couponCode != null && !couponCode.trim().isEmpty()) {
            Coupon coupon = couponRepository.findByCode(couponCode.toUpperCase())
                    .orElseThrow(() -> new APIException("Invalid coupon code: " + couponCode));

            if (!coupon.getActive()) {
                throw new APIException("Coupon is not active");
            }
            if (coupon.getExpiryDate().isBefore(LocalDate.now())) {
                throw new APIException("Coupon has expired");
            }
            if (coupon.getUsedCount() >= coupon.getMaxUses()) {
                throw new APIException("Coupon usage limit reached");
            }

            double discountAmount = totalAmount * coupon.getDiscountPercent() / 100.0;
            totalAmount = totalAmount - discountAmount;

            coupon.setUsedCount(coupon.getUsedCount() + 1);
            couponRepository.save(coupon);
        }

        order.setTotalAmount(totalAmount);


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
        OrderDTO orderDTO = modelMapper.map(savedOrder, OrderDTO.class);

        // transforming order item to that orderitemdto
        orderItems.forEach(item ->

                orderDTO.getItems().add(
                        modelMapper.map(item, OrderItemDTO.class)));
        orderDTO.setAddressId(addressId);

        emailService.sendOrderConfirmationEmail(emailId, orderDTO);

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
            "Placed", "Packed", "Shipped", "Delivered", "Cancelled"
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

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
