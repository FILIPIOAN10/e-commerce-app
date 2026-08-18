package com.ecommerce.project.service;

import com.ecommerce.project.payload.TrackingStatus;

public interface CourierTrackingService {

    TrackingStatus track(String carrierName, String trackingNumber);
}
