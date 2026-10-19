package com.tomerwolf.devicemonitor.adb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thin wrapper around the {@code adb} command-line tool. There is no maintained pure-Java ADB
 * client for full device control, so -- same as most real-world Android tooling -- this shells
 * out to the platform-tools binary and parses its text output.
 */
@Service
public class AdbService {

    @Value("${adb.path:adb}")
    private String adbPath;

    private static final Pattern DEVICE_LINE = Pattern.compile("^(\\S+)\\s+(device|offline|unauthorized)(.*)$");
    private static final Pattern MODEL_ATTR = Pattern.compile("model:(\\S+)");

    public record RawDevice(String serial, String state, String model) {
    }

    public List<RawDevice> listDevices() {
        List<RawDevice> devices = new ArrayList<>();
        for (String line : run("devices", "-l")) {
            Matcher m = DEVICE_LINE.matcher(line.trim());
            if (m.matches()) {
                String serial = m.group(1);
                String state = m.group(2);
                Matcher modelMatcher = MODEL_ATTR.matcher(m.group(3));
                String model = modelMatcher.find() ? modelMatcher.group(1) : "unknown";
                devices.add(new RawDevice(serial, state, model));
            }
        }
        return devices;
    }

    public Integer getBatteryLevel(String serial) {
        for (String line : run("-s", serial, "shell", "dumpsys", "battery")) {
            line = line.trim();
            if (line.startsWith("level:")) {
                try {
                    return Integer.parseInt(line.substring("level:".length()).trim());
                } catch (NumberFormatException ignored) {
                    // fall through, keep scanning
                }
            }
        }
        return null;
    }

    public String getScreenState(String serial) {
        for (String line : run("-s", serial, "shell", "dumpsys", "power")) {
            line = line.trim();
            if (line.startsWith("mWakefulness=")) {
                return line.substring("mWakefulness=".length()).trim();
            }
        }
        return "unknown";
    }

    public byte[] takeScreenshot(String serial) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(adbPath, "-s", serial, "exec-out", "screencap", "-p").start();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = process.getInputStream()) {
            in.transferTo(buffer);
        }
        process.waitFor(15, TimeUnit.SECONDS);
        return buffer.toByteArray();
    }

    /**
     * Runs a shell command on the device and returns its output. This method itself does not
     * validate {@code command} -- callers (see DeviceController) are responsible for only ever
     * passing pre-approved, allowlisted commands, never unvalidated caller input.
     */
    public List<String> runShellCommand(String serial, String command) {
        return run("-s", serial, "shell", command);
    }

    private List<String> run(String... args) {
        List<String> command = new ArrayList<>();
        command.add(adbPath);
        Collections.addAll(command, args);
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            List<String> output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes()).lines().toList();
            }
            process.waitFor(10, TimeUnit.SECONDS);
            return output;
        } catch (IOException | InterruptedException e) {
            throw new AdbException("Failed to run adb command: " + String.join(" ", args), e);
        }
    }
}
