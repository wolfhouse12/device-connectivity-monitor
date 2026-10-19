package com.tomerwolf.devicemonitor.model;

import java.time.Instant;

public class DeviceStatus {

    private final String serial;
    private String model;
    private ConnectionState connectionState;
    private Integer batteryLevel;
    private String screenState;
    private Instant lastChecked;

    public DeviceStatus(String serial, String model) {
        this.serial = serial;
        this.model = model;
    }

    public String getSerial() {
        return serial;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }

    public void setConnectionState(ConnectionState connectionState) {
        this.connectionState = connectionState;
    }

    public Integer getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(Integer batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public String getScreenState() {
        return screenState;
    }

    public void setScreenState(String screenState) {
        this.screenState = screenState;
    }

    public Instant getLastChecked() {
        return lastChecked;
    }

    public void setLastChecked(Instant lastChecked) {
        this.lastChecked = lastChecked;
    }
}
