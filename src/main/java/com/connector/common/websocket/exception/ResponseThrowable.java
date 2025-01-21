package com.connector.common.websocket.exception;

import com.connector.common.websocket.constant.WSLifecycle;
import okhttp3.Response;

public class ResponseThrowable extends Throwable
{
    private final Response    response;
    private final WSLifecycle lifecycle;

    public ResponseThrowable(Response response, Throwable t, WSLifecycle lifecycle)
    {
        super(t);
        this.response = response;
        this.lifecycle = lifecycle;
    }

    public Response getResponse()
    {
        return response;
    }

    public WSLifecycle getLifecycle()
    {
        return lifecycle;
    }
}
