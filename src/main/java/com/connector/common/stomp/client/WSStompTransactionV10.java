package com.connector.common.stomp.client;

import com.connector.common.stomp.client.base.StompClientTransaction;
import com.connector.common.stomp.internal.config.StompConnectConfigV10;
import com.connector.common.stomp.internal.config.StompDisconnectConfigV10;
import com.connector.common.stomp.internal.model.StompSessionInfoV10;
import com.connector.common.websocket.internal.model.WSRequest;
import com.connector.common.websocket.internal.model.WSResponse;

public class WSStompTransactionV10 extends StompClientTransaction<StompConnectConfigV10, StompDisconnectConfigV10, StompSessionInfoV10, WSRequest, WSResponse>
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
