package com.ecommerce.project.service;

import com.ecommerce.project.payload.AnalyticsResponse;
import com.ecommerce.project.payload.ChartDataResponse;
import org.springframework.data.jpa.repository.Query;

public interface AnalyticsService {

    public AnalyticsResponse getAnalyticsData();

    ChartDataResponse getMonthlySalesData();

    ChartDataResponse getTopProductsData();

    ChartDataResponse getOrderStatusData();

    ChartDataResponse getRevenueByCategoryData();
}
