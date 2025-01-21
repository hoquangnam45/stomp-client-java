package com.connector.common.stomp.client;

import com.connector.common.stomp.client.base.StompClientTransaction;
import com.connector.common.stomp.internal.config.StompConnectConfigV11;
import com.connector.common.stomp.internal.config.StompDisconnectConfigV11;
import com.connector.common.stomp.internal.model.StompSessionInfoV11;
import com.connector.common.websocket.internal.model.WSRequest;
import com.connector.common.websocket.internal.model.WSResponse;

public class WSStompTransactionV11 extends StompClientTransaction<StompConnectConfigV11<StompDisconnectConfigV11>, StompDisconnectConfigV11, StompSessionInfoV11, WSRequest, WSResponse>
{
    public WSStompTransactionV11(WSStompClientV11 delegatee, String transactionId)
    {
        super(transactionId, delegatee);
    }

    public WSStompTransactionV11(WSStompSubscriptionV11 delegatee, String transactionId)
    {
        super(transactionId, delegatee);
    }
}
