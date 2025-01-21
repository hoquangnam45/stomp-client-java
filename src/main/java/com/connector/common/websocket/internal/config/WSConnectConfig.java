package com.connector.common.websocket.internal.config;

import java.time.Duration;

public class WSConnectConfig
{
    private final String   address;
    private final Duration connectTimeoutDuration;
    private final Duration readTimeoutDuration;
    private final Duration writeTimeoutDuration;

    public WSConnectConfig(String address, Duration connectTimeoutDuration, Duration readTimeoutDuration, Duration writeTimeoutDuration)
    {
        this.address = address;
        this.connectTimeoutDuration = connectTimeoutDuration;
        this.readTimeoutDuration = readTimeoutDuration;
        this.writeTimeoutDuration = writeTimeoutDuration;
    }

    public Duration getReadTimeoutDuration()
    {
        return readTimeoutDuration;
    }

    public Duration getWriteTimeoutDuration()
    {
        return writeTimeoutDuration;
    }

    public Duration getConnectTimeoutDuration()
    {
        return connectTimeoutDuration;
    }

    public String getAddress()
    {
        return address;
    }
}