package com.connector.common.websocket.internal.model;

import com.connector.common.websocket.constant.WSRawMessageType;

public class WSRawMessage
{
    private final WSRawMessageType type;
    private final byte[]           binaryData;
    private final String           stringData;

    public WSRawMessage(WSRawMessageType type, byte[] binaryData, String stringData)
    {
        this.type = type;
        this.binaryData = binaryData;
        this.stringData = stringData;
    }

    public static WSRawMessage text(String text)
    {
        return new WSRawMessage(WSRawMessageType.TEXT, null, text);
    }

    public static WSRawMessage binary(byte[] data)
    {
        return new WSRawMessage(WSRawMessageType.BINARY, data, null);
    }

    public WSRawMessageType getType()
    {
        return type;
    }

    public byte[] getBinaryData()
    {
        if (type != WSRawMessageType.BINARY)
        {
            throw new IllegalStateException("This WSRawMessage is not of type " + WSRawMessageType.BINARY);
        }
        return binaryData;
    }

    public String getStringData()
    {
        if (type != WSRawMessageType.TEXT)
        {
            throw new IllegalStateException("This WSRawMessage is not of type " + WSRawMessageType.TEXT);
        }
        return stringData;
    }
}
