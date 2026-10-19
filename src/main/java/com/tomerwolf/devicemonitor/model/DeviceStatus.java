package com.tomerwolf.devicemonitor.model;

import java.time.Instant;

/**
 * Written by the scheduler thread and read by HTTP request threads. Fields are volatile so a
 * request always sees the latest value the poller wrote.
 */
public class DeviceStatus {

    private final String serial;
    private volatile String model;
    private volatile ConnectionState connectionState;
    private volatile Integer batteryLevel;
    private volatile String screenState;
    private volatile Instant lastChecked;

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
