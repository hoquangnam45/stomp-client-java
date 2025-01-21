package com.connector.common.stomp.client;

import com.connector.common.stomp.client.base.StompSubscriptionBaseV11;
import com.connector.common.stomp.constant.StompAckMode;
import com.connector.common.stomp.internal.config.StompConnectConfigV11;
import com.connector.common.stomp.internal.config.StompDisconnectConfigV11;
import com.connector.common.stomp.internal.model.StompSessionInfoV11;
import com.connector.common.websocket.internal.model.WSRequest;
import com.connector.common.websocket.internal.model.WSResponse;

public class WSStompSubscriptionV11 extends StompSubscriptionBaseV11<StompConnectConfigV11<StompDisconnectConfigV11>, StompDisconnectConfigV11, StompSessionInfoV11, WSRequest, WSResponse>
{
    public WSStompSubscriptionV11(String destination, String id, StompAckMode ackMode, WSStompClientV11 stompClient)
    {
        super(destination, id, ackMode, stompClient);
    }
}
