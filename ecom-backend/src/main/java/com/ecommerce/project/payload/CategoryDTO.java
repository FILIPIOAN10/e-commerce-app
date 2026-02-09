package com.ecommerce.project.payload;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// requst object
// used to encapsulate and transfer data related to categories from client to server
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {


    @Schema(description = "Category ID",example = "101")
    private Long categoryId;

    @Schema(description = "Category name for category you wish to create")
    private String categoryName;
}
