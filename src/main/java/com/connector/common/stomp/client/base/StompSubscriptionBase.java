package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompAckMode;
import com.connector.common.stomp.constant.StompConnectionStatus;
import com.connector.common.stomp.constant.StompFrameType;
import com.connector.common.stomp.constant.StompHeaders;
import com.connector.common.stomp.constant.StompSubscriptionStatus;
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
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class StompSubscriptionBase<StompConnectConfig extends IStompConnectConfig, StompDisconnectConfig, StompSessionInfo extends IStompSessionInfo, TransportReq, TransportResp> extends StompClientDelegate<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportReq, TransportResp> implements IStompSubscription<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportReq, TransportResp>
{
    protected final Logger                        logger = LoggerFactory.getLogger(getClass());
    private final   String                        destination;
    private final   String                        id;
    private final   StompAckMode                  ackMode;
    private final   Many<StompSubscriptionStatus> subscriptionStatusPublisher;

    private IStompRequestHandler        requestHandler;
    private StompSubscriptionStatus     subscriptionStatus;
    private Disposable                  deliverMessageDispose;
    private ConnectableFlux<StompFrame> messageStream;

    protected StompSubscriptionBase(IStompClient<StompConnectConfig, StompDisconnectConfig, StompSessionInfo, TransportReq, TransportResp> stompClient, String destination, String id, StompAckMode ackMode)
    {
        super(stompClient);
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
        }).doOnError(e -> setSubscriptionStatus(StompSubscriptionStatus.UNSUBSCRIBED)).doOnComplete(() -> setSubscriptionStatus(StompSubscriptionStatus.UNSUBSCRIBED)).subscribe();

    }

    public abstract boolean deliverMessage(StompFrame message) throws Throwable;

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
    public void connectDeliverMessage()
    {
        if (getSubscriptionStatus() != StompSubscriptionStatus.SUBSCRIBED)
        {
            return;
        }
        if (deliverMessageDispose != null && !deliverMessageDispose.isDisposed())
        {
            return;
        }
        this.messageStream = getDelegatee().deliverMessageStream().filter(msg -> {
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
        }).filter(msg -> {
            try
            {
                return deliverMessage(msg);
            }
            catch (Throwable e)
            {
                logger.error("Error while delivering message. Reason: {}", e.getMessage(), e);
                return false;
            }
        }).doOnError(err -> disconnectDeliverMessage()).doOnComplete(this::disconnectDeliverMessage).publish();

        this.deliverMessageDispose = messageStream.connect();
    }

    @Override
    public String getClientId()
    {
        return MessageFormat.format("subscription[id = {0}, destination = {1}] -> {2} ", getSubscriptionId() == null ? "<empty>" : getSubscriptionId(), getDestination(), getDelegatee().getClientId());
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
    public synchronized void subscribe() throws Throwable
    {
        if (getSubscriptionStatus() == StompSubscriptionStatus.SUBSCRIBED)
        {
            return;
        }
        sendStompMessage(new StompFrame(null, null, StompFrameType.SUBSCRIBE, null));

        setSubscriptionStatus(StompSubscriptionStatus.SUBSCRIBED);

        connectDeliverMessage();
    }

    @Override
    public synchronized void unsubscribe() throws Throwable
    {
        if (getSubscriptionStatus() != StompSubscriptionStatus.SUBSCRIBED)
        {
            return;
        }
        sendStompMessage(new StompFrame(null, null, StompFrameType.UNSUBSCRIBE, null));

        setSubscriptionStatus(StompSubscriptionStatus.UNSUBSCRIBED);

        disconnectDeliverMessage();
    }

    @Override
    public void disconnectDeliverMessage()
    {
        if (getSubscriptionStatus() == StompSubscriptionStatus.UNINITIALIZED)
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
    public StompSubscriptionStatus getSubscriptionStatus()
    {
        return subscriptionStatus;
    }

    @Override
    public Flux<StompSubscriptionStatus> subscriptionStatusStream()
    {
        return subscriptionStatusPublisher.asFlux();
    }

    @Override
    public StompFrame populateRequest(StompFrame msg)
    {
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
        if (requestHandler != null)
        {
            msg = requestHandler.populateRequest(msg);
        }
        return msg;
    }

    protected void setSubscriptionStatus(StompSubscriptionStatus subscriptionStatus)
    {
        subscriptionStatusPublisher.tryEmitNext(subscriptionStatus).orThrow();
    }
}
