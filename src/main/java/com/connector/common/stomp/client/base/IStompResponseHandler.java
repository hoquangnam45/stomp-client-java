package com.connector.common.stomp.client.base;

import com.connector.common.stomp.internal.model.StompFrame;

@FunctionalInterface
public interface IStompResponseHandler
{
    Throwable handleStompResponse(StompFrame frame) throws Throwable;
}
