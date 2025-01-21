package com.connector.common.stomp.client;

import com.connector.common.stomp.client.base.StompClientTransaction;
import com.connector.common.stomp.internal.config.StompConnectConfigV10;
import com.connector.common.stomp.internal.model.StompSessionInfoV10;
import com.connector.common.websocket.internal.model.WSRawMessage;

public class WSStompTransactionV10 extends StompClientTransaction<StompConnectConfigV10, Void, StompSessionInfoV10, WSRawMessage>
{
    public WSStompTransactionV10(WSStompClientV10 delegatee, String transactionId)
    {
        super(transactionId, delegatee);
    }

    public WSStompTransactionV10(WSStompSubscriptionV10 delegatee, String transactionId)
    {
        super(transactionId, delegatee);
    }
}
