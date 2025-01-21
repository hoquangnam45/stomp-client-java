package com.connector.common.websocket.client.base;

import com.connector.common.IAuthenticationHandler;
import com.connector.common.websocket.constant.WSStatus;
import com.connector.common.websocket.internal.config.WSConnectConfig;
import com.connector.common.websocket.internal.config.WSDisconnectConfig;
import com.connector.common.websocket.internal.model.WSRequest;
import com.connector.common.websocket.internal.model.WSResponse;
import reactor.core.publisher.Flux;

public interface IWSClient<ConnectConfig extends WSConnectConfig, DisconnectConfig extends WSDisconnectConfig>
{
    String getClientId();

    void sendMessage(WSRequest request) throws Throwable;

    void connect(ConnectConfig wsConnectConfig, IAuthenticationHandler<WSRequest> authenticateHandler) throws Throwable;

    void disconnect(DisconnectConfig wsDisconnectConfig) throws Throwable;

    Flux<WSResponse> responseStream();

    WSStatus getSocketStatus();

    Flux<WSStatus> socketStatusStream();
}
