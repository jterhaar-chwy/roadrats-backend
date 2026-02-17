package com.roadrats.demo.repository.io;

import com.roadrats.demo.model.io.QueueStatusResult;
import com.roadrats.demo.service.XmlParsingService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Queries the 6 CLS queue tables for orders with attempts > 2.
 * Mirrors the queries in cls_scripts.sql.
 */
@Repository
public class QueueStatusRepository {

    private static final Logger logger = LoggerFactory.getLogger(QueueStatusRepository.class);

    @PersistenceContext(unitName = "io")
    private EntityManager entityManager;

    @Autowired
    private XmlParsingService xmlParsingService;

    private static final Map<String, String> QUEUE_QUERIES = new LinkedHashMap<>();

    static {
        QUEUE_QUERIES.put("rate",
                "SELECT 'rate' as type, q.wh_id, q.order_number, cls.xml_response, cls.xml_message "
                + "FROM t_cls_rate_queue q "
                + "JOIN dbo.t_cls_xml_log cls ON cls.order_number = q.order_number AND cls.wh_id = q.wh_id "
                + "WHERE q.attempts > 2 ORDER BY q.order_number");
        QUEUE_QUERIES.put("2nd rate",
                "SELECT '2nd rate' as type, q.wh_id, q.order_number, cls.xml_response, cls.xml_message "
                + "FROM t_cls_rate_order_queue q "
                + "JOIN dbo.t_cls_xml_log cls ON cls.order_number = q.order_number AND cls.wh_id = q.wh_id "
                + "WHERE q.attempts > 2 ORDER BY q.order_number");
        QUEUE_QUERIES.put("rerate",
                "SELECT 'rerate' as type, q.wh_id, q.order_number, cls.xml_response, cls.xml_message "
                + "FROM t_cls_rerate_order_queue q "
                + "JOIN dbo.t_cls_xml_log cls ON cls.order_number = q.order_number AND cls.wh_id = q.wh_id "
                + "WHERE q.attempts > 2 ORDER BY q.order_number");
        QUEUE_QUERIES.put("manifest",
                "SELECT 'manifest' as type, q.wh_id, q.order_number, cls.xml_response, cls.xml_message "
                + "FROM t_cls_manifest_queue q "
                + "JOIN dbo.t_cls_xml_log cls ON cls.order_number = q.order_number AND cls.wh_id = q.wh_id "
                + "WHERE q.attempts > 2 ORDER BY q.order_number");
        QUEUE_QUERIES.put("remanifest",
                "SELECT 'remanifest' as type, q.wh_id, q.order_number, cls.xml_response, cls.xml_message "
                + "FROM t_cls_remanifest_queue q "
                + "JOIN dbo.t_cls_xml_log cls ON cls.order_number = q.order_number AND cls.wh_id = q.wh_id "
                + "WHERE q.attempts > 2 ORDER BY q.order_number");
        QUEUE_QUERIES.put("release",
                "SELECT 'release' as type, q.wh_id, q.order_number, cls.xml_response, cls.xml_message "
                + "FROM t_cls_release_queue q "
                + "JOIN dbo.t_cls_xml_log cls ON cls.order_number = q.order_number AND cls.wh_id = q.wh_id "
                + "WHERE q.attempts > 2 ORDER BY q.order_number");
    }

    /**
     * Query all 6 CLS queues and return results grouped by queue type.
     */
    @SuppressWarnings("unchecked")
    public Map<String, List<QueueStatusResult>> getAllQueueStatuses() {
        Map<String, List<QueueStatusResult>> allResults = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : QUEUE_QUERIES.entrySet()) {
            String queueType = entry.getKey();
            String sql = entry.getValue();

            try {
                Query query = entityManager.createNativeQuery(sql);
                List<Object[]> rows = (List<Object[]>) query.getResultList();

                // Parse all rows, then deduplicate per (wh_id, order_number)
                // preferring the row that has a non-empty route
                Map<String, QueueStatusResult> deduped = new LinkedHashMap<>();
                for (Object[] row : rows) {
                    String whId = row[1] != null ? row[1].toString() : null;
                    String orderNumber = row[2] != null ? row[2].toString() : null;
                    String key = whId + "|" + orderNumber;

                    // Parse error and route from xml_response
                    String errorText = null;
                    String route = null;
                    String xmlResponse = (row.length > 3 && row[3] != null) ? row[3].toString() : null;
                    if (xmlResponse != null) {
                        errorText = xmlParsingService.extractErrorFromXml(xmlResponse);
                        route = xmlParsingService.extractRoute(xmlResponse);
                    }

                    // Parse zip from xml_message (consignee info is in the request)
                    String zip = null;
                    String xmlMessage = (row.length > 4 && row[4] != null) ? row[4].toString() : null;
                    if (xmlMessage != null) {
                        Map<String, String> consignee = xmlParsingService.extractConsigneeInfo(xmlMessage);
                        if (consignee != null && consignee.get("postalcode") != null) {
                            String pc = consignee.get("postalcode");
                            zip = pc.length() >= 5 ? pc.substring(0, 5) : pc;
                        }
                    }

                    QueueStatusResult existing = deduped.get(key);
                    boolean hasRoute = route != null && !route.isEmpty();
                    boolean existingHasRoute = existing != null
                            && existing.getRoute() != null && !existing.getRoute().isEmpty();

                    // Keep this row if: no existing yet, or this one has route and existing doesn't
                    if (existing == null || (hasRoute && !existingHasRoute)) {
                        QueueStatusResult r = new QueueStatusResult();
                        r.setType(row[0] != null ? row[0].toString() : null);
                        r.setWhId(whId);
                        r.setOrderNumber(orderNumber);
                        r.setErrorText(errorText);
                        r.setRoute(route);
                        r.setZip(zip);
                        deduped.put(key, r);
                    } else if (existing.getZip() == null && zip != null) {
                        // Fill in zip from another row if missing
                        existing.setZip(zip);
                    }
                }

                List<QueueStatusResult> mapped = new ArrayList<>(deduped.values());
                allResults.put(queueType, mapped);
                logger.debug("Queue '{}' returned {} rows with attempts > 2", queueType, mapped.size());
            } catch (Exception e) {
                logger.error("Error querying queue '{}': {}", queueType, e.getMessage());
                allResults.put(queueType, new ArrayList<>());
            }
        }

        return allResults;
    }
}
