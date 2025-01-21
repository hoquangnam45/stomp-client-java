package com.connector.common.stomp.client.base;

import com.connector.common.stomp.internal.model.StompFrame;

public interface IStompRequestHandler
{
    StompFrame populateRequest(StompFrame msg);
}
