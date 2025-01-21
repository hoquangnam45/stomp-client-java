package com.connector.common.stomp.internal.exception;

import com.connector.common.stomp.internal.model.StompFrame;

import java.text.MessageFormat;

public class StompErrorFrame extends Exception
{
    private final StompFrame errorFrame;

    public StompErrorFrame(StompFrame errorFrame, String message)
    {
        super(MessageFormat.format("{0}: [headers = {1}, body = {2}]", message, errorFrame.getHeaders(), errorFrame.getBody()));
        this.errorFrame = errorFrame;
    }

    public StompFrame getErrorFrame()
    {
        return errorFrame;
    }
}
