package com.ecommerce.project.service;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.ReturnRequest;
import com.ecommerce.project.payload.ReturnRequestDTO;
import com.ecommerce.project.payload.TrackingStatus;
import com.ecommerce.project.repository.OrderRepository;
import com.ecommerce.project.repository.ReturnRequestRepository;
import com.ecommerce.project.service.impl.ReturnServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ReturnServiceImpl tests")
class ReturnServiceTest {

    @Mock
    private ReturnRequestRepository returnRequestRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private CourierTrackingService courierTrackingService;

    @InjectMocks
    private ReturnServiceImpl returnService;

    private Order order;
    private ReturnRequest returnRequest;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(1L);
        order.setEmail("user@test.com");
        order.setOrderStatus("Delivered");
        order.setTotalAmount(new BigDecimal("150.00"));

        returnRequest = new ReturnRequest();
        returnRequest.setId(1L);
        returnRequest.setOrderId(1L);
        returnRequest.setUserEmail("user@test.com");
        returnRequest.setReason("Defective");
        returnRequest.setStatus("REQUESTED");
        returnRequest.setRequestedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("requestReturn creates a return request for delivered order")
    void requestReturn_success() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(returnRequestRepository.existsByOrderIdAndStatus(1L, "REQUESTED")).thenReturn(false);
        when(returnRequestRepository.existsByOrderIdAndStatus(1L, "APPROVED")).thenReturn(false);
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> {
            ReturnRequest r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        ReturnRequestDTO result = returnService.requestReturn(1L, "user@test.com", "Defective");

        assertNotNull(result);
        assertEquals(1L, result.getOrderId());
        assertEquals("user@test.com", result.getUserEmail());
        assertEquals("REQUESTED", result.getStatus());
        assertEquals(new BigDecimal("150.00"), result.getRefundAmount());
        assertEquals("Return Requested", order.getOrderStatus());
        verify(returnRequestRepository).save(any(ReturnRequest.class));
        verify(orderRepository).save(order);
        verify(notificationService).notifyAdminNewOrder(1L, "user@test.com", new BigDecimal("150.00"));
    }

    @Test
    @DisplayName("requestReturn throws when order not found")
    void requestReturn_orderNotFound_throws() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> returnService.requestReturn(99L, "user@test.com", "Defective"));
    }

    @Test
    @DisplayName("requestReturn throws when order is not delivered")
    void requestReturn_notDelivered_throws() {
        order.setOrderStatus("Shipped");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        APIException ex = assertThrows(APIException.class,
                () -> returnService.requestReturn(1L, "user@test.com", "Defective"));
        assertTrue(ex.getMessage().contains("only be requested for delivered"));
    }

    @Test
    @DisplayName("requestReturn throws when email does not match order")
    void requestReturn_wrongEmail_throws() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        APIException ex = assertThrows(APIException.class,
                () -> returnService.requestReturn(1L, "other@test.com", "Defective"));
        assertTrue(ex.getMessage().contains("your own orders"));
    }

    @Test
    @DisplayName("requestReturn throws when pending request already exists")
    void requestReturn_pendingExists_throws() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(returnRequestRepository.existsByOrderIdAndStatus(1L, "REQUESTED")).thenReturn(true);

        APIException ex = assertThrows(APIException.class,
                () -> returnService.requestReturn(1L, "user@test.com", "Defective"));
        assertTrue(ex.getMessage().contains("pending return request"));
    }

    @Test
    @DisplayName("approveReturn updates status and order")
    void approveReturn_success() {
        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(returnRequest));
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenReturn(returnRequest);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        ReturnRequestDTO result = returnService.approveReturn(1L, "Approved by admin");

        assertEquals("APPROVED", result.getStatus());
        assertEquals("Approved by admin", result.getAdminNote());
        assertNotNull(result.getProcessedAt());
        assertEquals("Returned", order.getOrderStatus());
        verify(notificationService).notifyUserOrderStatusChanged(1L, "user@test.com", "Return Approved");
    }

    @Test
    @DisplayName("approveReturn throws when not in REQUESTED status")
    void approveReturn_notRequested_throws() {
        returnRequest.setStatus("APPROVED");
        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(returnRequest));

        APIException ex = assertThrows(APIException.class,
                () -> returnService.approveReturn(1L, "note"));
        assertTrue(ex.getMessage().contains("Only requested"));
    }

    @Test
    @DisplayName("markAsRefunded updates status to REFUNDED")
    void markAsRefunded_success() {
        returnRequest.setStatus("SHIPPED_BACK");
        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(returnRequest));
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenReturn(returnRequest);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        ReturnRequestDTO result = returnService.markAsRefunded(1L);

        assertEquals("REFUNDED", result.getStatus());
        assertEquals("Refunded", order.getOrderStatus());
        verify(notificationService).notifyUserOrderStatusChanged(1L, "user@test.com", "Refunded");
    }

    @Test
    @DisplayName("markAsRefunded throws when status is not approved or shipped-back")
    void markAsRefunded_invalidStatus_throws() {
        returnRequest.setStatus("REQUESTED");
        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(returnRequest));

        APIException ex = assertThrows(APIException.class,
                () -> returnService.markAsRefunded(1L));
        assertTrue(ex.getMessage().contains("Only approved or shipped-back"));
    }

    @Test
    @DisplayName("rejectReturn updates status to REJECTED")
    void rejectReturn_success() {
        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(returnRequest));
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenReturn(returnRequest);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        ReturnRequestDTO result = returnService.rejectReturn(1L, "Rejected");

        assertEquals("REJECTED", result.getStatus());
        assertEquals("Delivered", order.getOrderStatus());
    }

    @Test
    @DisplayName("provideTracking sets tracking and status to SHIPPED_BACK")
    void provideTracking_success() {
        returnRequest.setStatus("APPROVED");
        TrackingStatus tracking = new TrackingStatus("IN_TRANSIT", "In transit", LocalDateTime.now());

        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(returnRequest));
        when(courierTrackingService.track("DHL", "TRACK123")).thenReturn(tracking);
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenReturn(returnRequest);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        ReturnRequestDTO result = returnService.provideTracking(1L, "user@test.com", "DHL", "TRACK123");

        assertEquals("SHIPPED_BACK", result.getStatus());
        assertEquals("TRACK123", result.getTrackingNumber());
        assertEquals("DHL", result.getCarrierName());
    }

    @Test
    @DisplayName("getAllReturnRequests returns paged results")
    void getAllReturnRequests_success() {
        Page<ReturnRequest> page = new PageImpl<>(List.of(returnRequest),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "requestedAt")), 1);

        when(returnRequestRepository.findAllByOrderByRequestedAtDesc(any(PageRequest.class))).thenReturn(page);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        Page<ReturnRequestDTO> result = returnService.getAllReturnRequests(0, 10);

        assertEquals(1, result.getTotalElements());
    }
}
