package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompConnectionStatus;
import com.connector.common.stomp.constant.StompVersion;
import com.connector.common.stomp.internal.model.StompFrame;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportPayload> extends IStompRequestHandler, IStompDeliverable
{
    String describeClient();

    String sendStompMessage(StompFrame msg, String receiptId) throws Throwable;

    void sendRawMessage(TransportPayload rawMessage) throws Throwable;

    void registerRequestHandler(IStompRequestHandler requestHandler);

    void connectStomp(StompConnectConfig stompConnectConfig) throws Throwable;

    Mono<StompConnectionStatus> waitConnectionStatus(StompConnectionStatus status);

    String disconnectStomp(StompDisconnectConfig stompDisconnectConfig, String receiptId) throws Throwable;

    List<StompVersion> acceptedVersions();

    TransportPayload encode(StompFrame msg) throws Throwable;

    StompFrame decode(TransportPayload payload) throws Throwable;

    StompConnectionStatus getConnectionStatus();

    Flux<StompConnectionStatus> connectionStatusStream();

    StompSessionInfo getConnectedSessionInfo();

    Mono<StompFrame> waitReceipt(String receiptId) throws Throwable;
}
