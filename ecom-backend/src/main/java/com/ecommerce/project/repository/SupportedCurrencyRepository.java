package com.ecommerce.project.repository;

import com.ecommerce.project.model.SupportedCurrency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportedCurrencyRepository extends JpaRepository<SupportedCurrency, String> {

    List<SupportedCurrency> findByActiveTrueOrderBySortOrderAsc();
}
