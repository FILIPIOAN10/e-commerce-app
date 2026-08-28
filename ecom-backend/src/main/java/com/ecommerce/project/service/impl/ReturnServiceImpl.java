package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.ReturnRequest;
import com.ecommerce.project.payload.ReturnRequestDTO;
import com.ecommerce.project.payload.TrackingStatus;
import com.ecommerce.project.repository.OrderRepository;
import com.ecommerce.project.repository.ReturnRequestRepository;
import com.ecommerce.project.service.CourierTrackingService;
import com.ecommerce.project.service.NotificationService;
import com.ecommerce.project.service.ReturnService;
import com.ecommerce.project.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnServiceImpl implements ReturnService {

    private final ReturnRequestRepository returnRequestRepository;
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;
    private final CourierTrackingService courierTrackingService;

    private static final String STATUS_REQUESTED = "REQUESTED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_SHIPPED_BACK = "SHIPPED_BACK";
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

        if (returnRequestRepository.existsByOrderIdAndStatus(orderId, STATUS_REQUESTED)) {
            throw new APIException("A pending return request already exists for this order");
        }

        if (returnRequestRepository.existsByOrderIdAndStatus(orderId, STATUS_APPROVED)) {
            throw new APIException("A return request for this order has already been approved");
        }

        ReturnRequest returnRequest = new ReturnRequest();
        returnRequest.setOrderId(orderId);
        returnRequest.setUserEmail(email);
        returnRequest.setReason(reason);
        returnRequest.setStatus(STATUS_REQUESTED);
        returnRequest.setRequestedAt(LocalDateTime.now());
        returnRequest.setRefundAmount(order.getTotalAmount());

        returnRequest = returnRequestRepository.save(returnRequest);

        order.setOrderStatus("Return Requested");
        orderRepository.save(order);

        notificationService.notifyAdminNewOrder(orderId, email, order.getTotalAmount());

        return toDTO(returnRequest);
    }

    @Override
    public Page<ReturnRequestDTO> getAllReturnRequests(int page, int size) {
        Pageable pageRequest = PageRequest.of(page, size, PaginationUtil.buildSort("requestedAt", "desc"));
        return mapPage(returnRequestRepository.findAllByOrderByRequestedAtDesc(pageRequest));
    }

    @Override
    public Page<ReturnRequestDTO> getMyReturnRequests(String email, int page, int size) {
        Pageable pageRequest = PageRequest.of(page, size, PaginationUtil.buildSort("requestedAt", "desc"));
        return mapPage(returnRequestRepository.findByUserEmailOrderByRequestedAtDesc(email, pageRequest));
    }

    /** Resolves every page row's order total in one query instead of one per row. */
    private Page<ReturnRequestDTO> mapPage(Page<ReturnRequest> requests) {
        List<Long> orderIds = requests.getContent().stream()
                .map(ReturnRequest::getOrderId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Double> totals = new HashMap<>();
        if (!orderIds.isEmpty()) {
            for (Object[] row : orderRepository.findTotalsByIds(orderIds)) {
                totals.put((Long) row[0], (Double) row[1]);
            }
        }
        return requests.map(r -> toDTO(r, totals.getOrDefault(r.getOrderId(), 0.0)));
    }

    @Override
    @Transactional
    public ReturnRequestDTO approveReturn(Long returnId, String adminNote) {
        ReturnRequest returnRequest = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnRequest", "id", returnId));

        if (!STATUS_REQUESTED.equals(returnRequest.getStatus())) {
            throw new APIException("Only requested return requests can be approved");
        }

        returnRequest.setStatus(STATUS_APPROVED);
        returnRequest.setAdminNote(adminNote);
        returnRequest.setProcessedAt(LocalDateTime.now());
        ReturnRequest saved = returnRequestRepository.save(returnRequest);

        updateOrderStatusAndNotify(saved.getOrderId(), "Returned", "Return Approved");

        return toDTO(saved);
    }

    @Override
    @Transactional
    public ReturnRequestDTO rejectReturn(Long returnId, String adminNote) {
        ReturnRequest returnRequest = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnRequest", "id", returnId));

        if (!STATUS_REQUESTED.equals(returnRequest.getStatus())) {
            throw new APIException("Only requested return requests can be rejected");
        }

        returnRequest.setStatus(STATUS_REJECTED);
        returnRequest.setAdminNote(adminNote);
        returnRequest.setProcessedAt(LocalDateTime.now());
        ReturnRequest saved = returnRequestRepository.save(returnRequest);

        updateOrderStatusAndNotify(saved.getOrderId(), "Delivered", "Return Rejected");

        return toDTO(saved);
    }

    @Override
    @Transactional
    public ReturnRequestDTO markAsRefunded(Long returnId) {
        ReturnRequest returnRequest = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnRequest", "id", returnId));

        if (!STATUS_SHIPPED_BACK.equals(returnRequest.getStatus()) && !STATUS_APPROVED.equals(returnRequest.getStatus())) {
            throw new APIException("Only approved or shipped-back returns can be marked as refunded");
        }

        returnRequest.setStatus(STATUS_REFUNDED);
        if (returnRequest.getProcessedAt() == null) {
            returnRequest.setProcessedAt(LocalDateTime.now());
        }
        ReturnRequest saved = returnRequestRepository.save(returnRequest);

        updateOrderStatusAndNotify(saved.getOrderId(), "Refunded", "Refunded");

        return toDTO(saved);
    }

    @Override
    @Transactional
    public ReturnRequestDTO provideTracking(Long returnId, String email, String carrierName, String trackingNumber) {
        ReturnRequest returnRequest = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnRequest", "id", returnId));

        assertOwnership(returnRequest, email);

        if (!STATUS_APPROVED.equals(returnRequest.getStatus())) {
            throw new APIException("Tracking can only be provided for approved returns");
        }

        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new APIException("Tracking number is required");
        }

        returnRequest.setCarrierName(carrierName);
        returnRequest.setTrackingNumber(trackingNumber);
        returnRequest.setShippedBackAt(LocalDateTime.now());
        returnRequest.setStatus(STATUS_SHIPPED_BACK);

        TrackingStatus tracking = courierTrackingService.track(carrierName, trackingNumber);
        returnRequest.setTrackingStatus(tracking.getStatus());
        returnRequest.setLastTrackedAt(LocalDateTime.now());

        ReturnRequest saved = returnRequestRepository.save(returnRequest);

        updateOrderStatusAndNotify(saved.getOrderId(), "Returned", "Return Shipped Back");

        return toDTO(saved);
    }

    @Override
    @Transactional
    public ReturnRequestDTO refreshTracking(Long returnId) {
        ReturnRequest returnRequest = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnRequest", "id", returnId));

        if (returnRequest.getTrackingNumber() == null) {
            throw new APIException("No tracking number available for this return");
        }

        TrackingStatus tracking = courierTrackingService.track(
                returnRequest.getCarrierName(),
                returnRequest.getTrackingNumber());

        returnRequest.setTrackingStatus(tracking.getStatus());
        returnRequest.setLastTrackedAt(LocalDateTime.now());
        returnRequest = returnRequestRepository.save(returnRequest);

        if ("DELIVERED".equals(tracking.getStatus())) {
            return markAsRefunded(returnRequest.getId());
        }

        return toDTO(returnRequest);
    }

    @Override
    public TrackingStatus getTrackingStatus(Long returnId, String email) {
        ReturnRequest returnRequest = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnRequest", "id", returnId));

        assertOwnership(returnRequest, email);

        if (returnRequest.getTrackingNumber() == null) {
            return new TrackingStatus("NO_TRACKING", "No tracking number provided", LocalDateTime.now());
        }

        return courierTrackingService.track(returnRequest.getCarrierName(), returnRequest.getTrackingNumber());
    }

    private void updateOrderStatusAndNotify(Long orderId, String status, String notificationMessage) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));
        order.setOrderStatus(status);
        orderRepository.save(order);
        notificationService.notifyUserOrderStatusChanged(order.getId(), order.getEmail(), notificationMessage);
    }

    private Double getOrderTotal(Long orderId) {
        return orderRepository.findById(orderId)
                .map(Order::getTotalAmount)
                .orElse(0.0);
    }

    private ReturnRequestDTO toDTO(ReturnRequest r) {
        return toDTO(r, getOrderTotal(r.getOrderId()));
    }

    private ReturnRequestDTO toDTO(ReturnRequest r, Double orderTotal) {
        ReturnRequestDTO dto = new ReturnRequestDTO();
        dto.setId(r.getId());
        dto.setOrderId(r.getOrderId());
        dto.setUserEmail(r.getUserEmail());
        dto.setReason(r.getReason());
        dto.setStatus(r.getStatus());
        dto.setRequestedAt(r.getRequestedAt());
        dto.setProcessedAt(r.getProcessedAt());
        dto.setShippedBackAt(r.getShippedBackAt());
        dto.setAdminNote(r.getAdminNote());
        dto.setTrackingNumber(r.getTrackingNumber());
        dto.setCarrierName(r.getCarrierName());
        dto.setTrackingStatus(r.getTrackingStatus());
        dto.setLastTrackedAt(r.getLastTrackedAt());
        dto.setRefundAmount(r.getRefundAmount() != null ? r.getRefundAmount() : orderTotal);
        return dto;
    }

    private void assertOwnership(ReturnRequest returnRequest, String email) {
        if (!returnRequest.getUserEmail().equals(email)) {
            throw new APIException("You can only manage your own return requests");
        }
    }
}
