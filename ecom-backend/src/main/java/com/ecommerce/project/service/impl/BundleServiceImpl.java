package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Bundle;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.BundleDTO;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.repository.BundleRepository;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.service.BundleService;
import com.ecommerce.project.util.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.ecommerce.project.service.pricing.Money;

@Service
@RequiredArgsConstructor
public class BundleServiceImpl implements BundleService {

    private final BundleRepository bundleRepository;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public BundleDTO createBundle(BundleDTO bundleDTO) {
        Bundle bundle = new Bundle();
        bundle.setName(bundleDTO.getName());
        bundle.setDescription(bundleDTO.getDescription());
        bundle.setDiscountPercentage(bundleDTO.getDiscountPercentage());
        bundle.setActive(bundleDTO.getActive());

        List<Product> products = resolveProducts(bundleDTO.getProducts());
        bundle.setProducts(products);

        return mapToDTO(bundleRepository.save(bundle));
    }

    @Override
    @Transactional
    public BundleDTO updateBundle(Long bundleId, BundleDTO bundleDTO) {
        Bundle bundle = bundleRepository.findById(bundleId)
                .orElseThrow(() -> new ResourceNotFoundException("Bundle", "bundleId", bundleId));

        bundle.setName(bundleDTO.getName());
        bundle.setDescription(bundleDTO.getDescription());
        bundle.setDiscountPercentage(bundleDTO.getDiscountPercentage());
        bundle.setActive(bundleDTO.getActive());

        List<Product> products = resolveProducts(bundleDTO.getProducts());
        bundle.setProducts(products);

        return mapToDTO(bundleRepository.save(bundle));
    }

    @Override
    public BundleDTO getBundleById(Long bundleId) {
        Bundle bundle = bundleRepository.findById(bundleId)
                .orElseThrow(() -> new ResourceNotFoundException("Bundle", "bundleId", bundleId));
        return mapToDTO(bundle);
    }

    @Override
    public List<BundleDTO> getAllBundles() {
        return mapBundlesToDTOs(bundleRepository.findAllWithProducts());
    }

    @Override
    public List<BundleDTO> getActiveBundles() {
        return mapBundlesToDTOs(bundleRepository.findByActiveTrueWithProducts());
    }

    @Override
    @Transactional
    public BundleDTO deleteBundle(Long bundleId) {
        Bundle bundle = bundleRepository.findById(bundleId)
                .orElseThrow(() -> new ResourceNotFoundException("Bundle", "bundleId", bundleId));
        bundleRepository.delete(bundle);
        return mapToDTO(bundle);
    }

    private List<Product> resolveProducts(List<ProductDTO> productDTOs) {
        if (productDTOs == null || productDTOs.isEmpty()) {
            throw new APIException("Bundle must contain at least one product");
        }
        List<Long> productIds = productDTOs.stream()
                .map(ProductDTO::getProductId)
                .collect(Collectors.toList());
        List<Product> products = productRepository.findAllById(productIds);
        if (products.size() != productIds.size()) {
            throw new APIException("One or more products in the bundle do not exist");
        }
        return products;
    }

    public BundleDTO mapToDTO(Bundle bundle) {
        return mapBundlesToDTOs(List.of(bundle)).get(0);
    }

    /**
     * Maps several bundles in one pass: every product across every bundle is
     * turned into a DTO once (so {@link ProductMapper}'s two review-aggregate
     * queries run once for the whole call, not once per bundle).
     */
    private List<BundleDTO> mapBundlesToDTOs(List<Bundle> bundles) {
        Map<Long, Product> productsById = new LinkedHashMap<>();
        for (Bundle bundle : bundles) {
            for (Product product : bundle.getProducts()) {
                productsById.putIfAbsent(product.getProductId(), product);
            }
        }
        Map<Long, ProductDTO> productDtoById = productMapper.mapProductsToDTOs(List.copyOf(productsById.values()))
                .stream()
                .collect(Collectors.toMap(ProductDTO::getProductId, Function.identity(), (a, b) -> a));

        return bundles.stream().map(bundle -> buildBundleDTO(bundle, productDtoById)).collect(Collectors.toList());
    }

    private BundleDTO buildBundleDTO(Bundle bundle, Map<Long, ProductDTO> productDtoById) {
        BundleDTO dto = modelMapper.map(bundle, BundleDTO.class);
        dto.setProducts(bundle.getProducts().stream()
                .map(p -> productDtoById.get(p.getProductId()))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList()));

        // Money rounds to the cent on every operation, so the three figures below
        // agree by construction — where the hand-rolled Math.round(x * 100) / 100
        // this replaces could leave savings off the difference by a cent.
        Money bundlePrice = bundle.getProducts().stream()
                .map(product -> Money.of(product.getSpecialPrice()))
                .reduce(Money.ZERO, Money::add);
        BigDecimal discountPercent =
                bundle.getDiscountPercentage() != null ? bundle.getDiscountPercentage() : BigDecimal.ZERO;
        Money discountedPrice = bundlePrice.percentage(BigDecimal.valueOf(100).subtract(discountPercent));
        Money savings = bundlePrice.subtract(discountedPrice);

        dto.setBundlePrice(bundlePrice.toBigDecimal());
        dto.setDiscountedPrice(discountedPrice.toBigDecimal());
        dto.setSavings(savings.toBigDecimal());

        return dto;
    }
}
