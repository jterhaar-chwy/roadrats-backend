package com.roadrats.demo.model.io;

import java.time.LocalDateTime;

public class OrderImportResult {
    private String whId;
    private String orderNumber;
    private String itemNumber;
    private String xmlMessage;
    private String xmlResponse;
    private String errorText;
    private String importStatus;
    private LocalDateTime insertedDatetime;
    private LocalDateTime updatedDatetime;
    private LocalDateTime clsInsertDatetime;

    public OrderImportResult() {
    }

    public OrderImportResult(String whId, String orderNumber, String itemNumber, 
                            String xmlMessage, String xmlResponse, String errorText,
                            String importStatus, LocalDateTime insertedDatetime,
                            LocalDateTime updatedDatetime, LocalDateTime clsInsertDatetime) {
        this.whId = whId;
        this.orderNumber = orderNumber;
        this.itemNumber = itemNumber;
        this.xmlMessage = xmlMessage;
        this.xmlResponse = xmlResponse;
        this.errorText = errorText;
        this.importStatus = importStatus;
        this.insertedDatetime = insertedDatetime;
        this.updatedDatetime = updatedDatetime;
        this.clsInsertDatetime = clsInsertDatetime;
    }

    // Getters and Setters
    public String getWhId() {
        return whId;
    }

    public void setWhId(String whId) {
        this.whId = whId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getItemNumber() {
        return itemNumber;
    }

    public void setItemNumber(String itemNumber) {
        this.itemNumber = itemNumber;
    }

    public String getXmlMessage() {
        return xmlMessage;
    }

    public void setXmlMessage(String xmlMessage) {
        this.xmlMessage = xmlMessage;
    }

    public String getXmlResponse() {
        return xmlResponse;
    }

    public void setXmlResponse(String xmlResponse) {
        this.xmlResponse = xmlResponse;
    }

    public String getErrorText() {
        return errorText;
    }

    public void setErrorText(String errorText) {
        this.errorText = errorText;
    }

    public String getImportStatus() {
        return importStatus;
    }

    public void setImportStatus(String importStatus) {
        this.importStatus = importStatus;
    }

    public LocalDateTime getInsertedDatetime() {
        return insertedDatetime;
    }

    public void setInsertedDatetime(LocalDateTime insertedDatetime) {
        this.insertedDatetime = insertedDatetime;
    }

    public LocalDateTime getUpdatedDatetime() {
        return updatedDatetime;
    }

    public void setUpdatedDatetime(LocalDateTime updatedDatetime) {
        this.updatedDatetime = updatedDatetime;
    }

    public LocalDateTime getClsInsertDatetime() {
        return clsInsertDatetime;
    }

    public void setClsInsertDatetime(LocalDateTime clsInsertDatetime) {
        this.clsInsertDatetime = clsInsertDatetime;
    }
}

