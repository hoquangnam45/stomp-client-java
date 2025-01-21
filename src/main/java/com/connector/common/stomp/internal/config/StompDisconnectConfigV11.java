package com.connector.common.stomp.internal.config;

import java.time.Duration;

public class StompDisconnectConfigV11
{
    private final boolean  sendDisconnect;
    private final String   receipt;
    private final Duration waitReceiptDuration;

    public StompDisconnectConfigV11(boolean sendDisconnect, String receipt, Duration waitReceiptDuration)
    {
        this.sendDisconnect = sendDisconnect;
        this.receipt = receipt;
        this.waitReceiptDuration = waitReceiptDuration;
    }

    public Duration getWaitReceiptDuration()
    {
        return waitReceiptDuration;
    }

    public boolean isSendDisconnect()
    {
        return sendDisconnect;
    }

    public String getReceipt()
    {
        return receipt;
    }
}
