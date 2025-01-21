package com.connector.common.stomp.client.base;

import java.time.Duration;

public interface IStompConnectConfig
{
    String getLogin();

    String getPasscode();

    Duration getConnectTimeoutDuration();
}
