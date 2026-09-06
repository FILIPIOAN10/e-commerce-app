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
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import com.ecommerce.project.service.pricing.Money;
import com.ecommerce.project.util.SortWhitelist;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromoCampaignServiceImpl implements PromoCampaignService {

    /** Arbitrary constant key for pg_try_advisory_xact_lock — must be stable across instances. */
    private static final long ADVISORY_LOCK_KEY = 91_447_268L;

    private final PromoCampaignRepository promoCampaignRepository;
    private final PromoCampaignProductRepository promoCampaignProductRepository;
    private final ProductRepository productRepository;
    private final EntityManager entityManager;

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

        // The link rows are about to be replaced, and they are what remembers
        // each product's pre-campaign discount. Release the products first or
        // that price is gone and the discount is stranded on them. The next
        // sweep re-applies under the new terms if the campaign is still running.
        if (Boolean.TRUE.equals(campaign.getApplied())) {
            revertCampaignPrices(campaign);
        }

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

        // Deleting a running campaign used to leave its discount on every
        // product it touched, with the rows that could undo it deleted in the
        // same breath. Put the prices back while the links still exist.
        if (Boolean.TRUE.equals(campaign.getApplied())) {
            revertCampaignPrices(campaign);
        }

        promoCampaignProductRepository.deleteByPromoCampaignId(campaign.getId());
        promoCampaignRepository.delete(campaign);
    }

    @Override
    public PromoCampaignResponse getCampaigns(Integer pageNumber, Integer pageSize) {
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, "startTime", "desc",
                "startTime", SortWhitelist.PROMO_CAMPAIGN);
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

    /**
     * Brings product prices into line with the campaigns that should be running.
     *
     * <p>Was: reload every product of every active campaign once a minute and
     * rewrite the same discount onto it — 1 + N queries and N updates per
     * campaign, forever, bumping {@code @Version} and churning WAL for values
     * that had not changed. And it only ever pushed prices <em>on</em>; when a
     * campaign ended it simply stopped being selected, leaving the promotional
     * price on the product permanently.
     *
     * <p>Now each campaign is applied once and reverted once, so a steady state
     * costs two SELECTs that return nothing. The advisory lock — the same one
     * the other sweeps in this codebase use — keeps two instances from writing
     * the same rows and colliding on the version column.
     *
     * <p>Scheduling lives in {@code PromoCampaignSweepJob}, so tests can drive
     * one pass directly rather than waiting on (or racing) a timer.
     */
    @Override
    @Transactional
    public void applyActiveCampaigns() {
        if (!acquireAdvisoryLock()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        // Revert first: a product moving from one campaign straight into another
        // must have the old discount taken off before the new one is recorded,
        // or the second campaign captures the first campaign's price as the
        // "original" and the real one is lost.
        revertFinishedCampaigns(now);
        applyDueCampaigns(now);
    }

    private void applyDueCampaigns(LocalDateTime now) {
        List<PromoCampaign> due = promoCampaignRepository
                .findByActiveTrueAndAppliedFalseAndStartTimeBeforeAndEndTimeAfter(now, now);

        for (PromoCampaign campaign : due) {
            BigDecimal percent = campaign.getDiscountPercent() == null ? BigDecimal.ZERO : campaign.getDiscountPercent();
            for (PromoCampaignProduct link : promoCampaignProductRepository
                    .findByCampaignIdWithProduct(campaign.getId())) {
                Product product = link.getProduct();
                link.setOriginalDiscount(product.getDiscount() == null
                        ? BigDecimal.ZERO
                        : product.getDiscount());
                product.setDiscount(percent);
                product.setSpecialPrice(Money.of(product.getPrice())
                        .percentage(BigDecimal.valueOf(100).subtract(percent))
                        .toBigDecimal());
            }
            campaign.setApplied(true);
            log.info("Promo campaign {} ('{}') applied at {}%", campaign.getId(), campaign.getName(), percent);
        }
    }

    private void revertFinishedCampaigns(LocalDateTime now) {
        for (PromoCampaign campaign : promoCampaignRepository.findAppliedButNotRunning(now)) {
            revertCampaignPrices(campaign);
            log.info("Promo campaign {} ('{}') reverted", campaign.getId(), campaign.getName());
        }
    }

    /**
     * Puts every product in a campaign back to the discount it had before the
     * campaign was applied, and clears the record of it.
     *
     * <p>Shared by the sweep, {@code deleteCampaign} and {@code updateCampaign}
     * — deleting or re-scoping a live campaign has to release its products too,
     * otherwise the rows that remembered the original price are gone and the
     * discount is stranded on the product with nothing left to undo it.
     */
    private void revertCampaignPrices(PromoCampaign campaign) {
        for (PromoCampaignProduct link : promoCampaignProductRepository
                .findByCampaignIdWithProduct(campaign.getId())) {
            Product product = link.getProduct();
            BigDecimal original = link.getOriginalDiscount() == null
                    ? BigDecimal.ZERO
                    : link.getOriginalDiscount();
            product.setDiscount(original);
            product.setSpecialPrice(Money.of(product.getPrice())
                    .percentage(BigDecimal.valueOf(100).subtract(original))
                    .toBigDecimal());
            link.setOriginalDiscount(null);
        }
        campaign.setApplied(false);
    }

    /**
     * Transaction-level advisory lock, released automatically on commit or
     * rollback. Mirrors {@code AbandonedCartSweepService}; the key is arbitrary
     * but must stay stable across instances.
     */
    private boolean acquireAdvisoryLock() {
        Object result = entityManager
                .createNativeQuery("SELECT pg_try_advisory_xact_lock(:key)")
                .setParameter("key", ADVISORY_LOCK_KEY)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
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
