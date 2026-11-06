package com.cropexpert.app.data.api

/**
 * Network configuration for the NASA Crop Expert backend.
 */
object NetworkConfig {
    /**
     * BASE_URL Configuration:
     * - Using "127.0.0.1" to support ADB Port Forwarding (adb reverse tcp:8000 tcp:8000)
     *   for both Emulator and physical devices.
     */
    private const val HOST_IP = "127.0.0.1"
    private const val PORT = "8000"
    
    const val BASE_URL = "http://$HOST_IP:$PORT/"
}
