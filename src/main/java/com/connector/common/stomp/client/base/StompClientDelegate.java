package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompConnectionStatus;
import com.connector.common.stomp.constant.StompVersion;
import com.connector.common.stomp.internal.model.StompFrame;
import reactor.core.publisher.Flux;

import java.util.List;

public abstract class StompClientDelegate<StompConnectConfig extends IStompConnectConfig, StompDisconnectConfig, StompSessionInfo extends IStompSessionInfo, TransportReq, TransportResp> implements IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportReq, TransportResp>
{
    private final IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportReq, TransportResp> delegatee;

    public StompClientDelegate(IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportReq, TransportResp> delegatee)
    {
        this.delegatee = delegatee;
    }

    public abstract void registerRequestHandler(IStompRequestHandler requestHandler);

    public abstract Flux<StompFrame> deliverMessageStream();

    public abstract void connectDeliverMessage() throws Throwable;

    public abstract StompFrame populateRequest(StompFrame msg);

    public abstract void disconnectDeliverMessage() throws Throwable;

    @Override
    public String getClientId()
    {
        return delegatee.getClientId();
    }

    @Override
    public void sendStompMessage(StompFrame msg) throws Throwable
    {
        delegatee.sendStompMessage(populateRequest(msg));
    }

    @Override
    public void sendRawMessage(TransportReq rawMessage) throws Throwable
    {
        delegatee.sendRawMessage(rawMessage);
    }

    @Override
    public void connectStomp(StompConnectConfig stompConnectConfig) throws Throwable
    {
        delegatee.connectStomp(stompConnectConfig);
    }

    @Override
    public void disconnectStomp(StompDisconnectConfig disconnectConfig) throws Throwable
    {
        delegatee.disconnectStomp(disconnectConfig);
    }

    @Override
    public List<StompVersion> acceptedVersions()
    {
        return delegatee.acceptedVersions();
    }

    @Override
    public TransportReq encode(StompFrame msg) throws Throwable
    {
        return delegatee.encode(msg);
    }

    @Override
    public StompFrame decode(TransportResp payload) throws Throwable
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

    public IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportReq, TransportResp> getDelegatee()
    {
        return delegatee;
    }
}
