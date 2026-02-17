package com.roadrats.demo.model.dberrors;

import java.time.LocalDateTime;

/**
 * DTO representing a single row from ADV.dbo.t_log_message
 * with database error information (resource_name LIKE 'CANT_EXE_DB%').
 */
public class DatabaseErrorEntry {

    private String serverName;
    private LocalDateTime loggedOnLocal;
    private String machineId;
    private String userId;
    private String resourceName;
    private String details;
    private String callStack;
    private String arguments;

    public DatabaseErrorEntry() {}

    public DatabaseErrorEntry(String serverName, LocalDateTime loggedOnLocal, String machineId,
                              String userId, String resourceName, String details,
                              String callStack, String arguments) {
        this.serverName = serverName;
        this.loggedOnLocal = loggedOnLocal;
        this.machineId = machineId;
        this.userId = userId;
        this.resourceName = resourceName;
        this.details = details;
        this.callStack = callStack;
        this.arguments = arguments;
    }

    // Getters and Setters

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public LocalDateTime getLoggedOnLocal() {
        return loggedOnLocal;
    }

    public void setLoggedOnLocal(LocalDateTime loggedOnLocal) {
        this.loggedOnLocal = loggedOnLocal;
    }

    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getCallStack() {
        return callStack;
    }

    public void setCallStack(String callStack) {
        this.callStack = callStack;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }
}

