package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompConnectionStatus;
import com.connector.common.stomp.constant.StompFrameType;
import com.connector.common.stomp.constant.StompHeaders;
import com.connector.common.stomp.constant.StompTransactionStatus;
import com.connector.common.stomp.constant.StompVersion;
import com.connector.common.stomp.internal.model.StompFrame;
import reactor.core.Disposable;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class StompClientTransaction<StompConnectConfig extends IStompConnectConfig, StompDisconnectConfig, StompSessionInfo extends IStompSessionInfo, TransportPayload> extends StompClientDelegate<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportPayload> implements IStompTransaction<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportPayload>
{
    private final String                       transactionId;
    private final Many<StompTransactionStatus> transactionStatusPublisher;

    private StompTransactionStatus transactionStatus;
    private Disposable             deliverMessageDispose;
    private Flux<StompFrame>       messageStream;

    protected StompClientTransaction(String transactionId, IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportPayload> stompClient)
    {
        super(stompClient);

        registerWrappedRequestHandler(msg -> {
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
            return msg;
        });

        this.transactionId = transactionId;
        this.transactionStatusPublisher = Sinks.many().replay().latest();
        transactionStatusPublisher.tryEmitNext(StompTransactionStatus.UNINITIALIZED).orThrow();
        transactionStatusStream().subscribe(status -> this.transactionStatus = status);
        getDelegatee().connectionStatusStream().doOnNext(status -> {
            if (status == StompConnectionStatus.DISCONNECTED && getTransactionStatus() == StompTransactionStatus.IN_PROGRESS)
            {
                setTransactionStatus(StompTransactionStatus.ABORTED);
            }
        }).subscribe();
    }

    @Override
    public String describeClient()
    {
        return MessageFormat.format("transaction[id = {0}] -> {1} ", getTransactionId(), getDelegatee().describeClient());
    }

    @Override
    public synchronized String begin(String receiptId) throws Throwable
    {
        if (getTransactionStatus() == StompTransactionStatus.IN_PROGRESS)
        {
            return null;
        }

        receiptId = sendStompMessage(populateRequest(new StompFrame(null, null, StompFrameType.BEGIN, null)), receiptId);

        if (receiptId != null)
        {
            waitReceipt(receiptId).subscribe(frame -> setInProgress(), err -> setAborted(), this::setAborted);
        }
        else
        {
            setInProgress();
        }

        return receiptId;
    }

    @Override
    public synchronized String abort(String receiptId) throws Throwable
    {
        if (getTransactionStatus() != StompTransactionStatus.IN_PROGRESS)
        {
            return null;
        }

        receiptId = sendStompMessage(populateRequest(new StompFrame(null, null, StompFrameType.ABORT, null)), receiptId);

        if (receiptId != null)
        {
            waitReceipt(receiptId).subscribe(frame -> setAborted(), err -> setAborted(), this::setAborted);
        }
        else
        {
            setAborted();
        }

        return receiptId;
    }

    @Override
    public synchronized String commit(String receiptId) throws Throwable
    {
        if (getTransactionStatus() != StompTransactionStatus.IN_PROGRESS)
        {
            return null;
        }

        receiptId = sendStompMessage(populateRequest(new StompFrame(null, null, StompFrameType.COMMIT, null)), receiptId);

        if (receiptId != null)
        {
            waitReceipt(receiptId).subscribe(frame -> setCommitted(), err -> setAborted(), this::setAborted);
        }
        else
        {
            setCommitted();
        }

        return receiptId;
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

    @Override
    public Mono<StompTransactionStatus> waitTransactionStatus(StompTransactionStatus status)
    {
        return Mono.from(transactionStatusStream().filter(s -> s == status));
    }

    private void setTransactionStatus(StompTransactionStatus transactionStatus)
    {
        if (getTransactionStatus() == transactionStatus)
        {
            return;
        }
        transactionStatusPublisher.tryEmitNext(transactionStatus).orThrow();
    }

    @Override
    public void connectDeliverMessage()
    {
        if (deliverMessageDispose != null && !deliverMessageDispose.isDisposed())
        {
            return;
        }
        ConnectableFlux<StompFrame> connectableStream = getDelegatee().deliverMessageStream().filter(msg -> {
            Map<String, String> msgHeaders = msg.getHeaders() == null ? Collections.emptyMap() : msg.getHeaders();
            return getTransactionId().equals(msgHeaders.get(StompHeaders.TRANSACTION));
        }).publish();

        this.messageStream = connectableStream;
        this.deliverMessageDispose = connectableStream.connect();
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
    public Flux<StompFrame> deliverMessageStream()
    {
        return messageStream;
    }

    protected void setAborted()
    {
        disconnectDeliverMessage();

        setTransactionStatus(StompTransactionStatus.ABORTED);
    }

    protected void setInProgress()
    {
        connectDeliverMessage();

        setTransactionStatus(StompTransactionStatus.IN_PROGRESS);
    }

    protected void setCommitted()
    {
        disconnectDeliverMessage();

        setTransactionStatus(StompTransactionStatus.COMMITTED);
    }
}
