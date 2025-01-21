package com.connector.common.websocket.client.base;

import com.connector.common.websocket.constant.WSLifecycle;
import com.connector.common.websocket.constant.WSRawMessageType;
import com.connector.common.websocket.constant.WSStatus;
import com.connector.common.websocket.exception.ResponseThrowable;
import com.connector.common.websocket.internal.config.WSConnectConfig;
import com.connector.common.websocket.internal.config.WSDisconnectConfig;
import com.connector.common.websocket.internal.model.WSRawMessage;
import com.connector.common.websocket.internal.model.WSResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

public class WSClientBase<ConnectConfig extends WSConnectConfig, DisconnectConfig extends WSDisconnectConfig> implements IWSClient<ConnectConfig, DisconnectConfig>
{
    private final String         clientId;
    private final Many<WSStatus> socketStatusPublisher;

    private OkHttpClient     okHttpClient;
    private WSStatus         socketStatus;
    private Flux<WSResponse> messageStream;
    private WebSocket        webSocket;

    public WSClientBase(String clientId)
    {
        this.clientId = clientId;
        this.socketStatusPublisher = Sinks.many().replay().latest();
        socketStatusPublisher.tryEmitNext(WSStatus.UNINITIALIZED).orThrow();
        socketStatusStream().subscribe(status -> this.socketStatus = status);
    }

    @Override
    public synchronized void connect(ConnectConfig wsConnectConfig) throws Throwable
    {
        WSStatus socketStatus = getSocketStatus();
        if (socketStatus.isConnected() || socketStatus == WSStatus.CONNECTING)
        {
            return;
        }

        setSocketStatus(WSStatus.CONNECTING);

        // Setup observable channel
        Many<WSResponse> receivePublisher = Sinks.many().multicast().onBackpressureBuffer();
        Flux<WSResponse> receiveStream = receivePublisher.asFlux().doOnError(t -> setSocketStatus(WSStatus.FAILED)).doOnComplete(() -> setSocketStatus(WSStatus.CLOSED)).doOnNext(msg -> setSocketStatus(WSStatus.fromLifecycle(msg.getLifecycle()))).publish().refCount();
        receiveStream.onErrorComplete().subscribe();

        // Start connecting
        this.webSocket = connect(wsConnectConfig, getWebSocketListener(receivePublisher));
        this.messageStream = receiveStream;
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
    }

    @Override
    public Flux<WSResponse> responseStream()
    {
        return messageStream;
    }

    @Override
    public Flux<WSStatus> socketStatusStream()
    {
        return socketStatusPublisher.asFlux();
    }

    @Override
    public Mono<WSStatus> waitConnectionStatus(WSStatus status)
    {
        return Mono.from(socketStatusStream().filter(s -> s == status));
    }

    @Override
    public String getClientId()
    {
        return clientId;
    }

    @Override
    public void sendMessage(WSRawMessage message)
    {
        if (message == null)
        {
            return;
        }
        WSStatus socketStatus = getSocketStatus();
        if (!socketStatus.isConnected())
        {
            throw new IllegalStateException("WebSocket is not connected, the current socket status is " + socketStatus);
        }
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

    public OkHttpClient getOkHttpClient()
    {
        return okHttpClient;
    }

    protected void setSocketStatus(WSStatus wsStatus)
    {
        if (getSocketStatus() == wsStatus)
        {
            return;
        }
        socketStatusPublisher.tryEmitNext(wsStatus).orThrow();
    }

    protected WebSocket connect(ConnectConfig config, WebSocketListener webSocketListener)
    {
        OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

        if (config.getOkHttpClientCustomizer() != null)
        {
            OkHttpClient v = config.getOkHttpClientCustomizer().authenticate(okHttpClient);
            if (v == null)
            {
                this.okHttpClient = okHttpClient;
            }
            else
            {
                this.okHttpClient = v;
            }
        }
        else
        {
            this.okHttpClient = okHttpClient;
        }

        Request request = new Request.Builder().url(config.getAddress()).build();
        if (config.getRequestCustomizer() != null)
        {
            Request v = config.getRequestCustomizer().authenticate(request);
            if (v != null)
            {
                request = v;
            }
        }

        return this.okHttpClient.newWebSocket(request, webSocketListener);
    }

    protected void disconnect(WebSocket webSocket, boolean forceClose, Integer code, String reason)
    {
        if (webSocket == null)
        {
            return;
        }
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