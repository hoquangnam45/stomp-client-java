package com.connector.common.websocket.internal.config;

public class WSDisconnectConfig
{
    private final boolean forceClose;
    private final Integer code;
    private final String  reason;

    public WSDisconnectConfig(boolean forceClose, Integer code, String reason)
    {
        this.forceClose = forceClose;
        this.code = code;
        this.reason = reason;
    }

    public boolean isForceClose()
    {
        return forceClose;
    }

    public Integer getCode()
    {
        return code;
    }

    public String getReason()
    {
        return reason;
    }

    @Override
    public String toString()
    {
        return "WSDisconnectConfig{" + "forceClose=" + forceClose + ", code=" + code + ", reason='" + reason + '\'' + '}';
    }
}
