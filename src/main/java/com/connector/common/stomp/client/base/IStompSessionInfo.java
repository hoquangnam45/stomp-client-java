package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompVersion;

public interface IStompSessionInfo
{
    StompVersion getVersion();

    String getSessionId();
}
