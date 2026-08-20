package com.ecommerce.project.controller;

import com.ecommerce.project.payload.PaginationParams;
import com.ecommerce.project.payload.QuestionResponse;
import com.ecommerce.project.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
            @RequestBody Map<String, Object> body) {
        String question = (String) body.get("question");
        String message = questionService.askQuestion(productId, question);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/{questionId}/answer")
    public ResponseEntity<?> answerQuestion(
            @PathVariable Long productId,
            @PathVariable Long questionId,
            @RequestBody Map<String, Object> body) {
        String answer = (String) body.get("answer");
        String message = questionService.answerQuestion(questionId, answer);
        return ResponseEntity.ok(Map.of("message", message));
    }
}
