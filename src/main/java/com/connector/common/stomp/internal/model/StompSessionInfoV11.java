package com.connector.common.stomp.internal.model;

import com.connector.common.stomp.client.base.IStompSessionInfo;
import com.connector.common.stomp.constant.StompVersion;
import com.connector.common.stomp.internal.config.StompConnectConfigV11;

import java.time.Duration;
import java.util.Map;
import java.util.Map.Entry;

public class StompSessionInfoV11 implements IStompSessionInfo
{
    private final StompVersion                  version;
    private final String                        sessionId;
    private final String                        server;
    private final Map.Entry<Duration, Duration> clientHeartbeatInterval;
    private final Map.Entry<Duration, Duration> serverHeartbeatInterval;
    private final StompConnectConfigV11<?>      connectConfig;
    private final StompFrame                    connectRequest;
    private final StompFrame                    connectedResponse;

    public StompSessionInfoV11(StompVersion version, String sessionId, String server, Map.Entry<Duration, Duration> clientHeartbeatInterval, Map.Entry<Duration, Duration> serverHeartbeatInterval, StompConnectConfigV11<?> connectConfig, StompFrame connectRequest, StompFrame connectedResponse)
    {
        this.version = version;
        this.sessionId = sessionId;
        this.server = server;
        this.clientHeartbeatInterval = clientHeartbeatInterval;
        this.serverHeartbeatInterval = serverHeartbeatInterval;
        this.connectConfig = connectConfig;
        this.connectRequest = connectRequest;
        this.connectedResponse = connectedResponse;
    }

    public StompConnectConfigV11<?> getConnectConfig()
    {
        return connectConfig;
    }

    @Override
    public StompVersion getVersion()
    {
        return version;
    }

    @Override
    public String getSessionId()
    {
        return sessionId;
    }

    public String getServer()
    {
        return server;
    }

    public Entry<Duration, Duration> getClientHeartbeatInterval()
    {
        return clientHeartbeatInterval;
    }

    public Entry<Duration, Duration> getServerHeartbeatInterval()
    {
        return serverHeartbeatInterval;
    }

    public StompFrame getConnectRequest()
    {
        return connectRequest;
    }

    public StompFrame getConnectedResponse()
    {
        return connectedResponse;
    }
}
