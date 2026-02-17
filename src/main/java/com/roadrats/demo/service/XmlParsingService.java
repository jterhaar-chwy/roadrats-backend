package com.roadrats.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class XmlParsingService {

    private static final Logger logger = LoggerFactory.getLogger(XmlParsingService.class);

    private static final DateTimeFormatter[] DATE_FORMATS = {
        DateTimeFormatter.ofPattern("M/d/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("M/d/yy")
    };

    /**
     * Extract consignee information from XML message.
     * Mirrors Python extract_consignee_info().
     */
    public Map<String, String> extractConsigneeInfo(String xmlMessage) {
        if (xmlMessage == null || xmlMessage.isBlank()) {
            return null;
        }
        try {
            Document doc = parseXml(xmlMessage);
            Map<String, String> consignee = new LinkedHashMap<>();
            consignee.put("contact", getElementText(doc, "CONSIGNEE_CONTACT"));
            consignee.put("address1", getElementText(doc, "CONSIGNEE_ADDRESS1"));
            consignee.put("address2", getElementText(doc, "CONSIGNEE_ADDRESS2"));
            consignee.put("city", getElementText(doc, "CONSIGNEE_CITY"));
            consignee.put("state", getElementText(doc, "CONSIGNEE_STATE"));
            consignee.put("postalcode", getElementText(doc, "CONSIGNEE_POSTALCODE"));
            return consignee;
        } catch (Exception e) {
            logger.debug("Failed to parse consignee info from XML: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract shipping and arrival dates from XML response.
     * Mirrors Python extract_shipping_dates().
     */
    public Map<String, Object> extractShippingDates(String xmlResponse) {
        if (xmlResponse == null || xmlResponse.isBlank()) {
            return null;
        }
        try {
            Document doc = parseXml(xmlResponse);

            String shipDateStr = getElementText(doc, "SHIPDATE");
            String arriveDateStr = getElementText(doc, "ARRIVE_DATE");
            String travelDays = getElementText(doc, "CHE_TRAVEL_DAYS");

            LocalDate shipDateObj = parseDate(shipDateStr);
            LocalDate arriveDateObj = parseDate(arriveDateStr);

            String shipDay = shipDateObj != null ? shipDateObj.getDayOfWeek().toString() : null;
            String arriveDay = arriveDateObj != null ? arriveDateObj.getDayOfWeek().toString() : null;

            Integer daysBetween = null;
            if (shipDateObj != null && arriveDateObj != null) {
                daysBetween = (int) ChronoUnit.DAYS.between(shipDateObj, arriveDateObj);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("shipDate", shipDateStr);
            result.put("arriveDate", arriveDateStr);
            result.put("shipDay", shipDay);
            result.put("arriveDay", arriveDay);
            result.put("travelDays", travelDays);
            result.put("daysBetween", daysBetween);
            return result;
        } catch (Exception e) {
            logger.debug("Failed to parse shipping dates from XML: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract CHE_ROUTE from XML response.
     * Mirrors Python extract_route().
     */
    public String extractRoute(String xmlResponse) {
        if (xmlResponse == null || xmlResponse.isBlank()) {
            return "";
        }
        try {
            Document doc = parseXml(xmlResponse);
            String route = getElementText(doc, "CHE_ROUTE");
            return route != null ? route.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Extract SERVICE from XML response.
     * Mirrors Python extract_service_level().
     */
    public String extractServiceLevel(String xmlResponse) {
        if (xmlResponse == null || xmlResponse.isBlank()) {
            return "";
        }
        try {
            Document doc = parseXml(xmlResponse);
            String service = getElementText(doc, "SERVICE");
            return service != null ? service.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Extract error messages from XML response, checking multiple possible locations.
     * Mirrors Python extract_error_from_xml().
     */
    public String extractErrorFromXml(String xmlResponse) {
        if (xmlResponse == null || xmlResponse.isBlank()) {
            return "";
        }
        try {
            Document doc = parseXml(xmlResponse);

            // Check for ERROR_MESSAGE tag first
            String errorMessage = getElementText(doc, "ERROR_MESSAGE");
            if (errorMessage != null && !errorMessage.isBlank()) {
                return errorMessage.trim();
            }

            // Fall back to ERROR tag
            String error = getElementText(doc, "ERROR");
            if (error != null && !error.isBlank()) {
                return error.trim();
            }

            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Disable external entities for security
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private String getElementText(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            Element element = (Element) nodes.item(0);
            String text = element.getTextContent();
            return (text != null && !text.isBlank()) ? text.trim() : null;
        }
        return null;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateStr.trim(), fmt);
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }
        return null;
    }
}
