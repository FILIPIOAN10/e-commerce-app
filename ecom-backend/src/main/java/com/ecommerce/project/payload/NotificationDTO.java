package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private Long id;
    private String recipientEmail;
    private String title;
    private String message;
    private String type;
    private Long referenceId;
    private boolean read;
    private LocalDateTime createdAt;
}
