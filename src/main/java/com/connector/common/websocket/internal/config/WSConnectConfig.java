package com.connector.common.websocket.internal.config;

import com.connector.common.IAuthenticationHandler;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class WSConnectConfig
{
    private final String                               address;
    private final IAuthenticationHandler<OkHttpClient> okHttpClientCustomizer;
    private final IAuthenticationHandler<Request>      requestCustomizer;

    public WSConnectConfig(String address, IAuthenticationHandler<OkHttpClient> okHttpClientCustomizer, IAuthenticationHandler<Request> requestCustomizer)
    {
        this.address = address;
        this.okHttpClientCustomizer = okHttpClientCustomizer;
        this.requestCustomizer = requestCustomizer;
    }

    public String getAddress()
    {
        return address;
    }

    public IAuthenticationHandler<OkHttpClient> getOkHttpClientCustomizer()
    {
        return okHttpClientCustomizer;
    }

    public IAuthenticationHandler<Request> getRequestCustomizer()
    {
        return requestCustomizer;
    }

    @Override
    public String toString()
    {
        return "WSConnectConfig{" + "address='" + address + '\'' + '}';
    }
}