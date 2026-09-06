package com.ecommerce.project.service.impl;

import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.service.RecentlyViewedService;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.ProductMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecentlyViewedServiceImpl implements RecentlyViewedService {

    private static final int MAX_RECENTLY_VIEWED = 10;
    private static final String KEY_PREFIX = "recently_viewed:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductRepository productRepository;
    private final AuthUtil authUtil;
    private final ProductMapper productMapper;

    public RecentlyViewedServiceImpl(RedisTemplate<String, Object> redisTemplate,
                                     ProductRepository productRepository,
                                     AuthUtil authUtil,
                                     ProductMapper productMapper) {
        this.redisTemplate = redisTemplate;
        this.productRepository = productRepository;
        this.authUtil = authUtil;
        this.productMapper = productMapper;
    }

    @Override
    public void recordProductView(Long productId) {
        Long userId = authUtil.loggedInUserId();
        String key = KEY_PREFIX + userId;

        redisTemplate.opsForList().remove(key, 0, productId);
        redisTemplate.opsForList().leftPush(key, productId);
        redisTemplate.opsForList().trim(key, 0, MAX_RECENTLY_VIEWED - 1);
    }

    @Override
    // open-in-view is off, and ProductMapper walks the LAZY productImages
    // gallery; without a session held open here that is a LazyInitializationException.
    @Transactional(readOnly = true)
    public List<ProductDTO> getRecentlyViewedProducts() {
        Long userId = authUtil.loggedInUserId();
        String key = KEY_PREFIX + userId;

        List<Object> rawIds = redisTemplate.opsForList().range(key, 0, MAX_RECENTLY_VIEWED - 1);
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = rawIds.stream()
                .map(obj -> {
                    if (obj instanceof Integer) return ((Integer) obj).longValue();
                    if (obj instanceof Long) return (Long) obj;
                    if (obj instanceof Number) return ((Number) obj).longValue();
                    return Long.valueOf(obj.toString());
                })
                .toList();

        List<Product> products = productRepository.findAllById(productIds);

        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getProductId, Function.identity()));

        List<ProductDTO> result = new ArrayList<>();
        for (Long pid : productIds) {
            Product product = productMap.get(pid);
            if (product != null) {
                result.add(productMapper.mapProductToDTO(product));
            }
        }
        return result;
    }
}
