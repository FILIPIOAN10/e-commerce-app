package com.ecommerce.project.service;

import com.ecommerce.project.payload.AnalyticsResponse;
import org.springframework.data.jpa.repository.Query;

public interface AnalyticsService {



    public AnalyticsResponse getAnalyticsData();
}
