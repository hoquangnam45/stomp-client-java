package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompConnectionStatus;
import com.connector.common.stomp.constant.StompFrameType;
import com.connector.common.stomp.constant.StompHeaders;
import com.connector.common.stomp.constant.StompVersion;
import com.connector.common.stomp.internal.config.StompConnectConfigV10;
import com.connector.common.stomp.internal.config.StompDisconnectConfigV10;
import com.connector.common.stomp.internal.model.StompFrame;
import com.connector.common.stomp.internal.model.StompSessionInfoV10;
import com.connector.common.websocket.client.base.IWSClient;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WSStompClientBaseV10<StompConnectConfig extends StompConnectConfigV10, StompDisconnectConfig extends StompDisconnectConfigV10> extends WSStompClientBase<StompConnectConfig, StompDisconnectConfig, StompSessionInfoV10>
{
    // session infos
    private StompSessionInfoV10 sessionInfo;

    protected WSStompClientBaseV10(String stompClientId, IWSClient<?, ?> wsClient)
    {
        super(stompClientId, wsClient);
    }

    @Override
    public synchronized void connectStomp(StompConnectConfig stompConnectConfig) throws Throwable
    {
        if (getConnectionStatus() == StompConnectionStatus.CONNECTED)
        {
            return;
        }

        connectWithConfig(stompConnectConfig);
    }

    @Override
    public synchronized void disconnectStomp(StompDisconnectConfig stompDisconnectConfig) throws Throwable
    {
        if (getConnectionStatus() != StompConnectionStatus.CONNECTED)
        {
            return;
        }

        boolean sendDisconnect;
        if (stompDisconnectConfig == null)
        {
            sendDisconnect = true;
        }
        else
        {
            sendDisconnect = stompDisconnectConfig.isSendDisconnect();
        }
        if (sendDisconnect)
        {
            sendStompMessage(new StompFrame(null, null, StompFrameType.DISCONNECT, null));
        }

        this.sessionInfo = null;

        setConnectionStatus(StompConnectionStatus.DISCONNECTED);

        disconnectDeliverMessage();
    }

    @Override
    public List<StompVersion> acceptedVersions()
    {
        return Collections.singletonList(StompVersion.STOMP_1_0);
    }

    @Override
    public StompSessionInfoV10 getConnectedSessionInfo()
    {
        return sessionInfo;
    }

    @Override
    public String getClientId()
    {
        return MessageFormat.format("stomp[ver = 1.0]{0}", super.getClientId());
    }

    private void connectWithConfig(StompConnectConfig connectConfig) throws Throwable
    {
        Map<String, String> connectHeaders = new LinkedHashMap<>();
        if (connectConfig.getLogin() != null)
        {
            connectHeaders.put(StompHeaders.LOGIN, connectConfig.getLogin());
        }
        if (connectConfig.getPasscode() != null)
        {
            connectHeaders.put(StompHeaders.PASSCODE, connectConfig.getPasscode());
        }
        StompFrame stompMessage = new StompFrame(connectHeaders, null, StompFrameType.CONNECT, null);

        sendStompMessage(stompMessage);

        StompFrame connectedFrame = getWsClient().responseStream().map(this::decode).filter(msg -> msg.getType() == StompFrameType.CONNECTED || msg.getType() == StompFrameType.ERROR).blockFirst(connectConfig.getConnectTimeoutDuration());

        if (connectedFrame == null)
        {
            throw new IllegalStateException("Should not be possible to enter here either connecting will timeout or return something");
        }

        if (connectedFrame.getType() == StompFrameType.ERROR)
        {
            logger.error("Failed to connect to server. Headers: {}", connectedFrame.getHeaders());
            return;
        }

        setConnectionStatus(StompConnectionStatus.CONNECTED);

        connectDeliverMessage();

        String sessionId = connectedFrame.getHeaders().get(StompHeaders.SESSION);
        this.sessionInfo = new StompSessionInfoV10(sessionId, stompMessage, connectedFrame, connectConfig);
    }

    @Override
    protected boolean trimHeaders()
    {
        return true;
    }
}

