package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompAckMode;
import com.connector.common.stomp.constant.StompFrameType;
import com.connector.common.stomp.constant.StompHeaders;
import com.connector.common.stomp.internal.config.StompConnectConfigV10;
import com.connector.common.stomp.internal.model.StompFrame;
import com.connector.common.stomp.internal.model.StompSessionInfoV10;

import java.util.LinkedHashMap;
import java.util.Map;

public class StompSubscriptionBaseV10<StompConnectConfig extends StompConnectConfigV10, StompDisconnectConfig, StompSessionInfo extends StompSessionInfoV10, TransportPayload> extends StompSubscriptionBase<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportPayload>
{
    public StompSubscriptionBaseV10(String destination, String id, StompAckMode ackMode, IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportPayload> stompClient)
    {
        super(stompClient, destination, id, ackMode);
    }

    @Override
    public boolean ackMessage(StompFrame msg) throws Throwable
    {
        Map<String, String> msgHeaders = msg.getHeaders();
        Throwable handledResult = null;
        if (getResponseAckHandler() != null)
        {
            try
            {
                handledResult = getResponseAckHandler().handleStompResponse(msg);
            }
            catch (Throwable e)
            {
                handledResult = e;
            }
        }

        if (getAckMode() == StompAckMode.AUTO)
        {
            return true;
        }

        if (msgHeaders.get(StompHeaders.MESSAGE_ID) != null)
        {
            if (handledResult != null)
            {
                Map<String, String> ackHeader = new LinkedHashMap<>();
                ackHeader.put(StompHeaders.MESSAGE_ID, msgHeaders.get(StompHeaders.MESSAGE_ID));
                sendStompMessage(new StompFrame(ackHeader, null, StompFrameType.ACK, null), null);
                return true;
            }
            return false;
        }
        return true;
    }
}
