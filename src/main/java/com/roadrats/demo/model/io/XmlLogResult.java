package com.roadrats.demo.model.io;

import java.time.LocalDateTime;

/**
 * Represents a row from t_cls_xml_log for a specific order.
 */
public class XmlLogResult {
    private String whId;
    private String orderNumber;
    private String requestType;
    private String requestSproc;
    private String xmlMessage;
    private String xmlResponse;
    private String errorText;
    private LocalDateTime insertDatetime;

    public String getWhId() { return whId; }
    public void setWhId(String whId) { this.whId = whId; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public String getRequestSproc() { return requestSproc; }
    public void setRequestSproc(String requestSproc) { this.requestSproc = requestSproc; }

    public String getXmlMessage() { return xmlMessage; }
    public void setXmlMessage(String xmlMessage) { this.xmlMessage = xmlMessage; }

    public String getXmlResponse() { return xmlResponse; }
    public void setXmlResponse(String xmlResponse) { this.xmlResponse = xmlResponse; }

    public String getErrorText() { return errorText; }
    public void setErrorText(String errorText) { this.errorText = errorText; }

    public LocalDateTime getInsertDatetime() { return insertDatetime; }
    public void setInsertDatetime(LocalDateTime insertDatetime) { this.insertDatetime = insertDatetime; }
}
