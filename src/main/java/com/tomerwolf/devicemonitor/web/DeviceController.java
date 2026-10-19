package com.tomerwolf.devicemonitor.web;

import com.tomerwolf.devicemonitor.adb.AdbException;
import com.tomerwolf.devicemonitor.adb.AdbService;
import com.tomerwolf.devicemonitor.model.ConnectionState;
import com.tomerwolf.devicemonitor.model.DeviceStatus;
import com.tomerwolf.devicemonitor.service.DeviceMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private static final Logger log = LoggerFactory.getLogger(DeviceController.class);

    /**
     * Explicit allowlist of safe, read-only diagnostic commands exposed over the API. This
     * endpoint intentionally does NOT accept arbitrary shell commands from callers -- only these
     * pre-approved ones can be run, to avoid turning an HTTP endpoint into unrestricted remote
     * shell execution on the connected device.
     */
    static final Map<String, String> ALLOWED_COMMANDS = Map.of(
            "battery", "dumpsys battery",
            "screen", "dumpsys power",
            "storage", "df /data",
            "processes", "ps -A"
    );

    private final DeviceMonitorService monitorService;
    private final AdbService adbService;

    public DeviceController(DeviceMonitorService monitorService, AdbService adbService) {
        this.monitorService = monitorService;
        this.adbService = adbService;
    }

    @GetMapping
    public Collection<DeviceStatus> listDevices() {
        return monitorService.getAllDevices();
    }

    @GetMapping("/{serial}")
    public ResponseEntity<DeviceStatus> getDevice(@PathVariable String serial) {
        DeviceStatus status = monitorService.getDevice(serial);
        return status == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(status);
    }

    @PostMapping(value = "/{serial}/screenshot", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> screenshot(@PathVariable String serial) {
        requireOnline(serial);
        return ResponseEntity.ok(adbService.takeScreenshot(serial));
    }

    @PostMapping("/{serial}/diagnostics/{command}")
    public List<String> runDiagnostic(@PathVariable String serial, @PathVariable String command) {
        String resolved = ALLOWED_COMMANDS.get(command);
        if (resolved == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown command. Allowed: " + ALLOWED_COMMANDS.keySet());
        }
        requireOnline(serial);
        return adbService.runShellCommand(serial, resolved);
    }

    /**
     * The serial comes from the URL, so the caller can put anything there. Only devices the
     * monitor itself discovered through adb, and that are currently online, can be targeted.
     */
    private void requireOnline(String serial) {
        DeviceStatus status = monitorService.getDevice(serial);
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown device");
        }
        if (status.getConnectionState() != ConnectionState.ONLINE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Device is not online");
        }
    }

    /** adb failures are logged in full here; the client only gets a generic message. */
    @ExceptionHandler(AdbException.class)
    public ResponseEntity<Map<String, String>> handleAdbFailure(AdbException e) {
        log.warn("adb call failed: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "The device did not respond"));
    }
}
