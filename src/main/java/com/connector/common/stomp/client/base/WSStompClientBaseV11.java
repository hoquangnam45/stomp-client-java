package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompConnectionStatus;
import com.connector.common.stomp.constant.StompFrameType;
import com.connector.common.stomp.constant.StompHeaders;
import com.connector.common.stomp.constant.StompVersion;
import com.connector.common.stomp.internal.config.StompConnectConfigV11;
import com.connector.common.stomp.internal.config.StompDisconnectConfigV11;
import com.connector.common.stomp.internal.model.StompFrame;
import com.connector.common.stomp.internal.model.StompSessionInfoV11;
import com.connector.common.websocket.client.base.IWSClient;
import com.connector.common.websocket.internal.model.WSRawMessage;
import com.connector.common.websocket.internal.model.WSRequest;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class WSStompClientBaseV11<StompConnectConfig extends StompConnectConfigV11<StompDisconnectConfig>, StompDisconnectConfig extends StompDisconnectConfigV11> extends WSStompClientBase<StompConnectConfig, StompDisconnectConfig, StompSessionInfoV11>
{
    private final Scheduler healthyCheckThread;

    // session infos
    private StompSessionInfoV11 sessionInfo;
    private OffsetDateTime      lastReceivedTimestamp;

    protected WSStompClientBaseV11(String stompClientId, IWSClient<?, ?> wsClient, Scheduler healthyCheckThread)
    {
        super(stompClientId, wsClient);
        this.healthyCheckThread = healthyCheckThread;
        this.sessionInfo = null;
    }

    @Override
    public synchronized void connectStomp(StompConnectConfig stompConnectConfig) throws Throwable
    {
        if (getConnectionStatus() == StompConnectionStatus.CONNECTED)
        {
            return;
        }

        this.lastReceivedTimestamp = null;
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
        String receipt;
        if (stompDisconnectConfig == null)
        {
            sendDisconnect = true;
            receipt = null;
        }
        else
        {
            sendDisconnect = stompDisconnectConfig.isSendDisconnect();
            receipt = stompDisconnectConfig.getReceipt();
        }
        if (sendDisconnect)
        {
            LinkedHashMap<String, String> headers = new LinkedHashMap<>();
            if (receipt != null)
            {
                headers.put(StompHeaders.RECEIPT, receipt);
            }
            sendStompMessage(new StompFrame(headers, null, StompFrameType.DISCONNECT, null));
            if (receipt != null)
            {
                Duration receiptWaitTimeout = stompDisconnectConfig.getWaitReceiptDuration() == null ? Duration.ofMillis(10000) : stompDisconnectConfig.getWaitReceiptDuration();
                deliverMessageStream().filter(msg -> {
                    boolean isReceipt = msg.getType() == StompFrameType.RECEIPT;
                    String receiptId = msg.getHeaders().get(StompHeaders.RECEIPT_ID);
                    return isReceipt && receipt.equals(receiptId);
                }).blockFirst(receiptWaitTimeout);
            }
        }

        this.sessionInfo = null;

        setConnectionStatus(StompConnectionStatus.DISCONNECTED);

        disconnectDeliverMessage();
    }

    @Override
    public List<StompVersion> acceptedVersions()
    {
        return Arrays.asList(StompVersion.STOMP_1_1, StompVersion.STOMP_1_2);
    }

    @Override
    public StompSessionInfoV11 getConnectedSessionInfo()
    {
        return sessionInfo;
    }

    @Override
    public void sendRawMessage(WSRequest rawMessage) throws Throwable
    {
        super.sendRawMessage(rawMessage);
    }

    @Override
    public String getClientId()
    {
        return MessageFormat.format("stomp[ver = 1.1 + 1.2]{0}", super.getClientId());
    }

    @Override
    protected boolean trimHeaders()
    {
        return false;
    }

    public OffsetDateTime getLastReceivedTimestamp()
    {
        return lastReceivedTimestamp;
    }

    private void setLastReceivedTimestamp(OffsetDateTime v)
    {
        OffsetDateTime lastReceivedTimestamp = getLastReceivedTimestamp();
        if (v == null || lastReceivedTimestamp != null && lastReceivedTimestamp.isAfter(v))
        {
            return;
        }
        this.lastReceivedTimestamp = v;
    }

    private void connectWithConfig(StompConnectConfig connectConfig) throws Throwable
    {
        Map<String, String> connectHeaders = new LinkedHashMap<>();
        if (connectConfig.getHeartbeatClient() != null && connectConfig.getHeartbeatServer() != null)
        {
            String heartbeat = MessageFormat.format("{0},{1}", String.valueOf(connectConfig.getHeartbeatClient().toMillis()), String.valueOf(connectConfig.getHeartbeatServer().toMillis()));
            connectHeaders.put(StompHeaders.HEARTBEAT, heartbeat);
        }
        List<StompVersion> acceptedVersions = connectConfig.getAcceptedVersions();
        if (acceptedVersions == null || acceptedVersions.isEmpty())
        {
            acceptedVersions = acceptedVersions();
        }
        connectHeaders.put(StompHeaders.HOST, connectConfig.getHost());
        connectHeaders.put(StompHeaders.ACCEPT_VERSION, acceptedVersions.stream().map(StompVersion::getVersion).collect(Collectors.joining(",")));
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

        setLastReceivedTimestamp(OffsetDateTime.now());
        Map<String, String> connectedHeaders = connectedFrame.getHeaders() == null ? Collections.emptyMap() : connectedFrame.getHeaders();

        // set session info
        String sessionId = connectedHeaders.get(StompHeaders.SESSION);
        String server = connectedHeaders.get(StompHeaders.SERVER);
        String versionString = connectedHeaders.get(StompHeaders.VERSION);
        StompVersion version = StompVersion.parse(versionString);
        if (version == null || !acceptedVersions().contains(version))
        {
            throw new IllegalStateException(MessageFormat.format("Version of stomp server is not supported {0}", versionString));
        }

        String connectHeartbeat = connectHeaders.get(StompHeaders.HEARTBEAT);
        String connectedHeartbeat = connectedHeaders.get(StompHeaders.HEARTBEAT);
        Entry<Duration, Duration> clientHeartbeat = getHeartbeat(connectHeartbeat);
        Entry<Duration, Duration> serverHeartbeat = getHeartbeat(connectedHeartbeat);

        if (serverHeartbeat != null)
        {
            startHeartbeat(connectConfig.getDisconnectConfig(), clientHeartbeat == null ? null : clientHeartbeat.getKey(), clientHeartbeat == null ? null : clientHeartbeat.getValue(), serverHeartbeat.getKey(), serverHeartbeat.getValue());
        }

        this.sessionInfo = new StompSessionInfoV11(version, sessionId, server, clientHeartbeat, serverHeartbeat, connectConfig, stompMessage, connectedFrame);
    }

    private Disposable scheduleHealthyCheck(StompDisconnectConfig disconnectConfig, Duration clientReceiveHeartbeatIntervalSuggestion, Duration serverSendHeartbeatIntervalMin, Scheduler healthyCheckThread)
    {
        Duration clientReceiveHeartbeatPollingInterval = minDuration(clientReceiveHeartbeatIntervalSuggestion, serverSendHeartbeatIntervalMin);
        Duration clientReceiveHeartbeatAllowInterval = maxDuration(clientReceiveHeartbeatIntervalSuggestion, serverSendHeartbeatIntervalMin);
        return healthyCheckThread.schedulePeriodically(() -> {
            OffsetDateTime lastReceivedTimestamp = getLastReceivedTimestamp();
            Duration receivedInactivityPeriod = Duration.between(lastReceivedTimestamp, OffsetDateTime.now());
            if (receivedInactivityPeriod.compareTo(clientReceiveHeartbeatAllowInterval) > 0)
            {
                try
                {
                    disconnectStomp(disconnectConfig);
                }
                catch (Throwable e)
                {
                    throw new RuntimeException(e);
                }
            }
        }, 0, clientReceiveHeartbeatPollingInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void startHeartbeat(StompDisconnectConfig stompDisconnectConfig, Duration clientSendHeartbeatIntervalMin, Duration clientReceiveHeartbeatIntervalSuggestion, Duration serverSendHeartbeatIntervalMin, Duration serverReceiveHeartbeatIntervalSuggestion)
    {
        boolean shouldClientSendHeartbeat = clientSendHeartbeatIntervalMin != null && clientSendHeartbeatIntervalMin.compareTo(Duration.ZERO) > 0 && serverReceiveHeartbeatIntervalSuggestion != null && serverReceiveHeartbeatIntervalSuggestion.compareTo(Duration.ZERO) > 0;
        boolean shouldClientReceiveHeartbeat = clientReceiveHeartbeatIntervalSuggestion != null && clientReceiveHeartbeatIntervalSuggestion.compareTo(Duration.ZERO) > 0 && serverSendHeartbeatIntervalMin != null && serverSendHeartbeatIntervalMin.compareTo(Duration.ZERO) > 0;

        if (!shouldClientSendHeartbeat && !shouldClientReceiveHeartbeat)
        {
            return;
        }

        Disposable healthyCheck;
        Disposable setReceivedTimestampDispose;
        if (shouldClientReceiveHeartbeat)
        {
            setReceivedTimestampDispose = getWsClient().responseStream().doOnNext(_r -> setLastReceivedTimestamp(OffsetDateTime.now())).subscribe();
            healthyCheck = scheduleHealthyCheck(stompDisconnectConfig, clientReceiveHeartbeatIntervalSuggestion, serverSendHeartbeatIntervalMin, healthyCheckThread);
        }
        else
        {
            setReceivedTimestampDispose = null;
            healthyCheck = null;
        }

        Disposable heartbeat;
        if (shouldClientSendHeartbeat)
        {
            heartbeat = scheduleHeartbeat(stompDisconnectConfig, clientSendHeartbeatIntervalMin, serverReceiveHeartbeatIntervalSuggestion, healthyCheckThread);
        }
        else
        {
            heartbeat = null;
        }

        deliverMessageStream().filter(msg -> msg.getType() == StompFrameType.ERROR).doOnNext(msg -> disposeHeartbeat(heartbeat, healthyCheck, setReceivedTimestampDispose, healthyCheckThread)).doOnComplete(() -> disposeHeartbeat(heartbeat, healthyCheck, setReceivedTimestampDispose, healthyCheckThread)).doOnError(e -> disposeHeartbeat(heartbeat, healthyCheck, setReceivedTimestampDispose, healthyCheckThread)).subscribe();
    }

    private Disposable scheduleHeartbeat(StompDisconnectConfig disconnectConfig, Duration clientSendHeartbeatIntervalMin, Duration serverReceiveHeartbeatIntervalSuggestion, Scheduler healthyCheckThread)
    {
        Duration clientSendHeartbeatPollingInterval = minDuration(clientSendHeartbeatIntervalMin, serverReceiveHeartbeatIntervalSuggestion);
        return healthyCheckThread.schedulePeriodically(() -> {
            try
            {
                sendRawMessage(WSRequest.message(WSRawMessage.text("\n")));
            }
            catch (Throwable e)
            {
                logger.error("Failed to send heartbeat: {}", e.getMessage(), e);
                try
                {
                    disconnectStomp(disconnectConfig);
                }
                catch (Throwable ex)
                {
                    logger.error("Failed to disconnect stomp client[{}]", getClientId(), ex);
                }
            }
        }, 0, clientSendHeartbeatPollingInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static Entry<Duration, Duration> getHeartbeat(String heartbeat)
    {
        if (heartbeat == null || heartbeat.isEmpty())
        {
            return null;
        }

        String[] values = heartbeat.split(",");

        if (values.length != 2)
        {
            throw new IllegalArgumentException("Invalid heart-beat value " + heartbeat);
        }
        Duration sendHeartbeatIntervalMin = Duration.ofMillis(Long.parseLong(values[0]));
        Duration receiveHeartbeatIntervalSuggestion = Duration.ofMillis(Long.parseLong(values[1]));
        return new SimpleImmutableEntry<>(sendHeartbeatIntervalMin, receiveHeartbeatIntervalSuggestion);
    }

    private static void disposeHeartbeat(Disposable heartbeat, Disposable healthyCheck, Disposable setReceivedTimestampDispose, Scheduler healthyCheckThread)
    {
        if (setReceivedTimestampDispose != null && setReceivedTimestampDispose.isDisposed())
        {
            setReceivedTimestampDispose.dispose();
        }

        if (heartbeat != null && heartbeat.isDisposed())
        {
            heartbeat.dispose();
        }

        if (healthyCheck != null && healthyCheck.isDisposed())
        {
            healthyCheck.dispose();
        }

        if (healthyCheckThread.isDisposed())
        {
            healthyCheckThread.dispose();
        }
    }

    private static Duration minDuration(Duration a, Duration b)
    {
        return a.compareTo(b) < 0 ? a : b;
    }

    private static Duration maxDuration(Duration a, Duration b)
    {
        return a.compareTo(b) > 0 ? a : b;
    }

}