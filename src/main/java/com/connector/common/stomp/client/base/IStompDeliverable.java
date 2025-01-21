package com.connector.common.stomp.client.base;

import com.connector.common.stomp.internal.model.StompFrame;
import reactor.core.publisher.Flux;

public interface IStompDeliverable
{
    void connectDeliverMessage() throws Throwable;

    void disconnectDeliverMessage() throws Throwable;

    Flux<StompFrame> deliverMessageStream();
}
