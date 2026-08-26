package com.ecommerce.project.controller;

import com.ecommerce.project.payload.*;
import com.ecommerce.project.service.*;
import com.ecommerce.project.util.PaginationUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class AdminGraphQLController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final OrderService orderService;
    private final UserManagementService userManagementService;
    private final CouponService couponService;
    private final PromoCampaignService promoCampaignService;
    private final ReturnService returnService;

    public AdminGraphQLController(ProductService productService,
                                  CategoryService categoryService,
                                  OrderService orderService,
                                  UserManagementService userManagementService,
                                  CouponService couponService,
                                  PromoCampaignService promoCampaignService,
                                  ReturnService returnService) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.orderService = orderService;
        this.userManagementService = userManagementService;
        this.couponService = couponService;
        this.promoCampaignService = promoCampaignService;
        this.returnService = returnService;
    }

    @QueryMapping
    public ProductResponse adminProducts(@Argument Integer page,
                                         @Argument Integer size,
                                         @Argument String sortBy,
                                         @Argument String sortOrder) {
        return productService.getAllProductsForAdmin(page, size, sortBy, sortOrder);
    }

    @QueryMapping
    public ProductDTO adminProduct(@Argument Long productId) {
        return productService.getProductById(productId);
    }

    @QueryMapping
    public CategoryResponse adminCategories(@Argument Integer page,
                                           @Argument Integer size,
                                           @Argument String sortBy,
                                           @Argument String sortOrder) {
        return categoryService.getAllCategories(page, size, sortBy, sortOrder);
    }

    @QueryMapping
    public OrderResponse adminOrders(@Argument Integer page,
                                     @Argument Integer size,
                                     @Argument String sortBy,
                                     @Argument String sortOrder) {
        return orderService.getAllOrders(page, size, sortBy, sortOrder);
    }

    @QueryMapping
    public UserResponse adminUsers(@Argument Integer page,
                                   @Argument Integer size,
                                   @Argument String sortBy,
                                   @Argument String sortOrder) {
        Pageable pageable = PaginationUtil.buildPageable(page, size, sortBy, sortOrder);
        return userManagementService.getAllUsers(pageable);
    }

    @QueryMapping
    public List<CouponDTO> adminCoupons() {
        return couponService.getAllCoupons();
    }

    @QueryMapping
    public PromoCampaignResponse adminPromoCampaigns(@Argument Integer page,
                                                    @Argument Integer size) {
        return promoCampaignService.getCampaigns(page, size);
    }

    @QueryMapping
    public ReturnRequestPage adminReturnRequests(@Argument Integer page,
                                                 @Argument Integer size) {
        Page<ReturnRequestDTO> pageData = returnService.getAllReturnRequests(page, size);
        return new ReturnRequestPage(
                pageData.getContent(),
                pageData.getNumber(),
                pageData.getSize(),
                (double) pageData.getTotalElements(),
                pageData.getTotalPages(),
                pageData.isLast()
        );
    }

    public record ReturnRequestPage(
            List<ReturnRequestDTO> content,
            int pageNumber,
            int pageSize,
            double totalElements,
            int totalPages,
            boolean lastPage
    ) {
    }
}
