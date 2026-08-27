package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.PromoCampaign;
import com.ecommerce.project.model.PromoCampaignProduct;
import com.ecommerce.project.payload.PromoCampaignDTO;
import com.ecommerce.project.payload.PromoCampaignResponse;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.repository.PromoCampaignProductRepository;
import com.ecommerce.project.repository.PromoCampaignRepository;
import com.ecommerce.project.service.PromoCampaignService;
import com.ecommerce.project.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromoCampaignServiceImpl implements PromoCampaignService {

    private final PromoCampaignRepository promoCampaignRepository;
    private final PromoCampaignProductRepository promoCampaignProductRepository;
    private final ProductRepository productRepository;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Override
    @Transactional
    public PromoCampaignDTO createCampaign(PromoCampaignDTO dto) {
        PromoCampaign campaign = mapFromDTO(dto);
        PromoCampaign saved = promoCampaignRepository.save(campaign);
        saveProducts(saved, dto.getProductIds());
        return mapToDTO(saved);
    }

    @Override
    @Transactional
    public PromoCampaignDTO updateCampaign(Long id, PromoCampaignDTO dto) {
        PromoCampaign campaign = promoCampaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PromoCampaign", "id", id));
        campaign.setName(dto.getName());
        campaign.setDiscountPercent(dto.getDiscountPercent());
        campaign.setStartTime(LocalDateTime.parse(dto.getStartTime(), FORMATTER));
        campaign.setEndTime(LocalDateTime.parse(dto.getEndTime(), FORMATTER));
        campaign.setActive(dto.getActive());
        PromoCampaign saved = promoCampaignRepository.save(campaign);
        promoCampaignProductRepository.deleteByPromoCampaignId(saved.getId());
        saveProducts(saved, dto.getProductIds());
        return mapToDTO(saved);
    }

    @Override
    @Transactional
    public void deleteCampaign(Long id) {
        PromoCampaign campaign = promoCampaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PromoCampaign", "id", id));
        promoCampaignProductRepository.deleteByPromoCampaignId(campaign.getId());
        promoCampaignRepository.delete(campaign);
    }

    @Override
    public PromoCampaignResponse getCampaigns(Integer pageNumber, Integer pageSize) {
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, "startTime", "desc");
        Page<PromoCampaign> page = promoCampaignRepository.findAll(pageDetails);
        PromoCampaignResponse response = new PromoCampaignResponse();
        response.setContent(page.getContent().stream().map(this::mapToDTO).toList());
        response.setTotalElements(page.getTotalElements());
        return response;
    }

    @Override
    public PromoCampaignDTO getCampaign(Long id) {
        PromoCampaign campaign = promoCampaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PromoCampaign", "id", id));
        return mapToDTO(campaign);
    }

    @Override
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void applyActiveCampaigns() {
        LocalDateTime now = LocalDateTime.now();
        List<PromoCampaign> activeCampaigns = promoCampaignRepository.findByActiveTrueAndStartTimeBeforeAndEndTimeAfter(now, now);
        for (PromoCampaign campaign : activeCampaigns) {
            List<PromoCampaignProduct> links = promoCampaignProductRepository.findByPromoCampaignId(campaign.getId());
            for (PromoCampaignProduct link : links) {
                Product product = link.getProduct();
                product.setDiscount(campaign.getDiscountPercent());
                product.setSpecialPrice(product.getPrice() * (1 - campaign.getDiscountPercent() / 100.0));
                productRepository.save(product);
            }
        }
    }

    private void saveProducts(PromoCampaign campaign, List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            throw new APIException("A campaign must contain at least one product");
        }
        for (Long productId : productIds) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
            PromoCampaignProduct link = PromoCampaignProduct.builder()
                    .promoCampaign(campaign)
                    .product(product)
                    .build();
            promoCampaignProductRepository.save(link);
        }
    }

    private PromoCampaign mapFromDTO(PromoCampaignDTO dto) {
        PromoCampaign campaign = new PromoCampaign();
        campaign.setName(dto.getName());
        campaign.setDiscountPercent(dto.getDiscountPercent());
        campaign.setStartTime(LocalDateTime.parse(dto.getStartTime(), FORMATTER));
        campaign.setEndTime(LocalDateTime.parse(dto.getEndTime(), FORMATTER));
        campaign.setActive(dto.getActive());
        return campaign;
    }

    private PromoCampaignDTO mapToDTO(PromoCampaign campaign) {
        PromoCampaignDTO dto = new PromoCampaignDTO();
        dto.setId(campaign.getId());
        dto.setName(campaign.getName());
        dto.setDiscountPercent(campaign.getDiscountPercent());
        dto.setStartTime(campaign.getStartTime().format(FORMATTER));
        dto.setEndTime(campaign.getEndTime().format(FORMATTER));
        dto.setActive(campaign.getActive());
        List<Long> productIds = promoCampaignProductRepository.findByPromoCampaignId(campaign.getId())
                .stream().map(p -> p.getProduct().getProductId()).toList();
        dto.setProductIds(productIds);
        return dto;
    }
}
