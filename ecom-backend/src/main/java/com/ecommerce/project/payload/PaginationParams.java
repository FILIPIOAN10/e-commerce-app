package com.ecommerce.project.payload;

import com.ecommerce.project.config.AppConstants;
import lombok.Data;

@Data
public class PaginationParams {
    private Integer pageNumber = Integer.parseInt(AppConstants.PAGE_NUMBER);
    private Integer pageSize = Integer.parseInt(AppConstants.PAGE_SIZE);
    private String sortBy;
    private String sortOrder = AppConstants.SORT_DIR;
}
