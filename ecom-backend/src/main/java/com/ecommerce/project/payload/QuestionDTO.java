package com.ecommerce.project.payload;

import lombok.Data;

@Data
public class QuestionDTO {
    private Long questionId;
    private Long productId;
    private String username;
    private String question;
    private String answer;
    private String createdAt;
    private String answeredAt;
}
