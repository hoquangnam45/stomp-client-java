package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompConnectionStatus;
import com.connector.common.stomp.constant.StompFrameType;
import com.connector.common.stomp.constant.StompHeaders;
import com.connector.common.stomp.constant.StompVersion;
import com.connector.common.stomp.internal.config.StompConnectConfigV10;
import com.connector.common.stomp.internal.exception.StompErrorFrame;
import com.connector.common.stomp.internal.model.StompFrame;
import com.connector.common.stomp.internal.model.StompSessionInfoV10;
import com.connector.common.websocket.client.base.IWSClient;
import com.connector.common.websocket.constant.WSLifecycle;
import com.connector.common.websocket.internal.model.WSResponse;
import reactor.core.publisher.Mono;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WSStompClientBaseV10<StompConnectConfig extends StompConnectConfigV10, StompDisconnectConfig> extends WSStompClientBase<StompConnectConfig, StompDisconnectConfig, StompSessionInfoV10>
{
    protected WSStompClientBaseV10(String stompClientId, IWSClient<?, ?> wsClient)
    {
        super(stompClientId, wsClient);
    }

    @Override
    public List<StompVersion> acceptedVersions()
    {
        return Collections.singletonList(StompVersion.STOMP_1_0);
    }

    @Override
    public String describeClient()
    {
        return MessageFormat.format("stomp[ver = 1.0]{0}", super.describeClient());
    }

    @Override
    protected void connectWithConfig(StompConnectConfig connectConfig) throws Throwable
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

        sendStompMessage(stompMessage, null);
        Mono.from(getWsClient().responseStream().filter(resp -> resp.getLifecycle() == WSLifecycle.MESSAGE).map(WSResponse::getBody).map(this::decode).filter(msg -> msg.getType() == StompFrameType.CONNECTED || msg.getType() == StompFrameType.ERROR).flatMap(connectedFrame -> {
            if (connectedFrame.getType() == StompFrameType.ERROR)
            {
                setDisconnected();
                return Mono.error(new StompErrorFrame(connectedFrame, "Failed to connect to server. Client: " + describeClient() + ". Config: " + connectConfig));
            }

            connectDeliverMessage();

            Map<String, String> connectedHeaders = connectedFrame.getHeaders() == null ? Collections.emptyMap() : connectedFrame.getHeaders();
            String sessionId = connectedHeaders.get(StompHeaders.SESSION);
            setSessionInfo(new StompSessionInfoV10(sessionId, stompMessage, connectedFrame, connectConfig));

            setConnectionStatus(StompConnectionStatus.CONNECTED);

            return Mono.just(connectedFrame);
        })).subscribe();
    }

    @Override
    protected boolean trimHeaders()
    {
        return true;
    }
}

