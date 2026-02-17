package com.roadrats.demo.model.io;

import java.time.LocalDateTime;

public class EnrichedOrderResult {
    private String whId;
    private String orderNumber;
    private String itemNumber;
    private String errorText;
    private String importStatus;
    private String xmlMessage;
    private String xmlResponse;
    private LocalDateTime insertedDatetime;
    private LocalDateTime updatedDatetime;
    private LocalDateTime clsInsertDatetime;

    // Parsed from XML
    private String shipDate;
    private String arriveDate;
    private String shipDay;
    private String arriveDay;
    private String travelDays;
    private Integer daysBetween;
    private String serviceLevel;
    private String route;

    // Consignee info from XML
    private String consigneeContact;
    private String consigneeAddress1;
    private String consigneeAddress2;
    private String city;
    private String state;
    private String postalCode;

    public EnrichedOrderResult() {
    }

    // Getters and Setters
    public String getWhId() { return whId; }
    public void setWhId(String whId) { this.whId = whId; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public String getItemNumber() { return itemNumber; }
    public void setItemNumber(String itemNumber) { this.itemNumber = itemNumber; }

    public String getErrorText() { return errorText; }
    public void setErrorText(String errorText) { this.errorText = errorText; }

    public String getImportStatus() { return importStatus; }
    public void setImportStatus(String importStatus) { this.importStatus = importStatus; }

    public String getXmlMessage() { return xmlMessage; }
    public void setXmlMessage(String xmlMessage) { this.xmlMessage = xmlMessage; }

    public String getXmlResponse() { return xmlResponse; }
    public void setXmlResponse(String xmlResponse) { this.xmlResponse = xmlResponse; }

    public LocalDateTime getInsertedDatetime() { return insertedDatetime; }
    public void setInsertedDatetime(LocalDateTime insertedDatetime) { this.insertedDatetime = insertedDatetime; }

    public LocalDateTime getUpdatedDatetime() { return updatedDatetime; }
    public void setUpdatedDatetime(LocalDateTime updatedDatetime) { this.updatedDatetime = updatedDatetime; }

    public LocalDateTime getClsInsertDatetime() { return clsInsertDatetime; }
    public void setClsInsertDatetime(LocalDateTime clsInsertDatetime) { this.clsInsertDatetime = clsInsertDatetime; }

    public String getShipDate() { return shipDate; }
    public void setShipDate(String shipDate) { this.shipDate = shipDate; }

    public String getArriveDate() { return arriveDate; }
    public void setArriveDate(String arriveDate) { this.arriveDate = arriveDate; }

    public String getShipDay() { return shipDay; }
    public void setShipDay(String shipDay) { this.shipDay = shipDay; }

    public String getArriveDay() { return arriveDay; }
    public void setArriveDay(String arriveDay) { this.arriveDay = arriveDay; }

    public String getTravelDays() { return travelDays; }
    public void setTravelDays(String travelDays) { this.travelDays = travelDays; }

    public Integer getDaysBetween() { return daysBetween; }
    public void setDaysBetween(Integer daysBetween) { this.daysBetween = daysBetween; }

    public String getServiceLevel() { return serviceLevel; }
    public void setServiceLevel(String serviceLevel) { this.serviceLevel = serviceLevel; }

    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }

    public String getConsigneeContact() { return consigneeContact; }
    public void setConsigneeContact(String consigneeContact) { this.consigneeContact = consigneeContact; }

    public String getConsigneeAddress1() { return consigneeAddress1; }
    public void setConsigneeAddress1(String consigneeAddress1) { this.consigneeAddress1 = consigneeAddress1; }

    public String getConsigneeAddress2() { return consigneeAddress2; }
    public void setConsigneeAddress2(String consigneeAddress2) { this.consigneeAddress2 = consigneeAddress2; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
}
