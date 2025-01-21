package com.connector.common.stomp.internal.model;

import com.connector.common.stomp.client.base.IStompSessionInfo;
import com.connector.common.stomp.constant.StompVersion;
import com.connector.common.stomp.internal.config.StompConnectConfigV10;

public class StompSessionInfoV10 implements IStompSessionInfo
{
    private final String                sessionId;
    private final StompVersion          version = StompVersion.STOMP_1_0;
    private final StompConnectConfigV10 connectConfig;
    private final StompFrame            connectRequest;
    private final StompFrame            connectedResponse;

    public StompSessionInfoV10(String sessionId, StompFrame connectRequest, StompFrame connectedResponse, StompConnectConfigV10 connectConfig)
    {
        this.sessionId = sessionId;
        this.connectRequest = connectRequest;
        this.connectedResponse = connectedResponse;
        this.connectConfig = connectConfig;
    }

    public StompConnectConfigV10 getConnectConfig()
    {
        return connectConfig;
    }

    @Override
    public String getSessionId()
    {
        return sessionId;
    }

    @Override
    public StompVersion getVersion()
    {
        return version;
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
