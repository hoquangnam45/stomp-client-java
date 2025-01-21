package com.connector.common.stomp.internal.config;

import com.connector.common.stomp.client.base.IStompConnectConfig;

import java.time.Duration;

public class StompConnectConfigV10 implements IStompConnectConfig
{
    private final Duration connectTimeoutDuration;
    private final String   login;
    private final String   passcode;

    public StompConnectConfigV10(Duration connectTimeoutDuration, String login, String passcode)
    {
        this.connectTimeoutDuration = connectTimeoutDuration;
        this.login = login;
        this.passcode = passcode;
    }

    @Override
    public String getLogin()
    {
        return login;
    }

    @Override
    public String getPasscode()
    {
        return passcode;
    }

    @Override
    public Duration getConnectTimeoutDuration()
    {
        return connectTimeoutDuration;
    }
}
