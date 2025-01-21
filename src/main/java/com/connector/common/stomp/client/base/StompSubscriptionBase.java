package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompAckMode;
import com.connector.common.stomp.constant.StompConnectionStatus;
import com.connector.common.stomp.constant.StompFrameType;
import com.connector.common.stomp.constant.StompHeaders;
import com.connector.common.stomp.constant.StompSubscriptionStatus;
import com.connector.common.stomp.constant.StompVersion;
import com.connector.common.stomp.internal.model.StompFrame;
import reactor.core.Disposable;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class StompSubscriptionBase<StompConnectConfig extends IStompConnectConfig, StompDisconnectConfig, StompSessionInfo extends IStompSessionInfo, TransportPayload> extends StompClientDelegate<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportPayload> implements IStompSubscription<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportPayload>
{
    private final String                        destination;
    private final String                        id;
    private final StompAckMode                  ackMode;
    private final Many<StompSubscriptionStatus> subscriptionStatusPublisher;

    private Flux<StompFrame>        messageStream;
    private StompSubscriptionStatus subscriptionStatus;
    private IStompResponseHandler   responseAckHandler;
    private Disposable              deliverMessageDispose;

    protected StompSubscriptionBase(IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportPayload> stompClient, String destination, String id, StompAckMode ackMode)
    {
        super(stompClient);

        registerWrappedRequestHandler(msg -> {
            Map<String, String> headers = msg.getHeaders() == null ? new LinkedHashMap<>() : msg.getHeaders();
            switch (msg.getType())
            {
            case SEND:
                headers.put(StompHeaders.DESTINATION, getDestination());
                break;
            case SUBSCRIBE:
                if (getSubscriptionId() != null)
                {
                    headers.put(StompHeaders.ID, getSubscriptionId());
                }
                headers.put(StompHeaders.DESTINATION, getDestination());
                break;
            case UNSUBSCRIBE:
                if (getSubscriptionId() != null)
                {
                    headers.put(StompHeaders.ID, getSubscriptionId());
                    break;
                }
                headers.put(StompHeaders.DESTINATION, getDestination());
                break;
            case ACK:
                // Headers already set after receive the message
                return msg;
            case BEGIN:
            case COMMIT:
            case ABORT:
                // transaction frame types handled by the child class
                return msg;
            case DISCONNECT:
                // generic frame types, not handled this
                return msg;
            case STOMP:
            case CONNECTED:
            case CONNECT:
            case NACK:
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

        this.destination = destination;
        this.id = id;
        this.ackMode = ackMode;
        this.subscriptionStatusPublisher = Sinks.many().replay().latest();
        subscriptionStatusPublisher.tryEmitNext(StompSubscriptionStatus.UNINITIALIZED).orThrow();
        subscriptionStatusStream().subscribe(status -> this.subscriptionStatus = status);
        getDelegatee().connectionStatusStream().doOnNext(status -> {
            if (status == StompConnectionStatus.DISCONNECTED)
            {
                setSubscriptionStatus(StompSubscriptionStatus.UNSUBSCRIBED);
            }
        }).subscribe();
    }

    @Override
    public void connectDeliverMessage()
    {
        if (deliverMessageDispose != null && !deliverMessageDispose.isDisposed())
        {
            return;
        }
        ConnectableFlux<StompFrame> connectableStream = getDelegatee().deliverMessageStream().filter(msg -> {
            Map<String, String> msgHeaders = msg.getHeaders();
            boolean fromServer = msg.getType().fromServer();
            boolean containMessageId = msgHeaders.containsKey(StompHeaders.MESSAGE_ID);
            boolean isDestinationMatch = getDestination().equals(msgHeaders.get(StompHeaders.DESTINATION));
            if (!fromServer || !containMessageId || !isDestinationMatch)
            {
                return false;
            }
            if (StompVersion.STOMP_1_0.equals(getConnectedSessionInfo().getVersion()))
            {
                return getSubscriptionId() == null && !msgHeaders.containsKey(StompHeaders.SUBSCRIPTION) || getSubscriptionId() != null && getSubscriptionId().equals(msgHeaders.get(StompHeaders.SUBSCRIPTION));
            }
            else
            {
                return getSubscriptionId().equals(msgHeaders.get(StompHeaders.SUBSCRIPTION));
            }
        }).flatMap(msg -> {
            try
            {
                return ackMessage(msg) ? Mono.just(msg) : Mono.empty();
            }
            catch (Throwable e)
            {
                return Mono.error(e);
            }
        }).publish();

        this.messageStream = connectableStream;
        this.deliverMessageDispose = connectableStream.connect();
    }

    public IStompResponseHandler getResponseAckHandler()
    {
        return responseAckHandler;
    }

    @Override
    public void registerResponseHandler(IStompResponseHandler responseHandler)
    {
        if (this.responseAckHandler != null)
        {
            return;
        }
        this.responseAckHandler = responseHandler;
    }

    @Override
    public String describeClient()
    {
        return MessageFormat.format("subscription[id = {0}, destination = {1}] -> {2} ", getSubscriptionId() == null ? "<empty>" : getSubscriptionId(), getDestination(), getDelegatee().describeClient());
    }

    @Override
    public String getDestination()
    {
        return destination;
    }

    @Override
    public Flux<StompFrame> deliverMessageStream()
    {
        return messageStream;
    }

    @Override
    public String getSubscriptionId()
    {
        return id;
    }

    @Override
    public StompAckMode getAckMode()
    {
        return ackMode;
    }

    @Override
    public synchronized String subscribe(String receiptId) throws Throwable
    {
        if (getSubscriptionStatus() == StompSubscriptionStatus.SUBSCRIBED)
        {
            return null;
        }

        receiptId = sendStompMessage(new StompFrame(null, null, StompFrameType.SUBSCRIBE, null), receiptId);

        if (receiptId != null)
        {
            waitReceipt(receiptId).subscribe(frame -> setSubscribed(), err -> setUnsubscribed(), this::setUnsubscribed);
        }
        else
        {
            setSubscribed();
        }

        return receiptId;
    }

    @Override
    public synchronized String unsubscribe(String receiptId) throws Throwable
    {
        if (getSubscriptionStatus() != StompSubscriptionStatus.SUBSCRIBED)
        {
            return null;
        }

        receiptId = sendStompMessage(new StompFrame(null, null, StompFrameType.UNSUBSCRIBE, null), receiptId);

        if (receiptId != null)
        {
            waitReceipt(receiptId).subscribe(frame -> setUnsubscribed(), err -> setUnsubscribed(), this::setUnsubscribed);
        }
        else
        {
            setUnsubscribed();
        }
        return receiptId;
    }

    @Override
    public Mono<StompSubscriptionStatus> waitSubscriptionStatus(StompSubscriptionStatus status)
    {
        return Mono.from(subscriptionStatusStream().filter(s -> s == status));
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
    public StompSubscriptionStatus getSubscriptionStatus()
    {
        return subscriptionStatus;
    }

    @Override
    public Flux<StompSubscriptionStatus> subscriptionStatusStream()
    {
        return subscriptionStatusPublisher.asFlux();
    }

    protected void setSubscriptionStatus(StompSubscriptionStatus subscriptionStatus)
    {
        if (subscriptionStatus == getSubscriptionStatus())
        {
            return;
        }
        subscriptionStatusPublisher.tryEmitNext(subscriptionStatus).orThrow();
    }

    protected void setUnsubscribed()
    {
        disconnectDeliverMessage();

        setSubscriptionStatus(StompSubscriptionStatus.UNSUBSCRIBED);
    }

    protected void setSubscribed()
    {
        connectDeliverMessage();

        setSubscriptionStatus(StompSubscriptionStatus.SUBSCRIBED);
    }
}
