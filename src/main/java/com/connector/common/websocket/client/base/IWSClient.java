package com.connector.common.websocket.client.base;

import com.connector.common.websocket.constant.WSStatus;
import com.connector.common.websocket.internal.config.WSConnectConfig;
import com.connector.common.websocket.internal.config.WSDisconnectConfig;
import com.connector.common.websocket.internal.model.WSRawMessage;
import com.connector.common.websocket.internal.model.WSResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IWSClient<ConnectConfig extends WSConnectConfig, DisconnectConfig extends WSDisconnectConfig>
{
    String getClientId();

    void sendMessage(WSRawMessage request) throws Throwable;

    void connect(ConnectConfig wsConnectConfig) throws Throwable;

    void disconnect(DisconnectConfig wsDisconnectConfig) throws Throwable;

    Flux<WSResponse> responseStream();

    WSStatus getSocketStatus();

    Flux<WSStatus> socketStatusStream();

    Mono<WSStatus> waitConnectionStatus(WSStatus status);
}
