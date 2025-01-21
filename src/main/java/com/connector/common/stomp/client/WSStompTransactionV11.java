package com.connector.common.stomp.client;

import com.connector.common.stomp.client.base.StompClientTransaction;
import com.connector.common.stomp.internal.config.StompConnectConfigV11;
import com.connector.common.stomp.internal.model.StompSessionInfoV11;
import com.connector.common.websocket.internal.model.WSRawMessage;

public class WSStompTransactionV11 extends StompClientTransaction<StompConnectConfigV11<Void>, Void, StompSessionInfoV11, WSRawMessage>
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
