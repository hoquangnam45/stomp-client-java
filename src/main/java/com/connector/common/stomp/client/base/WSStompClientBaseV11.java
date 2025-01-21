package com.connector.common.stomp.client.base;

import com.connector.common.stomp.constant.StompConnectionStatus;
import com.connector.common.stomp.constant.StompFrameType;
import com.connector.common.stomp.constant.StompHeaders;
import com.connector.common.stomp.constant.StompVersion;
import com.connector.common.stomp.internal.config.StompConnectConfigV11;
import com.connector.common.stomp.internal.exception.StompErrorFrame;
import com.connector.common.stomp.internal.model.StompFrame;
import com.connector.common.stomp.internal.model.StompSessionInfoV11;
import com.connector.common.websocket.client.base.IWSClient;
import com.connector.common.websocket.constant.WSLifecycle;
import com.connector.common.websocket.internal.model.WSRawMessage;
import com.connector.common.websocket.internal.model.WSResponse;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

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
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class WSStompClientBaseV11<StompConnectConfig extends StompConnectConfigV11<StompDisconnectConfig>, StompDisconnectConfig> extends WSStompClientBase<StompConnectConfig, StompDisconnectConfig, StompSessionInfoV11>
{
    // session infos
    private Disposable           healthCheckWorker;
    private Disposable           heartbeatWorker;
    private Supplier<Disposable> heartbeatStart;

    protected WSStompClientBaseV11(String stompClientId, IWSClient<?, ?> wsClient)
    {
        super(stompClientId, wsClient);
    }

    @Override
    public List<StompVersion> acceptedVersions()
    {
        return Arrays.asList(StompVersion.STOMP_1_1, StompVersion.STOMP_1_2);
    }

    @Override
    public void sendRawMessage(WSRawMessage rawMessage) throws Throwable
    {
        super.sendRawMessage(rawMessage);
        if (heartbeatStart != null && heartbeatWorker != null && !heartbeatWorker.isDisposed())
        {
            // reset heartbeat timeout
            heartbeatWorker.dispose();
            this.heartbeatWorker = heartbeatStart.get();
        }
    }

    @Override
    public String describeClient()
    {
        return MessageFormat.format("stomp[ver = 1.1 + 1.2]{0}", super.describeClient());
    }

    @Override
    protected boolean trimHeaders()
    {
        return false;
    }

    @Override
    protected void connectWithConfig(StompConnectConfig connectConfig) throws Throwable
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

        sendStompMessage(stompMessage, null);

        Mono.from(getWsClient().responseStream().filter(resp -> resp.getLifecycle() == WSLifecycle.MESSAGE).map(WSResponse::getBody).map(this::decode).filter(msg -> msg.getType() == StompFrameType.CONNECTED || msg.getType() == StompFrameType.ERROR).flatMap(connectedFrame -> {
            if (connectedFrame.getType() == StompFrameType.ERROR)
            {
                setDisconnected();
                return Mono.error(new StompErrorFrame(connectedFrame, "Failed to connect to server. Client: " + describeClient() + ". Config: " + connectConfig));
            }

            connectDeliverMessage();

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

            Scheduler heartbeatScheduler = Schedulers.fromExecutorService(connectConfig.getHeartbeatScheduledExecutorService(), "heartbeat: " + describeClient());
            startHeartbeat(heartbeatScheduler, connectConfig.getDisconnectConfig(), clientHeartbeat == null ? null : clientHeartbeat.getKey(), serverHeartbeat == null ? null : serverHeartbeat.getValue());

            Scheduler healthCheckScheduler = Schedulers.fromExecutorService(connectConfig.getHeartbeatScheduledExecutorService(), "healthCheck: " + describeClient());
            startHealthCheck(healthCheckScheduler, connectConfig.getDisconnectConfig(), clientHeartbeat == null ? null : clientHeartbeat.getValue(), serverHeartbeat == null ? null : serverHeartbeat.getKey());

            setSessionInfo(new StompSessionInfoV11(version, sessionId, server, clientHeartbeat, serverHeartbeat, connectConfig, stompMessage, connectedFrame));

            setConnectionStatus(StompConnectionStatus.CONNECTED);

            return Mono.just(connectedFrame);
        })).subscribe();
    }

    private Supplier<Disposable> scheduleHealthyCheck(StompDisconnectConfig disconnectConfig, Duration clientReceiveHeartbeatIntervalSuggestion, Duration serverSendHeartbeatIntervalMin, Scheduler healthyCheckExecutorService)
    {
        Duration clientReceiveHeartbeatPollingInterval = minDuration(clientReceiveHeartbeatIntervalSuggestion, serverSendHeartbeatIntervalMin);
        Duration clientReceiveHeartbeatAllowInterval = maxDuration(clientReceiveHeartbeatIntervalSuggestion, serverSendHeartbeatIntervalMin);
        return () -> {
            OffsetDateTime lastReceivedTimestamp = OffsetDateTime.now();
            return healthyCheckExecutorService.schedulePeriodically(() -> {
                Duration receivedInactivityPeriod = Duration.between(lastReceivedTimestamp, OffsetDateTime.now());
                if (receivedInactivityPeriod.compareTo(clientReceiveHeartbeatAllowInterval) > 0)
                {
                    try
                    {
                        disconnectStomp(disconnectConfig, null);
                    }
                    catch (Throwable e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }, 0, clientReceiveHeartbeatPollingInterval.toMillis(), TimeUnit.MILLISECONDS);
        };
    }

    private void startHealthCheck(Scheduler healthCheckScheduler, StompDisconnectConfig stompDisconnectConfig, Duration clientReceiveHeartbeatIntervalSuggestion, Duration serverSendHeartbeatIntervalMin)
    {
        boolean shouldClientHealthCheck = clientReceiveHeartbeatIntervalSuggestion != null && clientReceiveHeartbeatIntervalSuggestion.compareTo(Duration.ZERO) > 0 && serverSendHeartbeatIntervalMin != null && serverSendHeartbeatIntervalMin.compareTo(Duration.ZERO) > 0;
        if (!shouldClientHealthCheck)
        {
            return;
        }

        Supplier<Disposable> healthCheckStart = scheduleHealthyCheck(stompDisconnectConfig, clientReceiveHeartbeatIntervalSuggestion, serverSendHeartbeatIntervalMin, healthCheckScheduler);
        healthCheckWorker = healthCheckStart.get();

        deliverMessageStream().doOnNext(frame -> {
            if (healthCheckWorker != null && !healthCheckWorker.isDisposed())
            {
                healthCheckWorker.dispose();
                healthCheckWorker = healthCheckStart.get();
            }
        }).onErrorComplete().doOnComplete(this::disposeHealthCheck).subscribe();
    }

    private void startHeartbeat(Scheduler heartbeatScheduler, StompDisconnectConfig stompDisconnectConfig, Duration clientSendHeartbeatIntervalMin, Duration serverReceiveHeartbeatIntervalSuggestion)
    {
        boolean shouldClientSendHeartbeat = clientSendHeartbeatIntervalMin != null && clientSendHeartbeatIntervalMin.compareTo(Duration.ZERO) > 0 && serverReceiveHeartbeatIntervalSuggestion != null && serverReceiveHeartbeatIntervalSuggestion.compareTo(Duration.ZERO) > 0;

        if (!shouldClientSendHeartbeat)
        {
            return;
        }

        heartbeatStart = scheduleHeartbeat(stompDisconnectConfig, clientSendHeartbeatIntervalMin, serverReceiveHeartbeatIntervalSuggestion, heartbeatScheduler);
        heartbeatWorker = heartbeatStart.get();

        deliverMessageStream().onErrorComplete().doOnComplete(this::disposeHeartbeat).subscribe();
    }

    private Supplier<Disposable> scheduleHeartbeat(StompDisconnectConfig disconnectConfig, Duration clientSendHeartbeatIntervalMin, Duration serverReceiveHeartbeatIntervalSuggestion, Scheduler healthyCheckThread)
    {
        Duration clientSendHeartbeatPollingInterval = minDuration(clientSendHeartbeatIntervalMin, serverReceiveHeartbeatIntervalSuggestion);
        return () -> healthyCheckThread.schedule(() -> {
            try
            {
                sendRawMessage(WSRawMessage.text("\n"));
            }
            catch (Throwable e)
            {
                try
                {
                    disconnectStomp(disconnectConfig, null);
                }
                catch (Throwable ex)
                {
                    throw new RuntimeException("Failed to disconnect stomp client: " + describeClient(), ex);
                }
                throw new RuntimeException("Failed to send stomp heartbeat: " + describeClient(), e);
            }
        }, clientSendHeartbeatPollingInterval.toMillis(), TimeUnit.MILLISECONDS);
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

    private void disposeHealthCheck()
    {
        if (healthCheckWorker != null && !healthCheckWorker.isDisposed())
        {
            healthCheckWorker.dispose();
            healthCheckWorker = null;
        }
    }

    private void disposeHeartbeat()
    {
        if (heartbeatWorker != null && !heartbeatWorker.isDisposed())
        {
            heartbeatWorker.dispose();
            heartbeatWorker = null;
        }

        this.heartbeatStart = null;
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