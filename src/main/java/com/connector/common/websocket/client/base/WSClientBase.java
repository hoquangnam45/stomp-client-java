package com.connector.common.websocket.client.base;

import com.connector.common.IAuthenticationHandler;
import com.connector.common.websocket.constant.WSLifecycle;
import com.connector.common.websocket.constant.WSRawMessageType;
import com.connector.common.websocket.constant.WSStatus;
import com.connector.common.websocket.exception.ResponseThrowable;
import com.connector.common.websocket.internal.config.WSConnectConfig;
import com.connector.common.websocket.internal.config.WSDisconnectConfig;
import com.connector.common.websocket.internal.model.WSRawMessage;
import com.connector.common.websocket.internal.model.WSRequest;
import com.connector.common.websocket.internal.model.WSResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

public class WSClientBase<ConnectConfig extends WSConnectConfig, DisconnectConfig extends WSDisconnectConfig> implements IWSClient<ConnectConfig, DisconnectConfig>
{
    private final Logger         logger = LoggerFactory.getLogger(getClass());
    private final String         clientId;
    private final Many<WSStatus> socketStatusPublisher;

    private WSStatus         socketStatus;
    private Many<WSResponse> receivePublisher;
    private WebSocket        webSocket;

    public WSClientBase(String clientId)
    {
        this.clientId = clientId;
        this.socketStatusPublisher = Sinks.many().replay().latest();
        socketStatusPublisher.tryEmitNext(WSStatus.UNINITIALIZED).orThrow();
        socketStatusStream().subscribe(status -> this.socketStatus = status);
    }

    @Override
    public synchronized void connect(ConnectConfig wsConnectConfig, IAuthenticationHandler<WSRequest> authenticationHandler) throws Throwable
    {
        WSStatus socketStatus = getSocketStatus();
        if (socketStatus.isConnected() || socketStatus == WSStatus.CONNECTING)
        {
            return;
        }

        setSocketStatus(WSStatus.CONNECTING);

        // Setup observable channel
        Many<WSResponse> receivePublisher = Sinks.many().multicast().onBackpressureBuffer();
        Flux<WSResponse> receiveStream = receivePublisher.asFlux().doOnError(t -> {
            logger.error("WebSocket connection failed. Reason: {}", t.getMessage(), t);
            setSocketStatus(WSStatus.FAILED);
        }).doOnComplete(() -> setSocketStatus(WSStatus.CLOSED)).doOnNext(msg -> setSocketStatus(WSStatus.fromLifecycle(msg.getLifecycle())));

        // Start connecting
        WebSocket webSocket = connect(wsConnectConfig, authenticationHandler, getWebSocketListener(receivePublisher));
        receiveStream.subscribe();

        socketStatusStream().filter(WSStatus::isConnected).blockFirst();

        this.webSocket = webSocket;
        this.receivePublisher = receivePublisher;
    }

    public WebSocketListener getWebSocketListener(Many<WSResponse> receivePublisher)
    {
        return new WebSocketListener()
        {
            @Override
            public void onOpen(WebSocket webSocket, Response response)
            {
                try (ResponseBody b = response.body())
                {
                    byte[] data;
                    if (b == null)
                    {
                        data = null;
                    }
                    else
                    {
                        data = b.bytes();
                    }
                    receivePublisher.tryEmitNext(new WSResponse(WSRawMessage.binary(data), webSocket, WSLifecycle.OPEN)).orThrow();
                }
                catch (Throwable t)
                {
                    Throwable wsThrowable = new ResponseThrowable(response, t, WSLifecycle.OPEN);
                    receivePublisher.tryEmitError(wsThrowable).orThrow();
                }
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason)
            {
                receivePublisher.tryEmitNext(new WSResponse(WSRawMessage.text(reason), webSocket, WSLifecycle.CLOSING)).orThrow();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response)
            {
                Throwable wsThrowable = new ResponseThrowable(response, t, WSLifecycle.OPEN);
                receivePublisher.tryEmitError(wsThrowable).orThrow();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text)
            {
                receivePublisher.tryEmitNext(new WSResponse(WSRawMessage.text(text), webSocket, WSLifecycle.MESSAGE)).orThrow();
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes)
            {
                receivePublisher.tryEmitNext(new WSResponse(WSRawMessage.binary(bytes.toByteArray()), webSocket, WSLifecycle.MESSAGE)).orThrow();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason)
            {

                if (getSocketStatus().isFinal())
                {
                    // Because error event will close publisher by emit error then OkWebSocket will proceed to closed event
                    // we cannot emit complete on already closed publisher this need to be checked so we do not emit complete
                    // here. Error evt -> closed evt -> this already emit error to subscriber no need to do anything
                    // in this case socket status is expected to be FAILED in this case
                    return;
                }
                receivePublisher.tryEmitComplete().orThrow();
            }
        };
    }

