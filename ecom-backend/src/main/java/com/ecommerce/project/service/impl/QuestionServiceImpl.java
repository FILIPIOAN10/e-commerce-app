package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.ProductQuestion;
import com.ecommerce.project.model.Role;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.QuestionDTO;
import com.ecommerce.project.payload.QuestionResponse;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.repository.QuestionRepository;
import com.ecommerce.project.service.QuestionService;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import com.ecommerce.project.util.SortWhitelist;

@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private final QuestionRepository questionRepository;
    private final ProductRepository productRepository;
    private final AuthUtil authUtil;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    @Transactional
    public String askQuestion(Long productId, String question) {
        User user = authUtil.loggedInUser();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
        if (question == null || question.trim().isEmpty()) {
            throw new APIException("Question cannot be empty");
        }
        ProductQuestion q = ProductQuestion.builder()
                .product(product)
                .user(user)
                .question(question.trim())
                .build();
        questionRepository.save(q);
        return "Question asked successfully";
    }

    @Override
    @Transactional
    public String answerQuestion(Long questionId, String answer) {
        User user = authUtil.loggedInUser();
        ProductQuestion q = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question", "questionId", questionId));
        if (answer == null || answer.trim().isEmpty()) {
            throw new APIException("Answer cannot be empty");
        }
        if (!canAnswer(user, q)) {
            throw new APIException("Not authorized to answer this question");
        }
        q.setAnswer(answer.trim());
        q.setAnsweredAt(LocalDateTime.now());
        questionRepository.save(q);
        return "Answer added successfully";
    }

    private boolean canAnswer(User user, ProductQuestion q) {
        boolean admin = user.getRoles().stream().map(Role::getRoleName).anyMatch(com.ecommerce.project.model.AppRole.ROLE_ADMIN::equals);
        boolean seller = q.getProduct().getUser() != null
                && q.getProduct().getUser().getUserId().equals(user.getUserId());
        return admin || seller;
    }

    @Override
    public QuestionResponse getProductQuestions(Long productId, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder,
                "createdAt", SortWhitelist.QUESTION);
        Page<ProductQuestion> page = questionRepository.findByProduct(product, pageDetails);
        List<QuestionDTO> dtos = page.getContent().stream().map(this::mapToDTO).toList();
        QuestionResponse response = new QuestionResponse();
        response.setContent(dtos);
        response.setTotalQuestions(questionRepository.countByProduct(product));
        return response;
    }

    private QuestionDTO mapToDTO(ProductQuestion q) {
        QuestionDTO dto = new QuestionDTO();
        dto.setQuestionId(q.getId());
        dto.setProductId(q.getProduct().getProductId());
        dto.setUsername(q.getUser().getUserName());
        dto.setQuestion(q.getQuestion());
        dto.setAnswer(q.getAnswer());
        dto.setCreatedAt(q.getCreatedAt().format(FORMATTER));
        dto.setAnsweredAt(q.getAnsweredAt() != null ? q.getAnsweredAt().format(FORMATTER) : null);
        return dto;
    }
}
