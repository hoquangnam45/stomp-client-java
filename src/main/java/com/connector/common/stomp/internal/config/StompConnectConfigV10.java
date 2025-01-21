package com.connector.common.stomp.internal.config;

import com.connector.common.stomp.client.base.IStompConnectConfig;

public class StompConnectConfigV10 implements IStompConnectConfig
{
    private final String login;
    private final String passcode;

    public StompConnectConfigV10(String login, String passcode)
    {
        this.login = login;
        this.passcode = passcode;
    }

    @Override
    public String getLogin()
    {
        return login;
    }

    @Override
    public String getPasscode()
    {
        return passcode;
    }
}
