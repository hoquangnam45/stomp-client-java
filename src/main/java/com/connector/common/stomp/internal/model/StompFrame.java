package com.connector.common.stomp.internal.model;

import com.connector.common.stomp.constant.StompFrameType;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class StompFrame
{
    private final String              body;
    private final StompFrameType      type;
    private final Map<String, String> headers;
    private final String              contentType;
    private final long                contentLength;

    public StompFrame(Map<String, String> headers, String body, StompFrameType type, String contentType)
    {
        this.contentType = contentType;
        this.body = body;
        if (body == null)
        {
            this.contentLength = 0;
        }
        else
        {
            this.contentLength = body.getBytes(StandardCharsets.UTF_8).length;
        }
        this.type = type;
        this.headers = headers;
    }

    private StompFrame(Map<String, String> headers, StompFrame rawMessage)
    {
        this.contentType = rawMessage.getContentType();
        if (rawMessage.getBody() == null)
        {
            this.body = null;
            this.contentLength = 0;
        }
        else
        {
            this.body = rawMessage.getBody();
            this.contentLength = rawMessage.getContentLength();
        }
        this.type = rawMessage.getType();
        this.headers = headers;
    }

    public static StompFrame populateHeaders(Map<String, String> headers, StompFrame message)
    {
        return new StompFrame(headers, message);
    }

    public String getContentType()
    {
        return contentType;
    }

    public long getContentLength()
    {
        return contentLength;
    }

    public String getBody()
    {
        return body;
    }

    public StompFrameType getType()
    {
        return type;
    }

    public Map<String, String> getHeaders()
    {
        return headers;
    }
}
