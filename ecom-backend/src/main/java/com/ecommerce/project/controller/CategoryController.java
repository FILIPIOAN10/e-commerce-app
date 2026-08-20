package com.ecommerce.project.controller;


import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.payload.CategoryDTO;
import com.ecommerce.project.payload.CategoryResponse;
import com.ecommerce.project.payload.PaginationParams;
import com.ecommerce.project.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api")
// Controller pentru operații CRUD pe categorii (public și admin)
public class CategoryController extends BaseController {

    private CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }
    /**
     * Returnează toate categoriile cu paginare și sortare.
     */
    // listare categorii cu paginare
    @Tag(name = "Category")
    @GetMapping("/public/categories")
    public ResponseEntity<CategoryResponse> getAllCategories(@ModelAttribute PaginationParams params) {
        CategoryResponse categories = categoryService.getAllCategories(params.getPageNumber(), params.getPageSize(), params.getSortBy(), params.getSortOrder());
        return ok(categories);
    }

    /**
     * Creează o categorie nouă.
     */
    @Tag(name = "Category")
    @Operation(summary = "Create category",description = "Api for create a new category")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Category is created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid Input", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content),
    })

    @Tag(name = "Category")
    @PostMapping("/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    // creare categories
    public ResponseEntity<CategoryDTO> createCategory(
            @Valid @RequestBody CategoryDTO categoryDTO) {
        CategoryDTO savedCategoryDTO = categoryService.createCategory(categoryDTO);
        return created(savedCategoryDTO);

    }


    /**
     * Șterge o categorie după ID (numai pentru admin).
     */
    // ștergere (doar admin)
    @Tag(name = "Category")
    @DeleteMapping("/admin/categories/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryDTO> deleteCategory(
            @Parameter(description = "Id of the Category that you wish to delete")
            @PathVariable Long categoryId) {

        CategoryDTO deleteCategory = categoryService.deleteCategory(categoryId);
        return ok(deleteCategory);

    }

    /**
     * Actualizează o categorie existentă.
     */
    // update
    @Tag(name = "Category")
    @PutMapping("/admin/categories/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryDTO> updateCategory(@Valid @RequestBody CategoryDTO categoryDTO,
                                                 @PathVariable Long categoryId) {

        CategoryDTO savedCategoryDTO = categoryService.updateCategory(categoryDTO, categoryId);
        return ok(savedCategoryDTO);

    }
}

