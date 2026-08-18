package com.ecommerce.project.service;

import com.ecommerce.project.payload.BundleDTO;
import com.ecommerce.project.payload.BundleResponse;

import java.util.List;

public interface BundleService {
    BundleDTO createBundle(BundleDTO bundleDTO);
    BundleDTO updateBundle(Long bundleId, BundleDTO bundleDTO);
    BundleDTO getBundleById(Long bundleId);
    List<BundleDTO> getAllBundles();
    List<BundleDTO> getActiveBundles();
    BundleDTO deleteBundle(Long bundleId);
}
