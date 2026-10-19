package com.tomerwolf.devicemonitor.service;

import com.tomerwolf.devicemonitor.adb.AdbException;
import com.tomerwolf.devicemonitor.adb.AdbService;
import com.tomerwolf.devicemonitor.model.ConnectionState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceMonitorServiceTest {

    private final AdbService adb = mock(AdbService.class);
    private final DeviceMonitorService service = new DeviceMonitorService(adb);

    @Test
    void onlineDeviceGetsBatteryAndScreenState() {
        when(adb.listDevices()).thenReturn(List.of(new AdbService.RawDevice("abc", "device", "Pixel_7")));
        when(adb.getBatteryLevel("abc")).thenReturn(80);
        when(adb.getScreenState("abc")).thenReturn("Awake");

        service.poll();

        var status = service.getDevice("abc");
        assertThat(status.getConnectionState()).isEqualTo(ConnectionState.ONLINE);
        assertThat(status.getBatteryLevel()).isEqualTo(80);
        assertThat(status.getScreenState()).isEqualTo("Awake");
    }

    @Test
    void unauthorizedDeviceIsTrackedWithoutQueryingIt() {
        when(adb.listDevices()).thenReturn(List.of(new AdbService.RawDevice("abc", "unauthorized", "unknown")));

        service.poll();

        assertThat(service.getDevice("abc").getConnectionState()).isEqualTo(ConnectionState.UNAUTHORIZED);
        assertThat(service.getDevice("abc").getBatteryLevel()).isNull();
    }

    @Test
    void unpluggedDeviceBecomesOffline() {
        when(adb.listDevices())
                .thenReturn(List.of(new AdbService.RawDevice("abc", "device", "Pixel_7")))
                .thenReturn(List.of());

        service.poll();
        service.poll();

        assertThat(service.getDevice("abc").getConnectionState()).isEqualTo(ConnectionState.OFFLINE);
    }

    @Test
    void adbFailureKeepsLastKnownState() {
        when(adb.listDevices())
                .thenReturn(List.of(new AdbService.RawDevice("abc", "device", "Pixel_7")))
                .thenThrow(new AdbException("adb missing", null));

        service.poll();
        service.poll();

        assertThat(service.getDevice("abc").getConnectionState()).isEqualTo(ConnectionState.ONLINE);
    }
}
