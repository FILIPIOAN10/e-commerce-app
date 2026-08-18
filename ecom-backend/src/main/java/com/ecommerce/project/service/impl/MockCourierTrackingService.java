package com.ecommerce.project.service.impl;

import com.ecommerce.project.payload.TrackingStatus;
import com.ecommerce.project.service.CourierTrackingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class MockCourierTrackingService implements CourierTrackingService {

    @Override
    public TrackingStatus track(String carrierName, String trackingNumber) {
        if (carrierName == null || carrierName.isBlank()) {
            return new TrackingStatus("UNKNOWN", "Carrier not specified", LocalDateTime.now());
        }
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return new TrackingStatus("UNKNOWN", "Tracking number not provided", LocalDateTime.now());
        }

        log.info("Tracking return package: carrier={}, trackingNumber={}", carrierName, trackingNumber);

        // Deterministic mock behavior for testing/demo:
        // - tracking numbers ending in "DEL" simulate delivered
        // - ending in "EXP" simulate an exception
        // - all others simulate in transit
        if (trackingNumber.toUpperCase().endsWith("DEL")) {
            return new TrackingStatus("DELIVERED", "Package delivered to warehouse", LocalDateTime.now());
        }
        if (trackingNumber.toUpperCase().endsWith("EXP")) {
            return new TrackingStatus("EXCEPTION", "Delivery exception — contact carrier", LocalDateTime.now());
        }
        return new TrackingStatus("IN_TRANSIT", "Package in transit", LocalDateTime.now());
    }
}
