package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompConnectionStatus;
import com.connector.common.stomp.constant.StompFrameType;
import com.connector.common.stomp.constant.StompHeaders;
import com.connector.common.stomp.constant.StompTransactionStatus;
import com.connector.common.stomp.constant.StompVersion;
import com.connector.common.stomp.internal.model.StompFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class StompClientTransaction<StompConnectConfig extends IStompConnectConfig, StompDisconnectConfig, StompSessionInfo extends IStompSessionInfo, TransportReq, TransportResp> extends StompClientDelegate<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportReq, TransportResp> implements IStompTransaction<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportReq, TransportResp>
{
    private final Logger logger = LoggerFactory.getLogger(StompClientTransaction.class);

    private final String                       transactionId;
    private final Many<StompTransactionStatus> transactionStatusPublisher;

    private IStompRequestHandler        requestHandler;
    private StompTransactionStatus      transactionStatus;
    private Disposable                  deliverMessageDispose;
    private ConnectableFlux<StompFrame> messageStream;

    protected StompClientTransaction(String transactionId, IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportReq, TransportResp> stompClient)
    {
        super(stompClient);
        this.transactionId = transactionId;
        this.transactionStatusPublisher = Sinks.many().replay().latest();
        transactionStatusPublisher.tryEmitNext(StompTransactionStatus.UNINITIALIZED).orThrow();
        transactionStatusStream().subscribe(status -> this.transactionStatus = status);
        getDelegatee().connectionStatusStream().doOnNext(status -> {
            if (status == StompConnectionStatus.DISCONNECTED && getTransactionStatus() == StompTransactionStatus.IN_PROGRESS)
            {
                setTransactionStatus(StompTransactionStatus.ABORTED);
            }
        }).doOnError(e -> {
            if (getTransactionStatus() == StompTransactionStatus.IN_PROGRESS)
            {
                setTransactionStatus(StompTransactionStatus.ABORTED);
            }
        }).doOnComplete(() -> {
            if (getTransactionStatus() == StompTransactionStatus.IN_PROGRESS)
            {
                setTransactionStatus(StompTransactionStatus.ABORTED);
            }
        }).subscribe();
    }

    @Override
    public String getClientId()
    {
        return MessageFormat.format("transaction[id = {0}] -> {1} ", getTransactionId(), getDelegatee().getClientId());
    }

    @Override
    public StompFrame populateRequest(StompFrame msg)
    {
        Map<String, String> headers = msg.getHeaders() == null ? new LinkedHashMap<>() : msg.getHeaders();
        switch (msg.getType())
        {
        case SEND:
            headers.put(StompHeaders.TRANSACTION, getTransactionId());
            break;
        case SUBSCRIBE:
        case UNSUBSCRIBE:
            // Headers will be set by the parent
            return msg;
        case ACK:
        case NACK:
            if (!StompVersion.STOMP_1_0.equals(getConnectedSessionInfo().getVersion()))
            {
                headers.put(StompHeaders.TRANSACTION, getTransactionId());
            }
            break;
        case BEGIN:
        case COMMIT:
        case ABORT:
            headers.put(StompHeaders.TRANSACTION, getTransactionId());
            return msg;
        case CONNECT:
        case DISCONNECT:
        case STOMP:
        case CONNECTED:
            // generic frame types, not handled this
            return msg;
        case MESSAGE:
        case RECEIPT:
        case ERROR:
        default:
            // Not handle these frame types
            return msg;
        }
        msg = StompFrame.populateHeaders(headers, msg);
        if (requestHandler != null)
        {
            msg = requestHandler.populateRequest(msg);
        }
        return msg;
    }

    @Override
    public synchronized void begin() throws Throwable
    {
        if (getTransactionStatus() == StompTransactionStatus.IN_PROGRESS)
        {
            return;
        }
        sendStompMessage(populateRequest(new StompFrame(null, null, StompFrameType.BEGIN, null)));

        setTransactionStatus(StompTransactionStatus.IN_PROGRESS);

        connectDeliverMessage();
    }

    @Override
    public synchronized void abort() throws Throwable
    {
        if (getTransactionStatus() != StompTransactionStatus.IN_PROGRESS)
        {
            return;
        }
        sendStompMessage(populateRequest(new StompFrame(null, null, StompFrameType.ABORT, null)));

        setTransactionStatus(StompTransactionStatus.ABORTED);

        disconnectDeliverMessage();
    }

    @Override
    public synchronized void commit() throws Throwable
    {
        if (getTransactionStatus() != StompTransactionStatus.IN_PROGRESS)
        {
            return;
        }
        sendStompMessage(populateRequest(new StompFrame(null, null, StompFrameType.COMMIT, null)));

        setTransactionStatus(StompTransactionStatus.COMMITTED);

        disconnectDeliverMessage();
    }

    @Override
    public String getTransactionId()
    {
        return transactionId;
    }

    @Override
    public StompTransactionStatus getTransactionStatus()
    {
        return transactionStatus;
    }

    @Override
    public Flux<StompTransactionStatus> transactionStatusStream()
    {
        return transactionStatusPublisher.asFlux();
    }

    private void setTransactionStatus(StompTransactionStatus transactionStatus)
    {
        transactionStatusPublisher.tryEmitNext(transactionStatus).orThrow();
    }

    @Override
    public void connectDeliverMessage()
    {
        if (getTransactionStatus() != StompTransactionStatus.IN_PROGRESS)
        {
            return;
        }
        if (deliverMessageDispose != null && !deliverMessageDispose.isDisposed())
        {
            return;
        }
        this.messageStream = getDelegatee().deliverMessageStream().filter(msg -> {
            Map<String, String> msgHeaders = msg.getHeaders() == null ? Collections.emptyMap() : msg.getHeaders();
            return getTransactionId().equals(msgHeaders.get(StompHeaders.TRANSACTION));
        }).doOnError(err -> disconnectDeliverMessage()).doOnComplete(this::disconnectDeliverMessage).publish();

        this.deliverMessageDispose = messageStream.connect();
    }

    @Override
    public void disconnectDeliverMessage()
    {
        if (getTransactionStatus() == StompTransactionStatus.UNINITIALIZED)
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
    public void registerRequestHandler(IStompRequestHandler requestHandler)
    {
        if (this.requestHandler != null)
        {
            return;
        }
        this.requestHandler = requestHandler;
    }

    @Override
    public Flux<StompFrame> deliverMessageStream()
    {
        return messageStream;
    }
}
