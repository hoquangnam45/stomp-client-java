package com.connector.common.stomp.client;

import com.connector.common.stomp.client.base.StompSubscriptionBaseV10;
import com.connector.common.stomp.constant.StompAckMode;
import com.connector.common.stomp.internal.config.StompConnectConfigV10;
import com.connector.common.stomp.internal.model.StompSessionInfoV10;
import com.connector.common.websocket.internal.model.WSRawMessage;

public class WSStompSubscriptionV10 extends StompSubscriptionBaseV10<StompConnectConfigV10, Void, StompSessionInfoV10, WSRawMessage>
{
    public WSStompSubscriptionV10(String destination, String id, StompAckMode ackMode, WSStompClientV10 stompClient)
    {
        super(destination, id, ackMode, stompClient);
    }
}
