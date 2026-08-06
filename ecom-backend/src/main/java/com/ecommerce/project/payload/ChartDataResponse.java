package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChartDataResponse {
    private List<DataPoint> data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPoint {
        private String label;
        private double value;
    }
}
