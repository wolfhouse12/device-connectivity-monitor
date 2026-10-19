package com.tomerwolf.devicemonitor.web;

import com.tomerwolf.devicemonitor.adb.AdbService;
import com.tomerwolf.devicemonitor.model.ConnectionState;
import com.tomerwolf.devicemonitor.model.DeviceStatus;
import com.tomerwolf.devicemonitor.service.DeviceMonitorService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceControllerTest {

    private final DeviceMonitorService monitor = mock(DeviceMonitorService.class);
    private final AdbService adb = mock(AdbService.class);
    private final DeviceController controller = new DeviceController(monitor, adb);

    private void deviceInState(String serial, ConnectionState state) {
        DeviceStatus status = new DeviceStatus(serial, "Pixel_7");
        status.setConnectionState(state);
        when(monitor.getDevice(serial)).thenReturn(status);
    }

    @Test
    void runsOnlyAllowlistedCommands() {
        deviceInState("abc", ConnectionState.ONLINE);
        when(adb.runShellCommand("abc", "dumpsys battery")).thenReturn(List.of("level: 80"));

        assertThat(controller.runDiagnostic("abc", "battery")).containsExactly("level: 80");

        assertThatThrownBy(() -> controller.runDiagnostic("abc", "rm -rf /sdcard"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(adb, never()).runShellCommand("abc", "rm -rf /sdcard");
    }

    @Test
    void rejectsSerialsTheMonitorNeverDiscovered() {
        assertThatThrownBy(() -> controller.screenshot("-H evil.example"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(adb, never()).takeScreenshot(anyString());
    }

    @Test
    void rejectsDevicesThatAreNotOnline() {
        deviceInState("abc", ConnectionState.UNAUTHORIZED);

        assertThatThrownBy(() -> controller.runDiagnostic("abc", "battery"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
