package com.connector.common.stomp.client;

import com.connector.common.stomp.client.base.WSStompClientBaseV11;
import com.connector.common.stomp.internal.config.StompConnectConfigV11;
import com.connector.common.stomp.internal.config.StompDisconnectConfigV11;
import com.connector.common.websocket.client.WSClient;
import reactor.core.scheduler.Scheduler;

public class WSStompClientV11 extends WSStompClientBaseV11<StompConnectConfigV11<StompDisconnectConfigV11>, StompDisconnectConfigV11>
{
    public WSStompClientV11(String stompClientId, WSClient wsClient, Scheduler healthyCheckThread)
    {
        super(stompClientId, wsClient, healthyCheckThread);
    }
}
