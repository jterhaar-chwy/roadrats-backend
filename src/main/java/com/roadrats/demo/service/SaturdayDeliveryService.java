package com.roadrats.demo.service;

import com.roadrats.demo.model.cls.SaturdayDeliveryResult;
import com.roadrats.demo.model.io.RateOrderResult;
import com.roadrats.demo.repository.cls.RoutingGuideRepository;
import com.roadrats.demo.repository.io.SaturdayDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates Saturday delivery checking:
 * 1. Query IO database for 2nd rate orders with ShipperOrigins
 * 2. For each distinct origin, query CLS routing guide for Saturday delivery flags
 * 3. Aggregate and return results grouped by service
 *
 * Mirrors the Python process_saturday_deliveries() flow in CLS_Debugger.py
 * and the main() flow in 2ndRateSat.py.
 */
@Service
public class SaturdayDeliveryService {

    private static final Logger logger = LoggerFactory.getLogger(SaturdayDeliveryService.class);

    @Autowired
    private SaturdayDeliveryRepository saturdayDeliveryRepository;

    @Autowired
    private RoutingGuideRepository routingGuideRepository;

    @Transactional(transactionManager = "ioTransactionManager", readOnly = true)
    public List<RateOrderResult> getRateOrderResults() {
        return saturdayDeliveryRepository.getRateOrderResults();
    }

    /**
     * Full Saturday delivery check pipeline.
     * Returns all postal code + service combinations flagged for Saturday delivery.
     */
    public Map<String, Object> checkSaturdayDeliveries() {
        logger.info("Starting Saturday delivery check...");
        Map<String, Object> response = new LinkedHashMap<>();

        // Step 1: Get rate order results from IO database
        List<RateOrderResult> rateResults;
        try {
            rateResults = getRateOrderResults();
        } catch (Exception e) {
            logger.error("Failed to get rate order results", e);
            response.put("error", "Failed to query rate order results: " + e.getMessage());
            response.put("saturdayDeliveries", Collections.emptyList());
            return response;
        }

        if (rateResults.isEmpty()) {
            logger.info("No rate order results found");
            response.put("message", "No rate order results found");
            response.put("saturdayDeliveries", Collections.emptyList());
            response.put("groupedByService", Collections.emptyMap());
            return response;
        }

        logger.info("Found {} rate order results", rateResults.size());

        // Step 2: Group by origin and get distinct zips per origin
        Map<String, List<String>> zipsByOrigin = rateResults.stream()
                .filter(r -> r.getOrigin() != null && r.getZip() != null)
                .collect(Collectors.groupingBy(
                        RateOrderResult::getOrigin,
                        Collectors.mapping(RateOrderResult::getZip, Collectors.toList())
                ));

        // Deduplicate zips per origin
        Map<String, List<String>> uniqueZipsByOrigin = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : zipsByOrigin.entrySet()) {
            List<String> uniqueZips = entry.getValue().stream().distinct().collect(Collectors.toList());
            uniqueZipsByOrigin.put(entry.getKey(), uniqueZips);
        }

        // Step 3: Query routing guide for each origin
        List<SaturdayDeliveryResult> allSaturdayResults = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : uniqueZipsByOrigin.entrySet()) {
            String origin = entry.getKey();
            List<String> zips = entry.getValue();
            logger.debug("Querying routing guide for origin={} with {} zips", origin, zips.size());

            try {
                List<SaturdayDeliveryResult> originResults = routingGuideRepository.getSaturdayDeliveryByOrigin(origin, zips);
                allSaturdayResults.addAll(originResults);
            } catch (Exception e) {
                logger.error("Error querying routing guide for origin={}: {}", origin, e.getMessage());
            }
        }

        // Step 4: Group by service and aggregate postal codes
        Map<String, List<String>> groupedByService = allSaturdayResults.stream()
                .filter(r -> r.getService() != null && r.getPostalCode() != null)
                .collect(Collectors.groupingBy(
                        SaturdayDeliveryResult::getService,
                        TreeMap::new,
                        Collectors.mapping(SaturdayDeliveryResult::getPostalCode,
                                Collectors.collectingAndThen(
                                        Collectors.toCollection(TreeSet::new),
                                        set -> new ArrayList<>(set)
                                ))
                ));

        response.put("totalRateOrders", rateResults.size());
        response.put("originsChecked", uniqueZipsByOrigin.size());
        response.put("saturdayDeliveries", allSaturdayResults);
        response.put("totalSaturdayFlags", allSaturdayResults.size());
        response.put("groupedByService", groupedByService);

        logger.info("Saturday delivery check complete: {} results across {} services",
                allSaturdayResults.size(), groupedByService.size());

        return response;
    }
}
