package com.ecommerce.project.controller;

import com.ecommerce.project.payload.QuestionResponse;
import com.ecommerce.project.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/public/products/{productId}/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    @GetMapping
    public ResponseEntity<QuestionResponse> getProductQuestions(
            @PathVariable Long productId,
            @RequestParam(name = "pageNumber", defaultValue = "0", required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = "10", required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = "createdAt", required = false) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = "desc", required = false) String sortOrder) {
        return ResponseEntity.ok(questionService.getProductQuestions(productId, pageNumber, pageSize, sortBy, sortOrder));
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
