package com.ecommerce.project.payload;

import lombok.Data;

import java.util.List;

@Data
public class PromoCampaignResponse {
    private List<PromoCampaignDTO> content;
    private Long totalElements;
}
