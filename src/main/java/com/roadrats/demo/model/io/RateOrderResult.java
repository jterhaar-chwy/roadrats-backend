package com.roadrats.demo.model.io;

/**
 * Represents a row from the 2nd rate order queue query joined with ShipperOrigins.
 * Mirrors the Python get_rate_results() output in 2ndRateSat.py.
 */
public class RateOrderResult {
    private String type;
    private String whId;
    private String orderNumber;
    private String zip;
    private String origin;

    public RateOrderResult() {
    }

    public RateOrderResult(String type, String whId, String orderNumber, String zip, String origin) {
        this.type = type;
        this.whId = whId;
        this.orderNumber = orderNumber;
        this.zip = zip;
        this.origin = origin;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getWhId() { return whId; }
    public void setWhId(String whId) { this.whId = whId; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public String getZip() { return zip; }
    public void setZip(String zip) { this.zip = zip; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
}
