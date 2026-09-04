package com.ecommerce.project.controller;

import com.ecommerce.project.payload.PaginationParams;
import com.ecommerce.project.payload.QuestionResponse;
import com.ecommerce.project.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.ecommerce.project.payload.request.QuestionRequest;
import com.ecommerce.project.payload.request.AnswerRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products/{productId}/questions")
@RequiredArgsConstructor
public class QuestionController extends BaseController {

    private final QuestionService questionService;

    @GetMapping
    public ResponseEntity<QuestionResponse> getProductQuestions(
            @PathVariable Long productId,
            @ModelAttribute PaginationParams params) {
        return ok(questionService.getProductQuestions(productId, params.getPageNumber(), params.getPageSize(), params.getSortBy(), params.getSortOrder()));
    }

    @PostMapping
    public ResponseEntity<?> askQuestion(
            @PathVariable Long productId,
            @Valid @RequestBody QuestionRequest body) {
        String question = body.question();
        String message = questionService.askQuestion(productId, question);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/{questionId}/answer")
    public ResponseEntity<?> answerQuestion(
            @PathVariable Long productId,
            @PathVariable Long questionId,
            @Valid @RequestBody AnswerRequest body) {
        String answer = body.answer();
        String message = questionService.answerQuestion(questionId, answer);
        return ResponseEntity.ok(Map.of("message", message));
    }
}
