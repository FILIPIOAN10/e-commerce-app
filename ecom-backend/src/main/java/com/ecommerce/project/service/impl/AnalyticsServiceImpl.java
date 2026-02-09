package com.ecommerce.project.service.impl;

import com.ecommerce.project.payload.AnalyticsResponse;
import com.ecommerce.project.repository.OrderRepository;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.service.AnalyticsService;
import org.springframework.stereotype.Service;


@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private ProductRepository productRepository;
    private OrderRepository orderRepository;

    public AnalyticsServiceImpl(ProductRepository productRepository, OrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

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
}
