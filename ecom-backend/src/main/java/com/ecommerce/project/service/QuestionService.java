package com.ecommerce.project.service;

import com.ecommerce.project.payload.QuestionResponse;

public interface QuestionService {

    String askQuestion(Long productId, String question);

    String answerQuestion(Long questionId, String answer);

    QuestionResponse getProductQuestions(Long productId, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);
}
