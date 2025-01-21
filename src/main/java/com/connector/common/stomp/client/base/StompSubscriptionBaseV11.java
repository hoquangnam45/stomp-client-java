package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompAckMode;
import com.connector.common.stomp.constant.StompFrameType;
import com.connector.common.stomp.constant.StompHeaders;
import com.connector.common.stomp.internal.model.StompFrame;
import com.connector.common.stomp.internal.model.StompSessionInfoV11;

import java.util.LinkedHashMap;
import java.util.Map;

public class StompSubscriptionBaseV11<StompConnectConfig extends IStompConnectConfig, StompDisconnectConfig, StompSessionInfo extends StompSessionInfoV11, TransportReq, TransportResp> extends StompSubscriptionBase<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportReq, TransportResp>
{
    public StompSubscriptionBaseV11(String destination, String id, StompAckMode ackMode, IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportReq, TransportResp> stompClient)
    {
        super(stompClient, destination, id, ackMode);
    }

    @Override
    public boolean deliverMessage(StompFrame msg) throws Throwable
    {
        Map<String, String> msgHeaders = msg.getHeaders();
        try
        {
            if (getAckMode() == StompAckMode.AUTO)
            {
                return true;
            }

            if (msgHeaders.get(StompHeaders.ACK) != null)
            {
                Map<String, String> ackHeader = new LinkedHashMap<>();
                ackHeader.put(StompHeaders.ID, msgHeaders.get(StompHeaders.ACK));
                sendStompMessage(new StompFrame(ackHeader, null, StompFrameType.ACK, null));
            }
            return true;
        }
        catch (Throwable e)
        {
            logger.error("Message [message-id = {}] received by subscription [clientId = {}] failed. Reason: {}", msgHeaders.get(StompHeaders.MESSAGE_ID), getClientId(), e.getMessage(), e);

            if (msgHeaders.get(StompHeaders.ACK) != null)
            {
                Map<String, String> nackHeader = new LinkedHashMap<>();
                sendStompMessage(new StompFrame(nackHeader, null, StompFrameType.NACK, null));
                nackHeader.put(StompHeaders.ID, msgHeaders.get(StompHeaders.ACK));
            }
            return false;
        }
    }
}
