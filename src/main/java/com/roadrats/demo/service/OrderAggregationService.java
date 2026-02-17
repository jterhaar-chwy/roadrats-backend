package com.roadrats.demo.service;

import com.roadrats.demo.model.io.EnrichedOrderResult;
import com.roadrats.demo.model.io.OrderImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Groups raw OrderImportResult rows by (whId, orderNumber), aggregates item numbers,
 * picks the most relevant XML messages, and enriches with parsed XML data.
 * Mirrors the Python refresh_data() grouping/aggregation logic in CLS_Debugger.py.
 */
@Service
public class OrderAggregationService {

    private static final Logger logger = LoggerFactory.getLogger(OrderAggregationService.class);

    @Autowired
    private XmlParsingService xmlParsingService;

    /**
     * Aggregate raw query results into enriched, deduplicated order results.
     */
    public List<EnrichedOrderResult> aggregateAndEnrich(List<OrderImportResult> rawResults) {
        if (rawResults == null || rawResults.isEmpty()) {
            return Collections.emptyList();
        }

        // Group by (whId, orderNumber)
        Map<String, List<OrderImportResult>> grouped = rawResults.stream()
                .collect(Collectors.groupingBy(
                        r -> (r.getWhId() != null ? r.getWhId() : "") + "|" + (r.getOrderNumber() != null ? r.getOrderNumber() : ""),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<EnrichedOrderResult> enrichedResults = new ArrayList<>();

        for (Map.Entry<String, List<OrderImportResult>> entry : grouped.entrySet()) {
            List<OrderImportResult> group = entry.getValue();
            EnrichedOrderResult enriched = processGroup(group);
            enrichedResults.add(enriched);
        }

        logger.debug("Aggregated {} raw rows into {} enriched results", rawResults.size(), enrichedResults.size());
        return enrichedResults;
    }

    private EnrichedOrderResult processGroup(List<OrderImportResult> group) {
        EnrichedOrderResult enriched = new EnrichedOrderResult();

        // All rows share the same whId and orderNumber
        OrderImportResult first = group.get(0);
        enriched.setWhId(first.getWhId());
        enriched.setOrderNumber(first.getOrderNumber());

        // Aggregate unique item numbers
        String aggregatedItems = group.stream()
                .map(OrderImportResult::getItemNumber)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(", "));
        enriched.setItemNumber(aggregatedItems);

        // Aggregate unique error texts
        String aggregatedErrors = group.stream()
                .map(OrderImportResult::getErrorText)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(", "));
        enriched.setErrorText(aggregatedErrors);

        // Aggregate unique import statuses
        String aggregatedStatuses = group.stream()
                .map(OrderImportResult::getImportStatus)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(", "));
        enriched.setImportStatus(aggregatedStatuses);

        // Sort by updatedDatetime desc, then insertedDatetime desc
        List<OrderImportResult> sortedByUpdate = group.stream()
                .sorted(Comparator.comparing(
                        (OrderImportResult r) -> r.getUpdatedDatetime() != null ? r.getUpdatedDatetime() : LocalDateTime.MIN
                ).reversed().thenComparing(
                        (OrderImportResult r) -> r.getInsertedDatetime() != null ? r.getInsertedDatetime() : LocalDateTime.MIN
                ).reversed())
                .collect(Collectors.toList());

        // Pick XML from the most recent row that has error_text, or fall back to most recent overall
        OrderImportResult selectedRow = sortedByUpdate.stream()
                .filter(r -> r.getErrorText() != null && !r.getErrorText().isBlank())
                .findFirst()
                .orElse(sortedByUpdate.get(0));

        String xmlMessage = selectedRow.getXmlMessage();
        String xmlResponse = selectedRow.getXmlResponse();

        // For travel-related data, use the most recent XML response by clsInsertDatetime
        String xmlResponseForTravel = group.stream()
                .filter(r -> r.getClsInsertDatetime() != null && r.getXmlResponse() != null)
                .max(Comparator.comparing(OrderImportResult::getClsInsertDatetime))
                .map(OrderImportResult::getXmlResponse)
                .orElse(xmlResponse);

        enriched.setXmlMessage(xmlMessage);
        enriched.setXmlResponse(xmlResponse);
        enriched.setInsertedDatetime(first.getInsertedDatetime());
        enriched.setUpdatedDatetime(first.getUpdatedDatetime());
        enriched.setClsInsertDatetime(first.getClsInsertDatetime());

        // If SQL error_text is empty, try to extract from XML
        if (enriched.getErrorText() == null || enriched.getErrorText().isBlank()) {
            String xmlError = xmlParsingService.extractErrorFromXml(xmlResponse);
            if (!xmlError.isEmpty()) {
                enriched.setErrorText(xmlError);
            }
        }

        // Parse consignee info from XML message
        Map<String, String> consignee = xmlParsingService.extractConsigneeInfo(xmlMessage);
        if (consignee != null) {
            enriched.setConsigneeContact(consignee.get("contact"));
            enriched.setConsigneeAddress1(consignee.get("address1"));
            enriched.setConsigneeAddress2(consignee.get("address2"));
            enriched.setCity(consignee.get("city"));
            enriched.setState(consignee.get("state"));
            String postalCode = consignee.get("postalcode");
            if (postalCode != null && postalCode.length() >= 5) {
                postalCode = postalCode.substring(0, 5);
            }
            enriched.setPostalCode(postalCode);
        }

        // Parse shipping dates from XML response (use most recent for travel data)
        Map<String, Object> shippingInfo = xmlParsingService.extractShippingDates(xmlResponseForTravel);
        if (shippingInfo != null) {
            enriched.setShipDate((String) shippingInfo.get("shipDate"));
            enriched.setArriveDate((String) shippingInfo.get("arriveDate"));
            enriched.setShipDay((String) shippingInfo.get("shipDay"));
            enriched.setArriveDay((String) shippingInfo.get("arriveDay"));
            enriched.setTravelDays((String) shippingInfo.get("travelDays"));
            enriched.setDaysBetween((Integer) shippingInfo.get("daysBetween"));
        }

        // Extract route and service level from most recent XML response
        enriched.setRoute(xmlParsingService.extractRoute(xmlResponseForTravel));
        enriched.setServiceLevel(xmlParsingService.extractServiceLevel(xmlResponseForTravel));

        return enriched;
    }
}
