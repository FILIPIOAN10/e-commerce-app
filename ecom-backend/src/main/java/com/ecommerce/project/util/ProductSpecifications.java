package com.ecommerce.project.util;

import com.ecommerce.project.model.Product;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> withKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        String likeKeyword = "%" + keyword.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("productName")), likeKeyword),
                cb.like(cb.lower(root.get("description")), likeKeyword),
                cb.like(cb.lower(root.get("tags")), likeKeyword)
        );
    }

    public static Specification<Product> withCategory(String category) {
        if (category == null || category.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.like(root.get("category").get("categoryName"), category);
    }

    public static Specification<Product> withTerms(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> {
            List<Predicate> termPredicates = new ArrayList<>();
            for (String term : terms) {
                String likeTerm = "%" + term.toLowerCase() + "%";
                termPredicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("productName")), likeTerm),
                                cb.like(cb.lower(root.get("description")), likeTerm),
                                cb.like(cb.lower(root.get("tags")), likeTerm),
                                cb.like(cb.lower(root.get("category").get("categoryName")), likeTerm)
                        ));
            }
            return cb.or(termPredicates.toArray(Predicate[]::new));
        };
    }
}
