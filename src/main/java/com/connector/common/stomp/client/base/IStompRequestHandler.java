package com.connector.common.stomp.client.base;

import com.connector.common.stomp.internal.model.StompFrame;

@FunctionalInterface
public interface IStompRequestHandler
{
    StompFrame populateRequest(StompFrame msg) throws Throwable;
}
