package com.connector.common.websocket.client;

import com.connector.common.websocket.client.base.WSClientBase;
import com.connector.common.websocket.internal.config.WSConnectConfig;
import com.connector.common.websocket.internal.config.WSDisconnectConfig;

public class WSClient extends WSClientBase<WSConnectConfig, WSDisconnectConfig>
{
    public WSClient(String clientId)
    {
        super(clientId);
    }
}
