package com.tomerwolf.devicemonitor.service;

import com.tomerwolf.devicemonitor.adb.AdbService;
import com.tomerwolf.devicemonitor.model.ConnectionState;
import com.tomerwolf.devicemonitor.model.DeviceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Polls adb on a fixed interval, tracks each known device's connection state, and logs a line
 * whenever a device's state actually changes -- not on every poll -- the same alert-on-transition
 * principle used in the NOC Monitor project, applied here to device connectivity instead of
 * website uptime.
 */
@Service
public class DeviceMonitorService {

    private static final Logger log = LoggerFactory.getLogger(DeviceMonitorService.class);

    private final AdbService adbService;
    private final Map<String, DeviceStatus> devices = new ConcurrentHashMap<>();

    public DeviceMonitorService(AdbService adbService) {
        this.adbService = adbService;
    }

    @Scheduled(fixedDelayString = "${monitor.poll-interval-ms:15000}")
    public void poll() {
        List<AdbService.RawDevice> raw = adbService.listDevices();

        for (AdbService.RawDevice rd : raw) {
            ConnectionState newState = switch (rd.state()) {
                case "device" -> ConnectionState.ONLINE;
                case "unauthorized" -> ConnectionState.UNAUTHORIZED;
                default -> ConnectionState.OFFLINE;
            };

            DeviceStatus status = devices.computeIfAbsent(rd.serial(), s -> new DeviceStatus(rd.serial(), rd.model()));
            ConnectionState previous = status.getConnectionState();

            status.setModel(rd.model());
            status.setConnectionState(newState);
            status.setLastChecked(Instant.now());

            if (newState == ConnectionState.ONLINE) {
                status.setBatteryLevel(adbService.getBatteryLevel(rd.serial()));
                status.setScreenState(adbService.getScreenState(rd.serial()));
            }

            if (previous != null && previous != newState) {
                log.info("Device {} ({}) transitioned {} -> {}", rd.serial(), rd.model(), previous, newState);
            } else if (previous == null) {
                log.info("Discovered device {} ({}), state {}", rd.serial(), rd.model(), newState);
            }
        }

        // adb only reports devices it can currently see. If a previously-known device stops
        // appearing at all (unplugged), treat that as a transition to OFFLINE rather than letting
        // its last-known state go stale.
        Set<String> seen = raw.stream().map(AdbService.RawDevice::serial).collect(Collectors.toSet());
        for (DeviceStatus status : devices.values()) {
            if (!seen.contains(status.getSerial()) && status.getConnectionState() != ConnectionState.OFFLINE) {
                log.info("Device {} disconnected (no longer listed by adb)", status.getSerial());
                status.setConnectionState(ConnectionState.OFFLINE);
                status.setLastChecked(Instant.now());
            }
        }
    }

    public Collection<DeviceStatus> getAllDevices() {
        return devices.values();
    }

    public DeviceStatus getDevice(String serial) {
        return devices.get(serial);
    }
}
