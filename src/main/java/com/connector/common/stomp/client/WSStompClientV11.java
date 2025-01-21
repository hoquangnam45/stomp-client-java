package com.connector.common.stomp.client;

import com.connector.common.stomp.client.base.WSStompClientBaseV11;
import com.connector.common.stomp.internal.config.StompConnectConfigV11;
import com.connector.common.websocket.client.WSClient;

public class WSStompClientV11 extends WSStompClientBaseV11<StompConnectConfigV11<Void>, Void>
{
    public WSStompClientV11(String stompClientId, WSClient wsClient)
    {
        super(stompClientId, wsClient);
    }
}
