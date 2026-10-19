package com.tomerwolf.devicemonitor.web;

import com.tomerwolf.devicemonitor.adb.AdbService;
import com.tomerwolf.devicemonitor.model.DeviceStatus;
import com.tomerwolf.devicemonitor.service.DeviceMonitorService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    /**
     * Explicit allowlist of safe, read-only diagnostic commands exposed over the API. This
     * endpoint intentionally does NOT accept arbitrary shell commands from callers -- only these
     * pre-approved ones can be run, to avoid turning an HTTP endpoint into unrestricted remote
     * shell execution on the connected device.
     */
    private static final Map<String, String> ALLOWED_COMMANDS = Map.of(
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
    public ResponseEntity<byte[]> screenshot(@PathVariable String serial) throws IOException, InterruptedException {
        byte[] png = adbService.takeScreenshot(serial);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + serial + ".png\"")
                .body(png);
    }

    @PostMapping("/{serial}/diagnostics/{command}")
    public ResponseEntity<List<String>> runDiagnostic(@PathVariable String serial, @PathVariable String command) {
        String resolved = ALLOWED_COMMANDS.get(command);
        if (resolved == null) {
            return ResponseEntity.badRequest().body(List.of("Unknown command. Allowed: " + ALLOWED_COMMANDS.keySet()));
        }
        return ResponseEntity.ok(adbService.runShellCommand(serial, resolved));
    }
}
