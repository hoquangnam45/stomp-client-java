package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompAckMode;
import com.connector.common.stomp.constant.StompSubscriptionStatus;
import reactor.core.publisher.Flux;

public interface IStompSubscription<StompConnectConfig extends IStompConnectConfig, StompDisconnectConfig, StompSessionInfo extends IStompSessionInfo, TransportReq, TransportResp> extends IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportReq, TransportResp>, IStompDeliverable, IStompRequestHandler
{
    String getDestination();

    String getSubscriptionId();

    StompAckMode getAckMode();

    void subscribe() throws Throwable;

    void unsubscribe() throws Throwable;

    StompSubscriptionStatus getSubscriptionStatus();

    Flux<StompSubscriptionStatus> subscriptionStatusStream();
}
