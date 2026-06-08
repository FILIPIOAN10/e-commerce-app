package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.payload.CategoryDTO;
import com.ecommerce.project.payload.CategoryResponse;
import com.ecommerce.project.repository.CategoryRepository;
import com.ecommerce.project.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    private final ModelMapper modelMapper;



    @Override
    @Cacheable(
            value = "publicCategories",
            key = " #pageNumber + '/' + #pageSize + '/' + #sortBy + '/' + #sortOrder "
    )
    public CategoryResponse getAllCategories(Integer pageNumber, Integer pageSize,String sortBy,String sortOrder) {

        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize,sortByAndOrder);

        // how you can get the page from DB
        Page<Category> categoryPage = categoryRepository.findAll(pageDetails);

        //Fetch the categories
        List<Category> categories = categoryPage.getContent();


        // mapping the category to CateogryDTO class
        // for every category in the list we are mapping that category to categoryDTO
        List<CategoryDTO> categoryDTOS = categories.stream()
                .map(category -> modelMapper.map(category, CategoryDTO.class))
                .toList();

        CategoryResponse categoryResponse = new CategoryResponse();
        categoryResponse.setContent(categoryDTOS);
        categoryResponse.setPageNumber(categoryPage.getNumber());
        categoryResponse.setPageSize(categoryPage.getSize());
        categoryResponse.setTotalElements(categoryPage.getTotalElements());
        categoryResponse.setTotalPages(categoryPage.getTotalPages());
        categoryResponse.setLastPage(categoryPage.isLast());
        return categoryResponse;
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "publicCategories",allEntries = true),
            @CacheEvict(value = "publicProducts",allEntries = true),
            @CacheEvict(value = "categoryProducts",allEntries = true),
            @CacheEvict(value = "productSearch",allEntries = true)
    })
    public CategoryDTO createCategory(CategoryDTO categoryDTO) {

        Category category = modelMapper.map(categoryDTO, Category.class);
        //1. Check if a category with the given name already exists
        Category categoryFromDb = categoryRepository.findByCategoryName(category.getCategoryName());
        if (categoryFromDb != null) {
            throw new APIException("Category with the name " + categoryDTO.getCategoryName() + " already exists !!!");
        }

        Category savedCategory = categoryRepository.save(category);

        return modelMapper.map(savedCategory, CategoryDTO.class);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "publicCategories",allEntries = true),
            @CacheEvict(value = "publicProducts",allEntries = true),
            @CacheEvict(value = "categoryProducts",allEntries = true),
            @CacheEvict(value = "productSearch",allEntries = true)
    })
    public CategoryDTO deleteCategory(Long categoryId) {

        Category category = categoryRepository.findById(categoryId).orElseThrow(() -> new ResourceNotFoundException("Category", "categoryId", categoryId));

        categoryRepository.delete(category);
        return modelMapper.map(category, CategoryDTO.class);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "publicCategories",allEntries = true),
            @CacheEvict(value = "publicProducts",allEntries = true),
            @CacheEvict(value = "categoryProducts",allEntries = true),
            @CacheEvict(value = "productSearch",allEntries = true)
    })
    public CategoryDTO updateCategory(CategoryDTO categoryDTO, Long categoryId) {

        Category savedCategory = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "categoryId", categoryId));
        Category category = modelMapper.map(categoryDTO, Category.class);

        category.setCategoryId(categoryId);

        savedCategory = categoryRepository.save(category);
        return modelMapper.map(savedCategory, CategoryDTO.class);
    }
}
