package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompConnectionStatus;
import com.connector.common.stomp.constant.StompFrameType;
import com.connector.common.stomp.constant.StompHeaders;
import com.connector.common.stomp.internal.exception.StompErrorFrame;
import com.connector.common.stomp.internal.model.StompFrame;
import com.connector.common.websocket.client.base.IWSClient;
import com.connector.common.websocket.constant.WSLifecycle;
import com.connector.common.websocket.constant.WSStatus;
import com.connector.common.websocket.internal.model.WSRawMessage;
import reactor.core.Disposable;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class WSStompClientBase<StompConnectConfig extends IStompConnectConfig, StompDisconnectConfig, StompSessionInfo extends IStompSessionInfo> implements IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, WSRawMessage>
{
    private final String                      stompClientId;
    private final IWSClient<?, ?>             wsClient;
    private final Many<StompConnectionStatus> connectionStatusPublisher;

    private IStompRequestHandler  additionalRequestHandler;
    private StompConnectionStatus connectionStatus;
    private Flux<StompFrame>      messageStream;
    private Disposable            deliverMessageDispose;

    private StompSessionInfo sessionInfo;

    protected WSStompClientBase(String stompClientId, IWSClient<?, ?> wsClient)
    {
        this.stompClientId = stompClientId;
        this.wsClient = wsClient;
        this.connectionStatusPublisher = Sinks.many().replay().latest();
        connectionStatusPublisher.tryEmitNext(StompConnectionStatus.UNINITIALIZED).orThrow();
        connectionStatusStream().subscribe(status -> this.connectionStatus = status);
        getWsClient().socketStatusStream().doOnNext(status -> {
            if (status == WSStatus.CLOSED)
            {
                setConnectionStatus(StompConnectionStatus.DISCONNECTED);
            }
        }).subscribe();

        setSessionInfo(null);
    }

    @Override
    public StompConnectionStatus getConnectionStatus()
    {
        return connectionStatus;
    }

    @Override
    public Flux<StompConnectionStatus> connectionStatusStream()
    {
        return connectionStatusPublisher.asFlux();
    }

    @Override
    public String sendStompMessage(StompFrame msg, String receiptId) throws Throwable
    {
        msg = populateRequest(msg);
        if (receiptId != null)
        {
            msg.getHeaders().put(StompHeaders.RECEIPT, receiptId);
        }
        WSRawMessage wsRawMessage = encode(msg);
        sendRawMessage(wsRawMessage);

        return receiptId;
    }

    @Override
    public Mono<StompFrame> waitReceipt(String receiptId)
    {
        if (receiptId == null)
        {
            return Mono.empty();
        }
        return Mono.from(deliverMessageStream().filter(resp -> resp.getType() == StompFrameType.RECEIPT && receiptId.equals(resp.getHeaders().get(StompHeaders.RECEIPT_ID))));
    }

    @Override
    public void sendRawMessage(WSRawMessage rawMessage) throws Throwable
    {
        wsClient.sendMessage(rawMessage);
    }

    @Override
    public String describeClient()
    {
        return MessageFormat.format("[id = {0}] -> ws[id = {1}]", stompClientId, wsClient.getClientId());
    }

    @Override
    public StompFrame populateRequest(StompFrame msg) throws Throwable
    {
        if (msg == null)
        {
            return null;
        }
        String body = msg.getBody() == null ? "" : msg.getBody();
        Map<String, String> headerMap = msg.getHeaders() == null ? new LinkedHashMap<>() : msg.getHeaders();
        headerMap.put(StompHeaders.CONTENT_LENGTH, String.valueOf(body.getBytes(StandardCharsets.UTF_8).length));
        if (msg.getContentType() != null)
        {
            headerMap.put(StompHeaders.CONTENT_TYPE, msg.getContentType());
        }
        msg = StompFrame.populateHeaders(headerMap, msg);
        if (additionalRequestHandler != null)
        {
            msg = additionalRequestHandler.populateRequest(msg);
        }
        return msg;
    }

    @Override
    public Mono<StompConnectionStatus> waitConnectionStatus(StompConnectionStatus status)
    {
        return Mono.from(connectionStatusStream().filter(s -> s == status));
    }

    @Override
    public WSRawMessage encode(StompFrame msg)
    {
        if (msg == null)
        {
            return null;
        }
        String header = msg.getHeaders().entrySet().stream().map(headerEntry -> headerEntry.getKey() + ":" + headerEntry.getValue() + "\n").collect(Collectors.joining(""));
        String body = msg.getBody() == null ? "" : msg.getBody();
        return WSRawMessage.text(msg.getType().toString() + '\n' + header + '\n' + body + '\0');
    }

    @Override
    public void registerRequestHandler(IStompRequestHandler requestHandler)
    {
        if (this.additionalRequestHandler != null)
        {
            return;
        }
        this.additionalRequestHandler = requestHandler;
    }

    @Override
    public StompFrame decode(WSRawMessage msg)
    {
        String data;

        switch (msg.getType())
        {
        case BINARY:
            data = new String(msg.getBinaryData(), StandardCharsets.UTF_8);
            break;
        case TEXT:
            data = msg.getStringData();
            break;
        default:
            throw new UnsupportedOperationException("Unknown websocket response type " + msg.getType());
        }

        if (data.isEmpty() || data.charAt(0) == '\n' || data.charAt(0) == '\0' || data.length() == 2 && data.charAt(0) == '\r' && data.charAt(1) == '\n')
        {
            // heartbeat
            return new StompFrame(Collections.emptyMap(), "\n", StompFrameType.HEARTBEAT, "");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        int lineIndex = data.indexOf("\n");
        if (lineIndex < 0)
        {
            throw new IllegalStateException("Not a valid stomp frame. Reason: missing EOL(LF / CRLF) - " + data);
        }
        String frameTypeStr = data.substring(0, removeCR(data, 0, lineIndex));
        StompFrameType type = StompFrameType.parse(frameTypeStr);
        if (type == null)
        {
            throw new IllegalStateException("Unknown stomp frame type: " + frameTypeStr);
        }
        while (true)
        {
            int startLineIndex = lineIndex + 1;
            lineIndex = data.indexOf("\n", startLineIndex);
            if (lineIndex < 0)
            {
                throw new IllegalStateException("Not a valid stomp frame. Reason: missing EOL(LF / CRLF) - " + data);
            }
            int endLineIndex = removeCR(data, startLineIndex, lineIndex);
            if (startLineIndex == endLineIndex)
            {
                break;
            }
            int headerDelimiterIndex = data.indexOf(":", startLineIndex);
            if (headerDelimiterIndex < 0 || headerDelimiterIndex > endLineIndex)
            {
                throw new IllegalStateException("Not a valid stomp frame header should be in the format of <headerKey>:<headerValue>. But is: " + data.substring(startLineIndex, endLineIndex));
            }
            headers.computeIfAbsent(data.substring(startLineIndex, headerDelimiterIndex), _k -> {
                if (trimHeaders())
                {
                    return data.substring(headerDelimiterIndex + 1, endLineIndex).trim();
                }
                return data.substring(headerDelimiterIndex + 1, endLineIndex);
            });
        }
        int dataStartIndex = lineIndex + 1;
        int dataBoundEndIndex = data.lastIndexOf("\0");
        if (dataBoundEndIndex < 0)
        {
            throw new IllegalStateException("Not a valid stomp frame. Reason: payload is missing null terminated ('\0')");
        }
        String rawData = data.substring(dataStartIndex, dataBoundEndIndex);

        if (headers.containsKey(StompHeaders.CONTENT_LENGTH))
        {
            int contentLength = Integer.parseInt(headers.get(StompHeaders.CONTENT_LENGTH));
            if (contentLength < 0)
            {
                throw new IllegalStateException("Not a valid stomp frame. Reason: invalid content length: " + headers.get(StompHeaders.CONTENT_LENGTH));
            }
            byte[] octets = rawData.getBytes(StandardCharsets.UTF_8);
            if (contentLength > octets.length)
            {
                throw new IllegalStateException("Not a valid stomp frame. Reason: content length " + contentLength + " exceeds payload length " + octets.length);
            }
            if (octets.length != contentLength)
            {
                byte[] dest = new byte[contentLength];
                rawData = new String(dest, 0, contentLength, StandardCharsets.UTF_8);
            }
        }

        return new StompFrame(headers, rawData, type, headers.get(StompHeaders.CONTENT_TYPE));
    }

    @Override
    public void connectDeliverMessage()
    {
        if (deliverMessageDispose != null && !deliverMessageDispose.isDisposed())
        {
            return;
        }

        ConnectableFlux<StompFrame> connectableStream = wsClient.responseStream().filter(resp -> resp.getLifecycle() == WSLifecycle.MESSAGE).flatMap(resp -> {
            try
            {
                StompFrame frame = decode(resp.getBody());
                if (frame.getType() == StompFrameType.ERROR)
                {
                    return Mono.error(new StompErrorFrame(frame, "Error frame received from server"));
                }
                return Mono.just(frame);
            }
            catch (Throwable e)
            {
                return Mono.error(e);
            }
        }).publish();

        this.messageStream = connectableStream;
        this.deliverMessageDispose = connectableStream.connect();
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
    public void disconnectDeliverMessage()
    {
        if (deliverMessageDispose == null || deliverMessageDispose.isDisposed())
        {
            return;
        }
        deliverMessageDispose.dispose();
    }

    @Override
    public synchronized String disconnectStomp(StompDisconnectConfig stompDisconnectConfig, String receiptId) throws Throwable
    {
        if (getConnectionStatus() != StompConnectionStatus.CONNECTED)
        {
            return null;
        }

        receiptId = sendStompMessage(new StompFrame(null, null, StompFrameType.DISCONNECT, null), receiptId);

        if (receiptId != null)
        {
            waitReceipt(receiptId).subscribe(frame -> setDisconnected(), err -> setDisconnected(), this::setDisconnected);
        }
        else
        {
            setDisconnected();
        }

        return receiptId;
    }

    @Override
    public Flux<StompFrame> deliverMessageStream()
    {
        return messageStream;
    }

    @Override
    public StompSessionInfo getConnectedSessionInfo()
    {
        return sessionInfo;
    }

    protected Integer removeCR(String text, int startInclusive, int endExclusive)
    {
        if (text == null || startInclusive < 0 || endExclusive < 0 || endExclusive > text.length() || endExclusive < startInclusive)
        {
            return null;
        }
        if (startInclusive == endExclusive)
        {
            return endExclusive;
        }
        if (text.charAt(endExclusive - 1) == '\r')
        {
            return endExclusive - 1;
        }
        return endExclusive;
    }

    protected IWSClient<?, ?> getWsClient()
    {
        return wsClient;
    }

    protected void setConnectionStatus(StompConnectionStatus status)
    {
        if (getConnectionStatus() == status)
        {
            return;
        }
        connectionStatusPublisher.tryEmitNext(status).orThrow();
    }

    protected void setDisconnected()
    {
        setSessionInfo(null);

        disconnectDeliverMessage();

        setConnectionStatus(StompConnectionStatus.DISCONNECTED);
    }

    protected void setSessionInfo(StompSessionInfo sessionInfo)
    {
        this.sessionInfo = sessionInfo;
    }

    protected abstract boolean trimHeaders();

    protected abstract void connectWithConfig(StompConnectConfig connectConfig) throws Throwable;
}
