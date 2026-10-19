# Android Device Connectivity Monitor

A Java / Spring Boot service that monitors Android devices connected over `adb`, tracks their connection state, and exposes it through a REST API with a small web dashboard.

## What it does

- **Scheduled polling:** every 15 seconds (configurable) it asks `adb` which devices are connected, and records each device's state (`ONLINE`, `OFFLINE`, `UNAUTHORIZED`), model, battery level and screen state.
- **Alerts on transitions only:** it logs a line when a device's state actually changes (or a device is unplugged), not on every poll, which avoids noisy logs.
- **Thread-safe state:** device state lives in a `ConcurrentHashMap` shared between the background scheduler and the HTTP request threads.
- **REST API + dashboard:** a static page (`/`) shows the devices; the API serves the same data.
- **Safe diagnostics:** the diagnostics endpoint runs only an explicit allowlist of read-only commands, never arbitrary shell input.
- **The server decides which device is targeted:** screenshot and diagnostics requests only work for a serial the monitor discovered itself and that is currently online. Unknown serials get `404`, offline ones `409`.
- **Local only:** the API has no login and can read a phone's screen, so it listens on `127.0.0.1` only (`server.address` in `application.properties`).

## Tests

`./mvnw test` runs unit tests for the state tracking (online, unauthorized, unplugged, adb failure) and for the controller's allowlist and device checks. They use a mocked `AdbService`, so no phone is needed.

## API

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/devices` | All known devices and their status |
| `GET` | `/api/devices/{serial}` | One device |
| `POST` | `/api/devices/{serial}/screenshot` | Capture a screenshot (PNG) |
| `POST` | `/api/devices/{serial}/diagnostics/{command}` | Run an allowlisted command: `battery`, `screen`, `storage`, `processes` |

## Run it

Requirements: Java 17+, and the Android platform tools (`adb`).

```
./mvnw spring-boot:run        # macOS / Linux / Git Bash
.\mvnw.cmd spring-boot:run    # Windows PowerShell or cmd
```

If `adb` isn't at the default path, pass it in: `./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dadb.path=/path/to/adb"`.
The poll interval is set in `src/main/resources/application.properties` (`monitor.poll-interval-ms`).

Then open http://localhost:8080.

## Tech

Java 17, Spring Boot 4 (Web MVC, scheduling), Maven.
