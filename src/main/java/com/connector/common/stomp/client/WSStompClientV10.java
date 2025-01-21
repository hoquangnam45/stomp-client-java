package com.connector.common.stomp.client;

import com.connector.common.stomp.client.base.WSStompClientBaseV10;
import com.connector.common.stomp.internal.config.StompConnectConfigV10;
import com.connector.common.websocket.client.WSClient;

public class WSStompClientV10 extends WSStompClientBaseV10<StompConnectConfigV10, Void>
{
    public WSStompClientV10(String stompClientId, WSClient wsClient)
    {
        super(stompClientId, wsClient);
    }
}
