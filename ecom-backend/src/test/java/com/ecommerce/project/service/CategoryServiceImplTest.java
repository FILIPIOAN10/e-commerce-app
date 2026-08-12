package com.ecommerce.project.service;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.payload.CategoryDTO;
import com.ecommerce.project.payload.CategoryResponse;
import com.ecommerce.project.repository.CategoryRepository;
import com.ecommerce.project.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CategoryServiceImpl tests")
class CategoryServiceImplTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private ModelMapper modelMapper;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private Category category;
    private CategoryDTO categoryDTO;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setCategoryId(1L);
        category.setCategoryName("Electronics");

        categoryDTO = new CategoryDTO();
        categoryDTO.setCategoryId(1L);
        categoryDTO.setCategoryName("Electronics");
    }

    @Test
    @DisplayName("getAllCategories returns paginated response")
    void getAllCategories_returnsResponse() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("categoryName").ascending());
        Page<Category> page = new PageImpl<>(List.of(category), pageable, 1);

        when(categoryRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(modelMapper.map(category, CategoryDTO.class)).thenReturn(categoryDTO);

        CategoryResponse response = categoryService.getAllCategories(0, 10, "categoryName", "asc");

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(0, response.getPageNumber());
        assertEquals(10, response.getPageSize());
        assertEquals(1L, response.getTotalElements());
        assertEquals(1, response.getTotalPages());
        assertTrue(response.getLastPage());
        verify(categoryRepository).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("createCategory saves and returns DTO")
    void createCategory_success() {
        when(modelMapper.map(categoryDTO, Category.class)).thenReturn(category);
        when(categoryRepository.findByCategoryName("Electronics")).thenReturn(null);
        when(categoryRepository.save(category)).thenReturn(category);
        when(modelMapper.map(category, CategoryDTO.class)).thenReturn(categoryDTO);

        CategoryDTO result = categoryService.createCategory(categoryDTO);

        assertNotNull(result);
        assertEquals("Electronics", result.getCategoryName());
        verify(categoryRepository).save(category);
    }

    @Test
    @DisplayName("createCategory throws APIException when name already exists")
    void createCategory_duplicateName_throws() {
        when(modelMapper.map(categoryDTO, Category.class)).thenReturn(category);
        when(categoryRepository.findByCategoryName("Electronics")).thenReturn(category);

        APIException exception = assertThrows(APIException.class,
                () -> categoryService.createCategory(categoryDTO));
        assertTrue(exception.getMessage().contains("already exists"));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteCategory removes category and returns DTO")
    void deleteCategory_success() {
        when(categoryRepository.findById(1L)).thenReturn(java.util.Optional.of(category));
        doNothing().when(categoryRepository).delete(category);
        when(modelMapper.map(category, CategoryDTO.class)).thenReturn(categoryDTO);

        CategoryDTO result = categoryService.deleteCategory(1L);

        assertNotNull(result);
        verify(categoryRepository).delete(category);
    }

    @Test
    @DisplayName("deleteCategory throws ResourceNotFoundException for missing id")
    void deleteCategory_notFound_throws() {
        when(categoryRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> categoryService.deleteCategory(99L));
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("updateCategory saves updated category")
    void updateCategory_success() {
        Category updatedCategory = new Category();
        updatedCategory.setCategoryId(1L);
        updatedCategory.setCategoryName("Home Electronics");

        CategoryDTO updateDTO = new CategoryDTO();
        updateDTO.setCategoryName("Home Electronics");

        when(categoryRepository.findById(1L)).thenReturn(java.util.Optional.of(category));
        when(modelMapper.map(updateDTO, Category.class)).thenReturn(updatedCategory);
        when(categoryRepository.save(updatedCategory)).thenReturn(updatedCategory);
        when(modelMapper.map(updatedCategory, CategoryDTO.class)).thenReturn(updateDTO);

        CategoryDTO result = categoryService.updateCategory(updateDTO, 1L);

        assertNotNull(result);
        assertEquals("Home Electronics", result.getCategoryName());
        verify(categoryRepository).save(updatedCategory);
    }

    @Test
    @DisplayName("updateCategory throws ResourceNotFoundException for missing id")
    void updateCategory_notFound_throws() {
        when(categoryRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> categoryService.updateCategory(categoryDTO, 99L));
        verify(categoryRepository, never()).save(any());
    }
}
