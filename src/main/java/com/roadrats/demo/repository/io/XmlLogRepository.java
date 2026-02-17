package com.roadrats.demo.repository.io;

import com.roadrats.demo.model.io.XmlLogResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class XmlLogRepository {

    private static final Logger logger = LoggerFactory.getLogger(XmlLogRepository.class);

    @PersistenceContext(unitName = "io")
    private EntityManager entityManager;

    private static final String XML_LOG_QUERY =
            "SELECT wh_id, order_number, request_type, request_sproc, xml_message, xml_response, error_text, insert_datetime "
            + "FROM dbo.t_cls_xml_log "
            + "WHERE order_number = :orderNumber AND wh_id = :whId "
            + "ORDER BY insert_datetime DESC";

    @SuppressWarnings("unchecked")
    public List<XmlLogResult> getXmlLogs(String orderNumber, String whId) {
        logger.debug("Fetching XML logs for order={}, wh={}", orderNumber, whId);
        try {
            Query query = entityManager.createNativeQuery(XML_LOG_QUERY);
            query.setParameter("orderNumber", orderNumber);
            query.setParameter("whId", whId);
            List<Object[]> rows = (List<Object[]>) query.getResultList();

            List<XmlLogResult> results = new ArrayList<>();
            for (Object[] row : rows) {
                XmlLogResult r = new XmlLogResult();
                r.setWhId(row[0] != null ? row[0].toString() : null);
                r.setOrderNumber(row[1] != null ? row[1].toString() : null);
                r.setRequestType(row[2] != null ? row[2].toString() : null);
                r.setRequestSproc(row[3] != null ? row[3].toString() : null);
                r.setXmlMessage(row[4] != null ? row[4].toString() : null);
                r.setXmlResponse(row[5] != null ? row[5].toString() : null);
                r.setErrorText(row[6] != null ? row[6].toString() : null);
                if (row[7] != null) {
                    r.setInsertDatetime(((java.sql.Timestamp) row[7]).toLocalDateTime());
                }
                results.add(r);
            }

            logger.debug("Found {} XML log entries for order={}, wh={}", results.size(), orderNumber, whId);
            return results;
        } catch (Exception e) {
            logger.error("Error fetching XML logs for order={}, wh={}: {}", orderNumber, whId, e.getMessage());
            throw new RuntimeException("Failed to fetch XML logs: " + e.getMessage(), e);
        }
    }
}