    @Override
    public synchronized void disconnect(DisconnectConfig wsDisconnectConfig) throws Throwable
    {
        if (!getSocketStatus().isConnected())
        {
            return;
        }
        disconnect(webSocket, wsDisconnectConfig.isForceClose(), wsDisconnectConfig.getCode(), wsDisconnectConfig.getReason());
        socketStatusStream().filter(WSStatus::isFinal).blockFirst();
    }

    @Override
    public Flux<WSResponse> responseStream()
    {
        return receivePublisher.asFlux();
    }

    @Override
    public Flux<WSStatus> socketStatusStream()
    {
        return socketStatusPublisher.asFlux();
    }

    @Override
    public String getClientId()
    {
        return clientId;
    }

    @Override
    public void sendMessage(WSRequest req)
    {
        if (req == null || req.getMessage() == null)
        {
            return;
        }
        WSStatus socketStatus = getSocketStatus();
        if (!socketStatus.isConnected())
        {
            throw new IllegalStateException("WebSocket is not connected, the current socket status is " + socketStatus);
        }
        WSRawMessage message = req.getMessage();
        if (message.getType() == WSRawMessageType.BINARY)
        {
            webSocket.send(ByteString.of(message.getBinaryData()));
        }
        else if (message.getType() == WSRawMessageType.TEXT)
        {
            webSocket.send(message.getStringData());
        }
        else
        {
            throw new IllegalArgumentException("Unsupported message type: " + message.getType());
        }
    }

    @Override
    public WSStatus getSocketStatus()
    {
        return socketStatus;
    }

    private void setSocketStatus(WSStatus wsStatus)
    {
        if (getSocketStatus() == wsStatus)
        {
            return;
        }
        socketStatusPublisher.tryEmitNext(wsStatus).orThrow();
    }

    protected WebSocket connect(ConnectConfig config, IAuthenticationHandler<WSRequest> authenticationHandler, WebSocketListener webSocketListener)
    {
        OkHttpClient client = new OkHttpClient.Builder().readTimeout(config.getReadTimeoutDuration()).writeTimeout(config.getWriteTimeoutDuration()).connectTimeout(config.getConnectTimeoutDuration()).build();

        Request request = new Request.Builder().url(config.getAddress()).build();

        WSRequest authenticatedRequest;
        if (authenticationHandler != null)
        {
            authenticatedRequest = authenticationHandler.authenticate(WSRequest.request(request));
        }
        else
        {
            authenticatedRequest = WSRequest.request(request);
        }

        return client.newWebSocket(authenticatedRequest.getRequest(), webSocketListener);
    }

    protected void disconnect(WebSocket webSocket, boolean forceClose, Integer code, String reason)
    {
        if (forceClose)
        {
            webSocket.cancel();
            return;
        }
        if (code == null)
        {
            // References: https://github.com/Luka967/websocket-close-codes
            code = 1000; // Normal closure
        }
        if (reason == null)
        {
            reason = "Normal disconnect operation";
        }
        webSocket.close(code, reason);
    }
}