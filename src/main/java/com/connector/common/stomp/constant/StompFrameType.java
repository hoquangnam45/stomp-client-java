package com.connector.common.stomp.constant;

public enum StompFrameType
{
    CONNECT(false), SEND(false), SUBSCRIBE(false), UNSUBSCRIBE(false), BEGIN(false), COMMIT(false), ABORT(false), ACK(false), NACK(false), DISCONNECT(false), STOMP(false), CONNECTED(false), MESSAGE(true), RECEIPT(true), ERROR(true);

    private final boolean fromServer;

    StompFrameType(boolean fromServer)
    {
        this.fromServer = fromServer;
    }

    public boolean fromServer()
    {
        return fromServer;
    }
}

