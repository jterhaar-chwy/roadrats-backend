package com.roadrats.demo.repository.cls;

import com.roadrats.demo.model.cls.SaturdayDeliveryResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Queries ps_PRIMARY_ROUTING_GUIDE_{origin} tables on WMSSQL-CLS (DMSServer)
 * to check for Saturday delivery flags.
 * Mirrors Python query_primary_routing_guide_by_origin() in 2ndRateSat.py.
 */
@Repository
public class RoutingGuideRepository {

    private static final Logger logger = LoggerFactory.getLogger(RoutingGuideRepository.class);

    @PersistenceContext(unitName = "cls")
    private EntityManager entityManager;

    /**
     * Query the routing guide table for a specific origin and set of postal codes.
     * Returns rows where SATURDAYDELIVERY_FLAG is true.
     */
    @SuppressWarnings("unchecked")
    public List<SaturdayDeliveryResult> getSaturdayDeliveryByOrigin(String origin, List<String> postalCodes) {
        if (postalCodes == null || postalCodes.isEmpty()) {
            return new ArrayList<>();
        }

        // Sanitize origin to prevent SQL injection (only allow alphanumeric and underscore)
        if (!origin.matches("[a-zA-Z0-9_]+")) {
            logger.warn("Invalid origin value rejected: {}", origin);
            return new ArrayList<>();
        }

        String tableName = "DMSServer.dbo.ps_PRIMARY_ROUTING_GUIDE_" + origin;

        // Build parameterized IN clause
        StringBuilder queryStr = new StringBuilder();
        queryStr.append("SELECT POSTALCODE, SERVICE, TRANSIT_DAYS FROM ");
        queryStr.append(tableName);
        queryStr.append(" WHERE SATURDAYDELIVERY_FLAG = 1 AND POSTALCODE IN (");

        for (int i = 0; i < postalCodes.size(); i++) {
            if (i > 0) queryStr.append(", ");
            queryStr.append("?").append(i + 1);
        }
        queryStr.append(")");

        try {
            Query query = entityManager.createNativeQuery(queryStr.toString());
            for (int i = 0; i < postalCodes.size(); i++) {
                query.setParameter(i + 1, postalCodes.get(i));
            }

            List<Object[]> results = (List<Object[]>) query.getResultList();
            List<SaturdayDeliveryResult> mapped = new ArrayList<>();

            for (Object[] row : results) {
                SaturdayDeliveryResult r = new SaturdayDeliveryResult();
                r.setPostalCode(row[0] != null ? row[0].toString() : null);
                r.setService(row[1] != null ? row[1].toString() : null);
                r.setTransitDays(row[2] != null ? row[2].toString() : null);
                mapped.add(r);
            }

            logger.debug("Routing guide query for origin={} returned {} Saturday delivery rows", origin, mapped.size());
            return mapped;
        } catch (Exception e) {
            logger.error("Error querying routing guide for origin={}: {}", origin, e.getMessage());
            return new ArrayList<>();
        }
    }
}
