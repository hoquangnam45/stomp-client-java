package com.connector.common.stomp.client;

import com.connector.common.stomp.client.base.StompSubscriptionBaseV11;
import com.connector.common.stomp.constant.StompAckMode;
import com.connector.common.stomp.internal.config.StompConnectConfigV11;
import com.connector.common.stomp.internal.model.StompSessionInfoV11;
import com.connector.common.websocket.internal.model.WSRawMessage;

public class WSStompSubscriptionV11 extends StompSubscriptionBaseV11<StompConnectConfigV11<Void>, Void, StompSessionInfoV11, WSRawMessage>
{
    public WSStompSubscriptionV11(String destination, String id, StompAckMode ackMode, WSStompClientV11 stompClient)
    {
        super(destination, id, ackMode, stompClient);
    }
}
