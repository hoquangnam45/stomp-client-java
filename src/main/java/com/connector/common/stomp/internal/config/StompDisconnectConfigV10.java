package com.connector.common.stomp.internal.config;

public class StompDisconnectConfigV10
{
    private final boolean sendDisconnect;

    public StompDisconnectConfigV10(boolean sendDisconnect)
    {
        this.sendDisconnect = sendDisconnect;
    }

    public boolean isSendDisconnect()
    {
        return sendDisconnect;
    }
}
