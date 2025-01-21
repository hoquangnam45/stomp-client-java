package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompTransactionStatus;
import reactor.core.publisher.Flux;

public interface IStompTransaction<StompConnectConfig extends IStompConnectConfig, StompDisconnectConfig, StompSessionInfo extends IStompSessionInfo, TransportReq, TransportResp> extends IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportReq, TransportResp>, IStompRequestHandler, IStompDeliverable
{
    void begin() throws Throwable;

    void abort() throws Throwable;

    void commit() throws Throwable;

    String getTransactionId();

    StompTransactionStatus getTransactionStatus();

    Flux<StompTransactionStatus> transactionStatusStream();
}

