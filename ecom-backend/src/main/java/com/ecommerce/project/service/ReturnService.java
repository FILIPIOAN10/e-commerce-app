package com.ecommerce.project.service;

import com.ecommerce.project.payload.ReturnRequestDTO;
import org.springframework.data.domain.Page;

public interface ReturnService {

    ReturnRequestDTO requestReturn(Long orderId, String email, String reason);

    Page<ReturnRequestDTO> getAllReturnRequests(int page, int size);

    Page<ReturnRequestDTO> getMyReturnRequests(String email, int page, int size);

    ReturnRequestDTO approveReturn(Long returnId, String adminNote);

    ReturnRequestDTO rejectReturn(Long returnId, String adminNote);

    ReturnRequestDTO markAsRefunded(Long returnId);
}
