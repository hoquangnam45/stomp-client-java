package com.connector.common.stomp.constant;

import java.util.HashMap;
import java.util.Map;

public enum StompFrameType
{
    HEARTBEAT(true), CONNECT(false), SEND(false), SUBSCRIBE(false), UNSUBSCRIBE(false), BEGIN(false), COMMIT(false), ABORT(false), ACK(false), NACK(false), DISCONNECT(false), STOMP(false), CONNECTED(false), MESSAGE(true), RECEIPT(true), ERROR(true);

    private final boolean fromServer;

    StompFrameType(boolean fromServer)
    {
        this.fromServer = fromServer;
    }

    public boolean fromServer()
    {
        return fromServer;
    }

    private static Map<String, StompFrameType> cachedFrameTypes;

    public static StompFrameType parse(String v)
    {
        if (v == null)
        {
            return null;
        }
        if (cachedFrameTypes != null)
        {
            return cachedFrameTypes.get(v);
        }
        synchronized (StompFrameType.class)
        {
            if (cachedFrameTypes == null)
            {
                cachedFrameTypes = new HashMap<>();
                for (StompFrameType t : values())
                {
                    if (t != HEARTBEAT)
                    {
                        cachedFrameTypes.put(t.name(), t);
                    }
                }
            }
            return cachedFrameTypes.get(v);
        }
    }
}

