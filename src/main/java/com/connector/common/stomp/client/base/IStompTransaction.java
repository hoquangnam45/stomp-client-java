package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompTransactionStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IStompTransaction<StompConnectConfig extends IStompConnectConfig, StompDisconnectConfig, StompSessionInfo extends IStompSessionInfo, TransportPayload> extends IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportPayload>, IStompRequestHandler, IStompDeliverable
{
    String begin(String receiptId) throws Throwable;

    String abort(String receiptId) throws Throwable;

    String commit(String receiptId) throws Throwable;

    String getTransactionId();

    StompTransactionStatus getTransactionStatus();

    Flux<StompTransactionStatus> transactionStatusStream();

    Mono<StompTransactionStatus> waitTransactionStatus(StompTransactionStatus status);
}

