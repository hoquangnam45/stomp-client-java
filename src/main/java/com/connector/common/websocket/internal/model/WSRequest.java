package com.connector.common.websocket.internal.model;

import okhttp3.Request;

public class WSRequest
{
    private final Request      request;
    private final WSRawMessage message;

    public static WSRequest message(WSRawMessage message)
    {
        return new WSRequest(null, message);
    }

    public static WSRequest request(Request request)
    {
        return new WSRequest(request, null);
    }

    private WSRequest(Request request, WSRawMessage message)
    {
        this.request = request;
        this.message = message;
    }

    public Request getRequest()
    {
        return request;
    }

    public WSRawMessage getMessage()
    {
        return message;
    }
}
