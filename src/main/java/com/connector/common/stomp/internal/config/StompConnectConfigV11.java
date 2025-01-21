package com.connector.common.stomp.internal.config;

import com.connector.common.stomp.client.base.IStompConnectConfig;
import com.connector.common.stomp.constant.StompVersion;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

public class StompConnectConfigV11<DisconnectConfig> implements IStompConnectConfig
{
    private final String                   host;
    private final List<StompVersion>       acceptedVersions;
    private final DisconnectConfig         disconnectConfig;
    private final String                   login;
    private final String                   passcode;
    private final Duration                 heartbeatClient;
    private final Duration                 heartbeatServer;
    private final ScheduledExecutorService heartbeatScheduledExecutorService;

    public StompConnectConfigV11(DisconnectConfig disconnectConfig, String host, List<StompVersion> acceptedVersions, String login, String passcode, Duration heartbeatClient, Duration heartbeatServer, ScheduledExecutorService heartbeatScheduledExecutorService)
    {
        this.disconnectConfig = disconnectConfig;
        this.host = host;
        this.acceptedVersions = acceptedVersions;
        this.login = login;
        this.passcode = passcode;
        this.heartbeatClient = heartbeatClient;
        this.heartbeatServer = heartbeatServer;
        this.heartbeatScheduledExecutorService = heartbeatScheduledExecutorService;
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

    public ScheduledExecutorService getHeartbeatScheduledExecutorService()
    {
        return heartbeatScheduledExecutorService;
    }

    public List<StompVersion> getAcceptedVersions()
    {
        return acceptedVersions;
    }

    public String getHost()
    {
        return host;
    }

    public Duration getHeartbeatClient()
    {
        return heartbeatClient;
    }

    public Duration getHeartbeatServer()
    {
        return heartbeatServer;
    }

    public DisconnectConfig getDisconnectConfig()
    {
        return disconnectConfig;
    }
}
