package com.ecommerce.project.controller;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.service.support.SupportOrderService;
import com.ecommerce.project.service.support.SupportPilotApi.OrderView;
import com.ecommerce.project.service.support.SupportPilotApi.RefundIn;
import com.ecommerce.project.service.support.SupportPilotApi.RefundOut;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The shop as SupportPilot's "order system": the exact contract of its
 * {@code orders_mock.py}, so SupportPilot only changes {@code ORDERS_BASE_URL}.
 *
 * <p>Service-to-service only: guarded by the {@code /internal/support/**} filter
 * chain (an API key, no cookies, no CSRF) and never routed by nginx, which
 * proxies {@code /api/} alone. Hidden from the public OpenAPI document.
 *
 * <p>Errors are SupportPilot-shaped ({@code {"detail": ...}}): 404 is "no such
 * order for this customer", 409 is a business refusal the agent should explain
 * — never retried by its client, and never tripping its circuit breaker.
 */
@Hidden
@RestController
@RequestMapping("/internal/support")
@RequiredArgsConstructor
public class SupportInternalController {

    private final SupportOrderService supportOrderService;

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderView> order(@PathVariable String orderId) {
        return supportOrderService.findOrder(orderId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping("/orders/{orderId}/refunds")
    public ResponseEntity<RefundOut> refund(@PathVariable String orderId,
                                            @RequestBody RefundIn body,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey,
                                            @RequestHeader("X-Customer-Id") String customerId,
                                            @RequestHeader(value = "X-Conversation-Id", required = false) String conversationId,
                                            @RequestHeader(value = "X-Approved-By", required = false) String approvedBy) {
        RefundOut out = supportOrderService.createApprovedReturn(orderId, customerId,
                body == null ? null : body.reason(), idempotencyKey, conversationId,
                approvedBy == null || approvedBy.isBlank() ? "support agent" : approvedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "no such order"));
    }

    @ExceptionHandler(APIException.class)
    public ResponseEntity<Map<String, String>> refused(APIException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("detail", e.getMessage()));
    }
}
