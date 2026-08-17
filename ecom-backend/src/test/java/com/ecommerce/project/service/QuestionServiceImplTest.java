package com.ecommerce.project.service;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.ProductQuestion;
import com.ecommerce.project.model.Role;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.QuestionResponse;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.repository.QuestionRepository;
import com.ecommerce.project.service.impl.QuestionServiceImpl;
import com.ecommerce.project.util.AuthUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QuestionServiceImpl tests")
class QuestionServiceImplTest {

    @Mock private QuestionRepository questionRepository;
    @Mock private ProductRepository productRepository;
    @Mock private AuthUtil authUtil;

    @InjectMocks
    private QuestionServiceImpl questionService;

    private User user;
    private User admin;
    private User seller;
    private Product product;
    private ProductQuestion question;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setProductId(1L);
        product.setProductName("Wireless Headphones");

        user = new User();
        user.setUserId(1L);
        user.setUserName("user1");
        user.setRoles(Set.of(new Role(AppRole.ROLE_USER)));

        admin = new User();
        admin.setUserId(2L);
        admin.setUserName("admin");
        admin.setRoles(Set.of(new Role(AppRole.ROLE_ADMIN)));

        seller = new User();
        seller.setUserId(3L);
        seller.setUserName("seller");
        product.setUser(seller);
        seller.setRoles(Set.of(new Role(AppRole.ROLE_SELLER)));

        question = ProductQuestion.builder()
                .id(1L)
                .product(product)
                .user(user)
                .question("Is it waterproof?")
                .createdAt(LocalDateTime.of(2026, 8, 12, 10, 0))
                .build();
    }

    @Test
    @DisplayName("askQuestion saves a valid question for a product")
    void askQuestion_success() {
        when(authUtil.loggedInUser()).thenReturn(user);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        String result = questionService.askQuestion(1L, "Is it waterproof?");

        assertEquals("Question asked successfully", result);
        verify(questionRepository).save(any(ProductQuestion.class));
    }

    @Test
    @DisplayName("askQuestion throws when product not found")
    void askQuestion_productNotFound_throws() {
        when(authUtil.loggedInUser()).thenReturn(user);
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> questionService.askQuestion(99L, "Is it waterproof?"));
        verify(questionRepository, never()).save(any());
    }

    @Test
    @DisplayName("askQuestion throws when question is empty")
    void askQuestion_emptyQuestion_throws() {
        when(authUtil.loggedInUser()).thenReturn(user);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(APIException.class, () -> questionService.askQuestion(1L, "   "));
        assertThrows(APIException.class, () -> questionService.askQuestion(1L, null));
    }

    @Test
    @DisplayName("answerQuestion allows admin to answer")
    void answerQuestion_admin_success() {
        when(authUtil.loggedInUser()).thenReturn(admin);
        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));

        String result = questionService.answerQuestion(1L, "Yes, IPX5 rated");

        assertEquals("Answer added successfully", result);
        assertEquals("Yes, IPX5 rated", question.getAnswer());
        assertNotNull(question.getAnsweredAt());
        verify(questionRepository).save(question);
    }

    @Test
    @DisplayName("answerQuestion allows product owner to answer")
    void answerQuestion_seller_success() {
        when(authUtil.loggedInUser()).thenReturn(seller);
        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));

        String result = questionService.answerQuestion(1L, "Yes, the seller confirms this");

        assertEquals("Answer added successfully", result);
        verify(questionRepository).save(question);
    }

    @Test
    @DisplayName("answerQuestion throws when random user tries to answer")
    void answerQuestion_unauthorized_throws() {
        User randomUser = new User();
        randomUser.setUserId(4L);
        randomUser.setRoles(Set.of(new Role(AppRole.ROLE_USER)));

        when(authUtil.loggedInUser()).thenReturn(randomUser);
        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));

        APIException ex = assertThrows(APIException.class,
                () -> questionService.answerQuestion(1L, "My answer"));
        assertTrue(ex.getMessage().contains("Not authorized"));
    }

    @Test
    @DisplayName("answerQuestion throws when answer is empty")
    void answerQuestion_emptyAnswer_throws() {
        when(authUtil.loggedInUser()).thenReturn(admin);
        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));

        assertThrows(APIException.class, () -> questionService.answerQuestion(1L, ""));
    }

    @Test
    @DisplayName("answerQuestion throws when question not found")
    void answerQuestion_questionNotFound_throws() {
        when(authUtil.loggedInUser()).thenReturn(admin);
        when(questionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> questionService.answerQuestion(99L, "Answer"));
    }

    @Test
    @DisplayName("getProductQuestions returns paginated questions")
    void getProductQuestions_success() {
        Pageable pageable = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("createdAt").descending());
        Page<ProductQuestion> page = new PageImpl<>(List.of(question), pageable, 1);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(questionRepository.findByProduct(eq(product), any(Pageable.class))).thenReturn(page);
        when(questionRepository.countByProduct(product)).thenReturn(1L);

        QuestionResponse response = questionService.getProductQuestions(1L, 0, 10, "createdAt", "desc");

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("Is it waterproof?", response.getContent().get(0).getQuestion());
        assertEquals(1L, response.getTotalQuestions());
    }

    @Test
    @DisplayName("getProductQuestions throws when product not found")
    void getProductQuestions_productNotFound_throws() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> questionService.getProductQuestions(99L, 0, 10, "createdAt", "desc"));
    }
}
