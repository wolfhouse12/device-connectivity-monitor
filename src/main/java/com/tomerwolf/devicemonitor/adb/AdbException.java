package com.tomerwolf.devicemonitor.adb;

public class AdbException extends RuntimeException {
    public AdbException(String message, Throwable cause) {
        super(message, cause);
    }
}
