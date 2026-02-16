package com.roadrats.demo.repository.io;

import com.roadrats.demo.model.io.OrderImportResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class OrderImportRepository {

    private static final Logger logger = LoggerFactory.getLogger(OrderImportRepository.class);

    @PersistenceContext(unitName = "io")
    private EntityManager entityManager;

    private static final String RATE_QUERY = """
        ;WITH CTE AS (SELECT wh_id, order_number, inserted_datetime, updated_datetime, import_status
        FROM t_order_import_queue oiq 
        WHERE 
            ((inserted_datetime < DATEADD(MINUTE, -10, GETDATE()) AND import_status = 'XML_PARSED')
            OR (updated_datetime < DATEADD(MINUTE, -10, GETDATE()) AND import_status <> 'XML_PARSED'))
            AND NOT EXISTS (
                SELECT * FROM dbo.t_cls_rate_hold_queue rhq 
                WHERE rhq.wh_id = oiq.wh_id 
                AND rhq.order_number = oiq.order_number))
        SELECT top 1000 CTE.wh_id,CTE.order_number,pkd.item_number, cls.xml_message, cls.xml_response, error_text, import_status, inserted_datetime, updated_datetime, cls.insert_datetime as cls_insert_datetime from CTE
        join dbo.t_cls_xml_log cls on cls.order_number = CTE.order_number and cls.wh_id = CTE.wh_id 
        left join dbo.t_pick_detail pkd on pkd.order_number = CTE.order_number and pkd.wh_id = CTE.wh_id
        order by CTE.order_number
        """;

    private static final String RATE_HOLD_QUERY = """
        ;WITH CTE AS (SELECT wh_id, order_number, inserted_datetime, updated_datetime, import_status
        FROM t_order_import_queue oiq 
        WHERE 
            ((inserted_datetime < DATEADD(MINUTE, -10, GETDATE()) AND import_status = 'XML_PARSED')
            OR (updated_datetime < DATEADD(MINUTE, -10, GETDATE()) AND import_status <> 'XML_PARSED'))
            AND EXISTS (
                SELECT * FROM dbo.t_cls_rate_hold_queue rhq 
                WHERE rhq.wh_id = oiq.wh_id 
                AND rhq.order_number = oiq.order_number))
        SELECT top 1000 CTE.wh_id,CTE.order_number,pkd.item_number, cls.xml_message, cls.xml_response, error_text, import_status, inserted_datetime, updated_datetime, cls.insert_datetime as cls_insert_datetime from CTE
        join dbo.t_cls_xml_log cls on cls.order_number = CTE.order_number and cls.wh_id = CTE.wh_id 
        left join dbo.t_pick_detail pkd on pkd.order_number = CTE.order_number and pkd.wh_id = CTE.wh_id
        order by CTE.order_number
        """;

    @SuppressWarnings("unchecked")
    public List<OrderImportResult> getRateQueryResults() {
        logger.debug("Executing rate query SQL");
        try {
            Query query = entityManager.createNativeQuery(RATE_QUERY);
            List<Object[]> results = (List<Object[]>) query.getResultList();
            logger.debug("Rate query returned {} raw rows", results.size());
            return mapResults(results);
        } catch (Exception e) {
            logger.error("Error executing rate query", e);
            throw new RuntimeException("Failed to execute rate query: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<OrderImportResult> getRateHoldQueryResults() {
        logger.debug("Executing rate hold query SQL");
        try {
            Query query = entityManager.createNativeQuery(RATE_HOLD_QUERY);
            List<Object[]> results = (List<Object[]>) query.getResultList();
            logger.debug("Rate hold query returned {} raw rows", results.size());
            return mapResults(results);
        } catch (Exception e) {
            logger.error("Error executing rate hold query", e);
            throw new RuntimeException("Failed to execute rate hold query: " + e.getMessage(), e);
        }
    }

    private List<OrderImportResult> mapResults(List<Object[]> results) {
        List<OrderImportResult> mappedResults = new ArrayList<>();
        
        for (Object[] row : results) {
            OrderImportResult result = new OrderImportResult();
            result.setWhId(row[0] != null ? row[0].toString() : null);
            result.setOrderNumber(row[1] != null ? row[1].toString() : null);
            result.setItemNumber(row[2] != null ? row[2].toString() : null);
            result.setXmlMessage(row[3] != null ? row[3].toString() : null);
            result.setXmlResponse(row[4] != null ? row[4].toString() : null);
            result.setErrorText(row[5] != null ? row[5].toString() : null);
            result.setImportStatus(row[6] != null ? row[6].toString() : null);
            
            if (row[7] != null) {
                result.setInsertedDatetime(((java.sql.Timestamp) row[7]).toLocalDateTime());
            }
            if (row[8] != null) {
                result.setUpdatedDatetime(((java.sql.Timestamp) row[8]).toLocalDateTime());
            }
            if (row[9] != null) {
                result.setClsInsertDatetime(((java.sql.Timestamp) row[9]).toLocalDateTime());
            }
            
            mappedResults.add(result);
        }
        
        return mappedResults;
    }
}

