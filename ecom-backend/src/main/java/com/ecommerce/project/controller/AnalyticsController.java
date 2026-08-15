package com.ecommerce.project.controller;


import com.ecommerce.project.payload.AnalyticsResponse;
import com.ecommerce.project.payload.ChartDataResponse;
import com.ecommerce.project.service.AnalyticsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AnalyticsController {

    private AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/admin/app/analytics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AnalyticsResponse> getAnalytics(){
        AnalyticsResponse response = analyticsService.getAnalyticsData();
        return  new ResponseEntity<AnalyticsResponse>(response, HttpStatus.OK);
    }

    @Tag(name = "Analytics")
    @GetMapping("/admin/app/analytics/sales")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChartDataResponse> getMonthlySales() {
        return new ResponseEntity<>(analyticsService.getMonthlySalesData(), HttpStatus.OK);
    }

    @Tag(name = "Analytics")
    @GetMapping("/admin/app/analytics/top-products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChartDataResponse> getTopProducts() {
        return new ResponseEntity<>(analyticsService.getTopProductsData(), HttpStatus.OK);
    }

    @Tag(name = "Analytics")
    @GetMapping("/admin/app/analytics/order-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChartDataResponse> getOrderStatus() {
        return new ResponseEntity<>(analyticsService.getOrderStatusData(), HttpStatus.OK);
    }

    @Tag(name = "Analytics")
    @GetMapping("/admin/app/analytics/revenue-by-category")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChartDataResponse> getRevenueByCategory() {
        return new ResponseEntity<>(analyticsService.getRevenueByCategoryData(), HttpStatus.OK);
    }
}
