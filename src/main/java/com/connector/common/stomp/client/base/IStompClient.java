package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompConnectionStatus;
import com.connector.common.stomp.constant.StompVersion;
import com.connector.common.stomp.internal.model.StompFrame;
import reactor.core.publisher.Flux;

import java.util.List;

public interface IStompClient<StompConnectConfig extends IStompConnectConfig, StompDisconnectConfig, StompSessionInfo extends IStompSessionInfo, TransportReq, TransportResp> extends IStompRequestHandler, IStompDeliverable
{
    String getClientId();

    void sendStompMessage(StompFrame msg) throws Throwable;

    void sendRawMessage(TransportReq rawMessage) throws Throwable;

    void registerRequestHandler(IStompRequestHandler requestHandler);

    void connectStomp(StompConnectConfig stompConnectConfig) throws Throwable;

    void disconnectStomp(StompDisconnectConfig stompDisconnectConfig) throws Throwable;

    List<StompVersion> acceptedVersions();

    TransportReq encode(StompFrame msg) throws Throwable;

    StompFrame decode(TransportResp payload) throws Throwable;

    StompConnectionStatus getConnectionStatus();

    Flux<StompConnectionStatus> connectionStatusStream();

    StompSessionInfo getConnectedSessionInfo();
}
