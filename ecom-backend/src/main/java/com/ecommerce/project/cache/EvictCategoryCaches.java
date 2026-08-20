package com.ecommerce.project.cache;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Caching(evict = {
        @CacheEvict(value = "publicCategories", allEntries = true),
        @CacheEvict(value = "publicProducts", allEntries = true),
        @CacheEvict(value = "categoryProducts", allEntries = true),
        @CacheEvict(value = "productSearch", allEntries = true),
        @CacheEvict(value = "product", allEntries = true),
        @CacheEvict(value = "adminProducts", allEntries = true),
        @CacheEvict(value = "sellerProducts", allEntries = true)
})
public @interface EvictCategoryCaches {
}
