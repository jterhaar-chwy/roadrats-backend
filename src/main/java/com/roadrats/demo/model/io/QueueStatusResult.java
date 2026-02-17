package com.roadrats.demo.model.io;

/**
 * Represents a row from the CLS queue status queries (attempts > 2).
 * Mirrors the queries in cls_scripts.sql.
 */
public class QueueStatusResult {
    private String type;
    private String whId;
    private String orderNumber;
    private String zip;
    private String route;
    private String errorText;

    public QueueStatusResult() {
    }

    public QueueStatusResult(String type, String whId, String orderNumber, String zip, String route, String errorText) {
        this.type = type;
        this.whId = whId;
        this.orderNumber = orderNumber;
        this.zip = zip;
        this.route = route;
        this.errorText = errorText;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getWhId() { return whId; }
    public void setWhId(String whId) { this.whId = whId; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public String getZip() { return zip; }
    public void setZip(String zip) { this.zip = zip; }

    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }

    public String getErrorText() { return errorText; }
    public void setErrorText(String errorText) { this.errorText = errorText; }
}
