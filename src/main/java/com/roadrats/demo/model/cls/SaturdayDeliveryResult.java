package com.roadrats.demo.model.cls;

public class SaturdayDeliveryResult {
    private String postalCode;
    private String service;
    private String transitDays;

    public SaturdayDeliveryResult() {
    }

    public SaturdayDeliveryResult(String postalCode, String service, String transitDays) {
        this.postalCode = postalCode;
        this.service = service;
        this.transitDays = transitDays;
    }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getTransitDays() { return transitDays; }
    public void setTransitDays(String transitDays) { this.transitDays = transitDays; }
}
