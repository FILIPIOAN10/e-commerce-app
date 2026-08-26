package com.ecommerce.project.config;


import com.ecommerce.project.model.Coupon;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.CouponDTO;
import com.ecommerce.project.payload.ProductDTO;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();

        modelMapper.typeMap(Coupon.class, CouponDTO.class)
                .addMapping(Coupon::getId, CouponDTO::setCouponId);

        modelMapper.typeMap(Product.class, ProductDTO.class)
                .addMappings(mapper -> mapper.skip(ProductDTO::setImages));

        return modelMapper;
    }
}
