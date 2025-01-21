package com.connector.common.websocket.internal.model;

import com.connector.common.websocket.constant.WSLifecycle;
import okhttp3.WebSocket;

public class WSResponse
{
    private final WebSocket    webSocket;
    private final WSRawMessage body;
    private final WSLifecycle  lifecycle;

    public WSResponse(WSRawMessage body, WebSocket webSocket, WSLifecycle lifecycle)
    {
        this.webSocket = webSocket;
        this.lifecycle = lifecycle;
        this.body = body;
    }

    public WSLifecycle getLifecycle()
    {
        return lifecycle;
    }

    public WebSocket getWebSocket()
    {
        return webSocket;
    }

    public WSRawMessage getBody()
    {
        return body;
    }
}
