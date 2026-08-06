package com.ecommerce.project.service;

import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderResponse;
import org.springframework.transaction.annotation.Transactional;

public interface OrderService {
    @Transactional
    OrderDTO placeOrder(String emailId, Long addressId, String paymentMethod, String pgName, String pgPaymentId, String pgStatus, String pgResponseMessage, String couponCode);

    OrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    OrderDTO updateOrder( Long orderId, String status);

    OrderResponse getAllSellerOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);
    OrderResponse getLoggedInUserOrders(String email, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    OrderDTO getOrderById(Long orderId, String email);

    byte[] exportOrdersToCsv();

    byte[] exportOrdersToPdf();
}
