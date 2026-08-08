package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.ReturnRequest;
import com.ecommerce.project.payload.ReturnRequestDTO;
import com.ecommerce.project.repository.OrderRepository;
import com.ecommerce.project.repository.ReturnRequestRepository;
import com.ecommerce.project.service.NotificationService;
import com.ecommerce.project.service.ReturnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnServiceImpl implements ReturnService {

    private final ReturnRequestRepository returnRequestRepository;
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_REFUNDED = "REFUNDED";

    @Override
    @Transactional
    public ReturnRequestDTO requestReturn(Long orderId, String email, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));

        if (!order.getEmail().equals(email)) {
            throw new APIException("You can only request a return for your own orders");
        }

        if (!"Delivered".equals(order.getOrderStatus())) {
            throw new APIException("Returns can only be requested for delivered orders");
        }

        if (returnRequestRepository.existsByOrderIdAndStatus(orderId, STATUS_PENDING)) {
            throw new APIException("A pending return request already exists for this order");
        }

        if (returnRequestRepository.existsByOrderIdAndStatus(orderId, STATUS_APPROVED)) {
            throw new APIException("A return request for this order has already been approved");
        }

        ReturnRequest returnRequest = new ReturnRequest();
        returnRequest.setOrderId(orderId);
        returnRequest.setUserEmail(email);
        returnRequest.setReason(reason);
        returnRequest.setStatus(STATUS_PENDING);
        returnRequest.setRequestedAt(LocalDateTime.now());

        returnRequest = returnRequestRepository.save(returnRequest);

        order.setOrderStatus("Return Requested");
        orderRepository.save(order);

        notificationService.notifyAdminNewOrder(orderId, email, order.getTotalAmount());

        return toDTO(returnRequest, order.getTotalAmount());
    }

    @Override
    public Page<ReturnRequestDTO> getAllReturnRequests(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("requestedAt").descending());
        Page<ReturnRequest> requests = returnRequestRepository.findAllByOrderByRequestedAtDesc(pageRequest);
        return requests.map(r -> toDTO(r, getOrderTotal(r.getOrderId())));
    }

    @Override
    public Page<ReturnRequestDTO> getMyReturnRequests(String email, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("requestedAt").descending());
        Page<ReturnRequest> requests = returnRequestRepository.findByUserEmailOrderByRequestedAtDesc(email, pageRequest);
        return requests.map(r -> toDTO(r, getOrderTotal(r.getOrderId())));
    }

    @Override
    @Transactional
    public ReturnRequestDTO approveReturn(Long returnId, String adminNote) {
        ReturnRequest returnRequest = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnRequest", "id", returnId));

        if (!STATUS_PENDING.equals(returnRequest.getStatus())) {
            throw new APIException("Only pending return requests can be approved");
        }

        returnRequest.setStatus(STATUS_APPROVED);
        returnRequest.setAdminNote(adminNote);
        returnRequest.setProcessedAt(LocalDateTime.now());
        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        final Long savedOrderId = saved.getOrderId();

        Order order = orderRepository.findById(savedOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", savedOrderId));
        order.setOrderStatus("Returned");
        orderRepository.save(order);

        notificationService.notifyUserOrderStatusChanged(order.getId(), order.getEmail(), "Returned");

        return toDTO(saved, order.getTotalAmount());
    }

    @Override
    @Transactional
    public ReturnRequestDTO rejectReturn(Long returnId, String adminNote) {
        ReturnRequest returnRequest = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnRequest", "id", returnId));

        if (!STATUS_PENDING.equals(returnRequest.getStatus())) {
            throw new APIException("Only pending return requests can be rejected");
        }

        returnRequest.setStatus(STATUS_REJECTED);
        returnRequest.setAdminNote(adminNote);
        returnRequest.setProcessedAt(LocalDateTime.now());
        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        final Long savedOrderId = saved.getOrderId();

        Order order = orderRepository.findById(savedOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", savedOrderId));
        order.setOrderStatus("Delivered");
        orderRepository.save(order);

        notificationService.notifyUserOrderStatusChanged(order.getId(), order.getEmail(), "Return Rejected — Order Delivered");

        return toDTO(saved, order.getTotalAmount());
    }

    @Override
    @Transactional
    public ReturnRequestDTO markAsRefunded(Long returnId) {
        ReturnRequest returnRequest = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnRequest", "id", returnId));

        if (!STATUS_APPROVED.equals(returnRequest.getStatus())) {
            throw new APIException("Only approved returns can be marked as refunded");
        }

        returnRequest.setStatus(STATUS_REFUNDED);
        returnRequest.setProcessedAt(LocalDateTime.now());
        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        final Long savedOrderId = saved.getOrderId();

        Order order = orderRepository.findById(savedOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", savedOrderId));
        order.setOrderStatus("Refunded");
        orderRepository.save(order);

        notificationService.notifyUserOrderStatusChanged(order.getId(), order.getEmail(), "Refunded");

        return toDTO(saved, order.getTotalAmount());
    }

    private Double getOrderTotal(Long orderId) {
        return orderRepository.findById(orderId)
                .map(Order::getTotalAmount)
                .orElse(0.0);
    }

    private ReturnRequestDTO toDTO(ReturnRequest r, Double refundAmount) {
        ReturnRequestDTO dto = new ReturnRequestDTO();
        dto.setId(r.getId());
        dto.setOrderId(r.getOrderId());
        dto.setUserEmail(r.getUserEmail());
        dto.setReason(r.getReason());
        dto.setStatus(r.getStatus());
        dto.setRequestedAt(r.getRequestedAt());
        dto.setProcessedAt(r.getProcessedAt());
        dto.setAdminNote(r.getAdminNote());
        dto.setRefundAmount(refundAmount);
        return dto;
    }
}
