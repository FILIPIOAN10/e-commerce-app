package com.ecommerce.project.service;

import com.ecommerce.project.payload.GuestCheckoutRequestDTO;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderResponse;
import com.ecommerce.project.payload.OrderSummaryDTO;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

public interface OrderService {
    @Transactional
    OrderDTO placeOrder(String emailId, Long addressId, String paymentMethod, String pgName, String pgPaymentId, String pgStatus, String pgResponseMessage, List<String> couponCodes, String currencyCode);

    OrderSummaryDTO previewOrder(String emailId, Long addressId, List<String> couponCodes, String currencyCode);

    BigDecimal calculateShippingCost(Long addressId, BigDecimal cartTotal);

    OrderDTO placeGuestOrder(GuestCheckoutRequestDTO request);

    OrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    OrderDTO updateOrder( Long orderId, String status);

    OrderResponse getAllSellerOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);
    OrderResponse getLoggedInUserOrders(String email, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    OrderDTO getOrderById(Long orderId, String email);

}
