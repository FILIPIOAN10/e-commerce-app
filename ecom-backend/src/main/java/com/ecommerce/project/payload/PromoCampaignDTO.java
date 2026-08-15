package com.ecommerce.project.payload;

import lombok.Data;

import java.util.List;

@Data
public class PromoCampaignDTO {
    private Long id;
    private String name;
    private Double discountPercent;
    private String startTime;
    private String endTime;
    private Boolean active;
    private List<Long> productIds;
}
