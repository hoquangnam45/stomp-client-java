package com.connector.common.stomp.client;

import com.connector.common.stomp.client.base.StompSubscriptionBaseV10;
import com.connector.common.stomp.constant.StompAckMode;
import com.connector.common.stomp.internal.config.StompConnectConfigV10;
import com.connector.common.stomp.internal.config.StompDisconnectConfigV10;
import com.connector.common.stomp.internal.model.StompSessionInfoV10;
import com.connector.common.websocket.internal.model.WSRequest;
import com.connector.common.websocket.internal.model.WSResponse;

public class WSStompSubscriptionV10 extends StompSubscriptionBaseV10<StompConnectConfigV10, StompDisconnectConfigV10, StompSessionInfoV10, WSRequest, WSResponse>
{
    public WSStompSubscriptionV10(String destination, String id, StompAckMode ackMode, WSStompClientV10 stompClient)
    {
        super(destination, id, ackMode, stompClient);
    }
}
