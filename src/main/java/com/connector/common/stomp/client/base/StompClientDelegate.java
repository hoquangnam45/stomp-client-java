package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompConnectionStatus;
import com.connector.common.stomp.constant.StompVersion;
import com.connector.common.stomp.internal.model.StompFrame;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public abstract class StompClientDelegate<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportPayload> implements IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportPayload>
{
    private final IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportPayload> delegatee;

    private IStompRequestHandler wrappedRequestHandler;
    private IStompRequestHandler additionalRequestHandler;

    public StompClientDelegate(IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportPayload> delegatee)
    {
        this.delegatee = delegatee;
    }

    public void registerAdditionalRequestHandler(IStompRequestHandler requestHandler)
    {
        if (additionalRequestHandler != null)
        {
            return;
        }

        this.additionalRequestHandler = requestHandler;
    }

    public void registerWrappedRequestHandler(IStompRequestHandler requestHandler)
    {
        if (wrappedRequestHandler != null)
        {
            return;
        }

        this.wrappedRequestHandler = requestHandler;
    }

    @Override
    public String describeClient()
    {
        return delegatee.describeClient();
    }

    @Override
    public void registerRequestHandler(IStompRequestHandler requestHandler)
    {
        delegatee.registerRequestHandler(requestHandler);
    }

    @Override
    public String sendStompMessage(StompFrame msg, String receiptId) throws Throwable
    {
        return delegatee.sendStompMessage(populateRequest(msg), receiptId);
    }

    @Override
    public StompFrame populateRequest(StompFrame msg) throws Throwable
    {
        if (wrappedRequestHandler != null)
        {
            msg = wrappedRequestHandler.populateRequest(msg);
        }

        if (additionalRequestHandler != null)
        {
            msg = additionalRequestHandler.populateRequest(msg);
        }

        return msg;
    }

    @Override
    public void sendRawMessage(TransportPayload rawMessage) throws Throwable
    {
        delegatee.sendRawMessage(rawMessage);
    }

    @Override
    public void connectStomp(StompConnectConfig stompConnectConfig) throws Throwable
    {
        delegatee.connectStomp(stompConnectConfig);
    }

    @Override
    public String disconnectStomp(StompDisconnectConfig disconnectConfig, String receiptId) throws Throwable
    {
        return delegatee.disconnectStomp(disconnectConfig, receiptId);
    }

    @Override
    public List<StompVersion> acceptedVersions()
    {
        return delegatee.acceptedVersions();
    }

    @Override
    public TransportPayload encode(StompFrame msg) throws Throwable
    {
        return delegatee.encode(msg);
    }

    @Override
    public StompFrame decode(TransportPayload payload) throws Throwable
    {
        return delegatee.decode(payload);
    }

    @Override
    public StompConnectionStatus getConnectionStatus()
    {
        return delegatee.getConnectionStatus();
    }

    @Override
    public Flux<StompConnectionStatus> connectionStatusStream()
    {
        return delegatee.connectionStatusStream();
    }

    @Override
    public StompSessionInfo getConnectedSessionInfo()
    {
        return delegatee.getConnectedSessionInfo();
    }

    @Override
    public Mono<StompFrame> waitReceipt(String receiptId) throws Throwable
    {
        return delegatee.waitReceipt(receiptId);
    }

    @Override
    public Mono<StompConnectionStatus> waitConnectionStatus(StompConnectionStatus status)
    {
        return delegatee.waitConnectionStatus(status);
    }

    public IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportPayload> getDelegatee()
    {
        return delegatee;
    }
}
