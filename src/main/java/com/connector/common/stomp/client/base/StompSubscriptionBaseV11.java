package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompAckMode;
import com.connector.common.stomp.constant.StompFrameType;
import com.connector.common.stomp.constant.StompHeaders;
import com.connector.common.stomp.internal.model.StompFrame;
import com.connector.common.stomp.internal.model.StompSessionInfoV11;

import java.util.LinkedHashMap;
import java.util.Map;

public class StompSubscriptionBaseV11<StompConnectConfig extends IStompConnectConfig, StompDisconnectConfig, StompSessionInfo extends StompSessionInfoV11, TransportPayload> extends StompSubscriptionBase<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportPayload>
{
    public StompSubscriptionBaseV11(String destination, String id, StompAckMode ackMode, IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportPayload> stompClient)
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

        if (msgHeaders.get(StompHeaders.ACK) != null)
        {
            Map<String, String> ackHeader = new LinkedHashMap<>();
            ackHeader.put(StompHeaders.ID, msgHeaders.get(StompHeaders.ACK));

            StompFrameType frameType;
            boolean result;
            if (handledResult != null)
            {
                frameType = StompFrameType.NACK;
                result = false;
            }
            else
            {
                frameType = StompFrameType.ACK;
                result = true;
            }
            sendStompMessage(new StompFrame(ackHeader, null, frameType, null), null);
            return result;
        }
        return true;
    }
}
