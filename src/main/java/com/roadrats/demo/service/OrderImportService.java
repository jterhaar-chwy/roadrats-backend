package com.roadrats.demo.service;

import com.roadrats.demo.model.io.EnrichedOrderResult;
import com.roadrats.demo.model.io.OrderImportResult;
import com.roadrats.demo.repository.io.OrderImportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderImportService {

    private static final Logger logger = LoggerFactory.getLogger(OrderImportService.class);

    @Autowired
    private OrderImportRepository orderImportRepository;

    @Autowired
    private OrderAggregationService orderAggregationService;

    @Transactional(transactionManager = "ioTransactionManager")
    public List<OrderImportResult> getRateQueryResults() {
        logger.debug("Executing rate query...");
        try {
            List<OrderImportResult> results = orderImportRepository.getRateQueryResults();
            logger.debug("Rate query returned {} results", results.size());
            return results;
        } catch (Exception e) {
            logger.error("Error in getRateQueryResults", e);
            throw e;
        }
    }

    @Transactional(transactionManager = "ioTransactionManager")
    public List<OrderImportResult> getRateHoldQueryResults() {
        logger.debug("Executing rate hold query...");
        try {
            List<OrderImportResult> results = orderImportRepository.getRateHoldQueryResults();
            logger.debug("Rate hold query returned {} results", results.size());
            return results;
        } catch (Exception e) {
            logger.error("Error in getRateHoldQueryResults", e);
            throw e;
        }
    }

    @Transactional(transactionManager = "ioTransactionManager")
    public List<EnrichedOrderResult> getEnrichedRateQueryResults() {
        logger.debug("Executing enriched rate query...");
        try {
            List<OrderImportResult> rawResults = orderImportRepository.getRateQueryResults();
            List<EnrichedOrderResult> enriched = orderAggregationService.aggregateAndEnrich(rawResults);
            logger.debug("Enriched rate query: {} raw rows -> {} enriched results", rawResults.size(), enriched.size());
            return enriched;
        } catch (Exception e) {
            logger.error("Error in getEnrichedRateQueryResults", e);
            throw e;
        }
    }

    @Transactional(transactionManager = "ioTransactionManager")
    public List<EnrichedOrderResult> getEnrichedRateHoldQueryResults() {
        logger.debug("Executing enriched rate hold query...");
        try {
            List<OrderImportResult> rawResults = orderImportRepository.getRateHoldQueryResults();
            List<EnrichedOrderResult> enriched = orderAggregationService.aggregateAndEnrich(rawResults);
            logger.debug("Enriched rate hold query: {} raw rows -> {} enriched results", rawResults.size(), enriched.size());
            return enriched;
        } catch (Exception e) {
            logger.error("Error in getEnrichedRateHoldQueryResults", e);
            throw e;
        }
    }
}

