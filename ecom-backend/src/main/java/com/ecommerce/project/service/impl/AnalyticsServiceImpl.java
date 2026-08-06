package com.ecommerce.project.service.impl;

import com.ecommerce.project.payload.AnalyticsResponse;
import com.ecommerce.project.payload.ChartDataResponse;
import com.ecommerce.project.repository.OrderItemRepository;
import com.ecommerce.project.repository.OrderRepository;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;



    @Override
    public AnalyticsResponse getAnalyticsData() {
        AnalyticsResponse response = new AnalyticsResponse();

        long producCount = productRepository.count();
        long totalOrders = orderRepository.count();
        Double totalRevenue = orderRepository.getTotalRevenue();

        response.setProductCount(String.valueOf( producCount));
        response.setTotalOrders(String.valueOf( totalOrders));
        response.setTotalRevenue(String.valueOf( totalRevenue !=null ? totalRevenue : 0));
         return response;
    }

    @Override
    public ChartDataResponse getMonthlySalesData() {
        List<Object[]> results = orderRepository.getMonthlyRevenue();
        List<ChartDataResponse.DataPoint> dataPoints = new ArrayList<>();

        for (Object[] row : results) {
            String month = (String) row[0];
            double revenue = ((Number) row[1]).doubleValue();
            dataPoints.add(new ChartDataResponse.DataPoint(month, revenue));
        }

        return new ChartDataResponse(dataPoints);
    }

    @Override
    public ChartDataResponse getTopProductsData() {
        List<Object[]> results = orderItemRepository.getTop10BestSellingProducts();
        List<ChartDataResponse.DataPoint> dataPoints = new ArrayList<>();

        for (Object[] row : results) {
            String productName = (String) row[0];
            long totalSold = ((Number) row[1]).longValue();
            dataPoints.add(new ChartDataResponse.DataPoint(productName, totalSold));
        }

        return new ChartDataResponse(dataPoints);
    }

    @Override
    public ChartDataResponse getOrderStatusData() {
        List<Object[]> results = orderRepository.getOrderCountByStatus();
        List<ChartDataResponse.DataPoint> dataPoints = new ArrayList<>();

        for (Object[] row : results) {
            String status = (String) row[0];
            long count = ((Number) row[1]).longValue();
            dataPoints.add(new ChartDataResponse.DataPoint(status, count));
        }

        return new ChartDataResponse(dataPoints);
    }
}
