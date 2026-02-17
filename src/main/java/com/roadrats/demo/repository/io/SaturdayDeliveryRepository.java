package com.roadrats.demo.repository.io;

import com.roadrats.demo.model.io.RateOrderResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Queries the 2nd rate order queue joined with ShipperOrigins to get
 * zip codes and origins for Saturday delivery checking.
 * Mirrors Python get_rate_results() in 2ndRateSat.py.
 */
@Repository
public class SaturdayDeliveryRepository {

    private static final Logger logger = LoggerFactory.getLogger(SaturdayDeliveryRepository.class);

    @PersistenceContext(unitName = "io")
    private EntityManager entityManager;

    private static final String RATE_ORDER_QUERY = """
        WITH ShipperOrigins AS (
            SELECT 'MCO1'         AS shipper, '34475'         AS origin
            UNION ALL SELECT 'PHX1'         AS shipper, '85338'         AS origin
            UNION ALL SELECT 'PHX2'         AS shipper, '85338_PHX2'    AS origin
            UNION ALL SELECT 'SDF1'         AS shipper, '40299'         AS origin
            UNION ALL SELECT 'DFW7'         AS shipper, '75236_DFW7'    AS origin
            UNION ALL SELECT 'BNA1'         AS shipper, '37122'         AS origin
            UNION ALL SELECT 'AVP1_FRESH'   AS shipper, '18706_FRESH'   AS origin
            UNION ALL SELECT 'RNO1'         AS shipper, '89506'         AS origin
            UNION ALL SELECT 'BKY1'         AS shipper, '85043'         AS origin
            UNION ALL SELECT 'CFC1_FRESH'   AS shipper, '46118_FRESH'   AS origin
            UNION ALL SELECT 'EFC3'         AS shipper, '17050'         AS origin
            UNION ALL SELECT 'CFF1'         AS shipper, '46118'         AS origin
            UNION ALL SELECT 'DFW1'         AS shipper, '75236'         AS origin
            UNION ALL SELECT 'MCO2'         AS shipper, '34475_MCO2'    AS origin
            UNION ALL SELECT 'PHX1_OCEANSIDE'   AS shipper, '92056'     AS origin
            UNION ALL SELECT 'DAY1'         AS shipper, '45377'         AS origin
            UNION ALL SELECT 'MCI1'         AS shipper, '64012'         AS origin
            UNION ALL SELECT 'AVP1'         AS shipper, '18706'         AS origin
            UNION ALL SELECT 'AVP2'         AS shipper, '18434'         AS origin
            UNION ALL SELECT 'PHX1_FULLERTON'  AS shipper, '92835'      AS origin
            UNION ALL SELECT 'PHX1_SUNVALLEY'  AS shipper, '91352'      AS origin
            UNION ALL SELECT 'DFW8'         AS shipper, '75236_DFW8'    AS origin
            UNION ALL SELECT 'CLT1'         AS shipper, '28146'         AS origin
            UNION ALL SELECT 'SDF4'         AS shipper, '40299_SDF4'    AS origin
            UNION ALL SELECT 'AVP4'         AS shipper, '18640'         AS origin
            UNION ALL SELECT 'MDT3'         AS shipper, '17339_MDT3'    AS origin
            UNION ALL SELECT 'DFW3'         AS shipper, '75134'         AS origin
            UNION ALL SELECT 'CHEWYPHX1MM'  AS shipper, '92835_MM'      AS origin
            UNION ALL SELECT 'CFC1'         AS shipper, '46118'         AS origin
            UNION ALL SELECT 'MDT1'         AS shipper, '17339'         AS origin
            UNION ALL SELECT 'MCO4'         AS shipper, '34475_MCO4'    AS origin
            UNION ALL SELECT 'RNF1'         AS shipper, '89506_RNF1'    AS origin
        )
        SELECT
            '2nd rate'      AS type,
            cls.wh_id       AS wh_id,
            cls.order_number,
            LEFT(pkc.ship_to_zip, 5) AS zip,
            so.origin
        FROM t_cls_rate_order_queue AS cls
             JOIN dbo.t_pick_container AS pkc
               ON pkc.order_number = cls.order_number
             JOIN ShipperOrigins AS so
               ON so.shipper = cls.wh_id
        WHERE cls.attempts > 0
        ORDER BY cls.insert_datetime
        """;

    @SuppressWarnings("unchecked")
    public List<RateOrderResult> getRateOrderResults() {
        logger.debug("Executing rate order query for Saturday delivery check");
        try {
            Query query = entityManager.createNativeQuery(RATE_ORDER_QUERY);
            List<Object[]> results = (List<Object[]>) query.getResultList();
            logger.debug("Rate order query returned {} rows", results.size());

            List<RateOrderResult> mapped = new ArrayList<>();
            for (Object[] row : results) {
                RateOrderResult r = new RateOrderResult();
                r.setType(row[0] != null ? row[0].toString() : null);
                r.setWhId(row[1] != null ? row[1].toString() : null);
                r.setOrderNumber(row[2] != null ? row[2].toString() : null);
                r.setZip(row[3] != null ? row[3].toString() : null);
                r.setOrigin(row[4] != null ? row[4].toString() : null);
                mapped.add(r);
            }
            return mapped;
        } catch (Exception e) {
            logger.error("Error executing rate order query", e);
            throw new RuntimeException("Failed to execute rate order query: " + e.getMessage(), e);
        }
    }
}
