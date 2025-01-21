package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompConnectionStatus;
import com.connector.common.stomp.constant.StompFrameType;
import com.connector.common.stomp.constant.StompHeaders;
import com.connector.common.stomp.internal.model.StompFrame;
import com.connector.common.websocket.client.base.IWSClient;
import com.connector.common.websocket.constant.WSLifecycle;
import com.connector.common.websocket.constant.WSStatus;
import com.connector.common.websocket.internal.model.WSRawMessage;
import com.connector.common.websocket.internal.model.WSRequest;
import com.connector.common.websocket.internal.model.WSResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class WSStompClientBase<StompConnectConfig extends IStompConnectConfig, StompDisconnectConfig, StompSessionInfo extends IStompSessionInfo> implements IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, WSRequest, WSResponse>
{
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final String                      stompClientId;
    private final IWSClient<?, ?>             wsClient;
    private final Many<StompConnectionStatus> connectionStatusPublisher;

    private IStompRequestHandler        requestHandler;
    private StompConnectionStatus       connectionStatus;
    private ConnectableFlux<StompFrame> messageStream;
    private Disposable                  deliverMessageDispose;

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
        }).doOnError(e -> setConnectionStatus(StompConnectionStatus.DISCONNECTED)).doOnComplete(() -> setConnectionStatus(StompConnectionStatus.DISCONNECTED)).subscribe();
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
    public void sendStompMessage(StompFrame msg) throws Throwable
    {
        WSRequest wsRequest = encode(populateRequest(msg));
        sendRawMessage(wsRequest);
    }

    @Override
    public void sendRawMessage(WSRequest rawMessage) throws Throwable
    {
        wsClient.sendMessage(rawMessage);
    }

    @Override
    public String getClientId()
    {
        return MessageFormat.format("[id = {0}] -> ws[id = {1}]", stompClientId, wsClient.getClientId());
    }

    @Override
    public StompFrame populateRequest(StompFrame msg)
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
        if (requestHandler != null)
        {
            msg = requestHandler.populateRequest(msg);
        }
        return msg;
    }

    @Override
    public WSRequest encode(StompFrame msg)
    {
        if (msg == null)
        {
            return null;
        }
        String header = msg.getHeaders().entrySet().stream().map(headerEntry -> headerEntry.getKey() + ":" + headerEntry.getValue() + "\n").collect(Collectors.joining(""));
        String body = msg.getBody() == null ? "" : msg.getBody();
        return WSRequest.message(WSRawMessage.text(msg.getType().toString() + '\n' + header + '\n' + body + '\0'));
    }

    @Override
    public void registerRequestHandler(IStompRequestHandler requestHandler)
    {
        if (this.requestHandler != null)
        {
            return;
        }
        this.requestHandler = requestHandler;
    }

    @Override
    public StompFrame decode(WSResponse resp)
    {
        WSRawMessage msg = resp.getBody();
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
            return null;
        }

        if (data.charAt(0) == '\0')
        {
            // heartbeat
            return null;
        }
        Map<String, String> headers = new LinkedHashMap<>();
        int lineIndex = data.indexOf("\n");
        if (lineIndex < 0)
        {
            throw new IllegalStateException("Not a valid stomp frame. Reason: missing EOL(LF / CRLF) - " + data);
        }
        String frameTypeStr = data.substring(0, removeCR(data, 0, lineIndex));
        StompFrameType type = StompFrameType.valueOf(frameTypeStr);
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
        String rawData;
        if (headers.containsKey(StompHeaders.CONTENT_LENGTH))
        {
            int contentLength = Integer.parseInt(headers.get(StompHeaders.CONTENT_LENGTH));
            if (contentLength < 0)
            {
                throw new IllegalStateException("Not a valid stomp frame. Reason: invalid content length: " + headers.get(StompHeaders.CONTENT_LENGTH));
            }
            int dataEndIndex = dataStartIndex + contentLength;
            if (dataEndIndex > dataBoundEndIndex)
            {
                throw new IllegalStateException("Not a valid stomp frame. Reason: payload length exceeds content length");
            }
            rawData = data.substring(dataStartIndex, dataEndIndex);
        }
        else
        {
            rawData = data.substring(dataStartIndex, dataBoundEndIndex);
        }

        return new StompFrame(headers, rawData, type, headers.get(StompHeaders.CONTENT_TYPE));
    }

    @Override
    public void connectDeliverMessage()
    {
        if (getConnectionStatus() != StompConnectionStatus.CONNECTED)
        {
            return;
        }
        if (deliverMessageDispose != null && !deliverMessageDispose.isDisposed())
        {
            return;
        }

        this.messageStream = wsClient.responseStream().filter(resp -> resp.getLifecycle() == WSLifecycle.MESSAGE).map(resp -> {
            try
            {
                return decode(resp);
            }
            catch (Throwable e)
            {
                logger.error("Error while delivering message. Reason: {}", e.getMessage(), e);
                return null;
            }
        }).filter(Objects::nonNull).doOnError(err -> disconnectDeliverMessage()).doOnComplete(this::disconnectDeliverMessage).publish();

        this.deliverMessageDispose = messageStream.connect();
    }

    @Override
    public void disconnectDeliverMessage()
    {
        if (getConnectionStatus() == StompConnectionStatus.UNINITIALIZED)
        {
            return;
        }
        if (deliverMessageDispose != null && !deliverMessageDispose.isDisposed())
        {
            // Disconnect from delivered message stream
            deliverMessageDispose.dispose();
        }
    }

    @Override
    public Flux<StompFrame> deliverMessageStream()
    {
        return messageStream;
    }

    protected Integer removeCR(String text, int startInclusive, int endInclusive)
    {
        if (text == null || startInclusive < 0 || endInclusive < 0 || endInclusive > text.length() || endInclusive < startInclusive)
        {
            return null;
        }
        if (startInclusive == endInclusive)
        {
            return endInclusive;
        }
        if (text.charAt(endInclusive - 1) == '\r')
        {
            return endInclusive - 1;
        }
        return endInclusive;
    }

    protected IWSClient<?, ?> getWsClient()
    {
        return wsClient;
    }

    protected void setConnectionStatus(StompConnectionStatus connected)
    {
        connectionStatusPublisher.tryEmitNext(connected).orThrow();
    }

    protected abstract boolean trimHeaders();
}
