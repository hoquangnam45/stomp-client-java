package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompAckMode;
import com.connector.common.stomp.constant.StompSubscriptionStatus;
import com.connector.common.stomp.internal.model.StompFrame;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IStompSubscription<StompConnectConfig extends IStompConnectConfig, StompDisconnectConfig, StompSessionInfo extends IStompSessionInfo, TransportPayload> extends IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportPayload>, IStompDeliverable, IStompRequestHandler
{
    String getDestination();

    String getSubscriptionId();

    StompAckMode getAckMode();

    void registerResponseHandler(IStompResponseHandler responseHandler);

    boolean ackMessage(StompFrame message) throws Throwable;

    String subscribe(String receiptId) throws Throwable;

    String unsubscribe(String receiptId) throws Throwable;

    StompSubscriptionStatus getSubscriptionStatus();

    Flux<StompSubscriptionStatus> subscriptionStatusStream();

    Mono<StompSubscriptionStatus> waitSubscriptionStatus(StompSubscriptionStatus status);
}
