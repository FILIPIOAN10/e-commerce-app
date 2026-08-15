package com.ecommerce.project.payload;

import lombok.Data;

import java.util.List;

@Data
public class QuestionResponse {
    private List<QuestionDTO> content;
    private Long totalQuestions;
}
