package com.connector.common.stomp;

import com.connector.common.stomp.client.WSStompClientV11;
import com.connector.common.stomp.client.WSStompSubscriptionV11;
import com.connector.common.stomp.client.WSStompTransactionV11;
import com.connector.common.stomp.constant.StompAckMode;
import com.connector.common.stomp.constant.StompVersion;
import com.connector.common.stomp.internal.config.StompConnectConfigV11;
import com.connector.common.stomp.internal.config.StompDisconnectConfigV11;
import com.connector.common.websocket.client.WSClient;
import com.connector.common.websocket.internal.config.WSConnectConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public class StompClientTest
{
    private static final WSClient               wsClient                       = new WSClient("test-client");
    private static final WSStompClientV11       stompClient                    = new WSStompClientV11("stomp-client", wsClient, Schedulers.boundedElastic());
    private static final WSStompSubscriptionV11 wsStompSubscription            = new WSStompSubscriptionV11("/topic/public/market/snapshot", "ABC", StompAckMode.CLIENT, stompClient);
    private static final WSStompTransactionV11  wsStompTransactionSubscription = new WSStompTransactionV11(wsStompSubscription, "ABC");
    private static final WSStompTransactionV11  wsStompTransaction             = new WSStompTransactionV11(stompClient, "ABC");
    private static final Logger                 logger                         = LoggerFactory.getLogger(StompClientTest.class);
    private static final ObjectMapper           objectMapper                   = new ObjectMapper();

    public static void main(String[] args) throws Throwable
    {
        logger.info(wsStompTransaction.getClientId());
        logger.info(wsStompTransactionSubscription.getClientId());

        wsClient.connect(new WSConnectConfig("wss://dev.ex.io/feeds", Duration.ofSeconds(3), Duration.ofSeconds(3), Duration.ofSeconds(3)), req -> req);
        wsStompSubscription.connectStomp(new StompConnectConfigV11<>(Duration.ofSeconds(20), new StompDisconnectConfigV11(true, null, Duration.ofSeconds(3)), "dev.ex.io", Arrays.asList(StompVersion.STOMP_1_0, StompVersion.STOMP_1_1, StompVersion.STOMP_1_2), null, null, Duration.ofSeconds(60), Duration.ofSeconds(60)));
        wsStompSubscription.registerRequestHandler(req -> {
            // Perform additional processing on the received message here
            return req;
        });
        wsStompSubscription.subscribe();
        wsStompSubscription.deliverMessageStream().map(msg -> {
            try
            {
                return objectMapper.readValue(msg.getBody(), MarketSnapshotEvent.class);
            }
            catch (JsonProcessingException e)
            {
                logger.error("Error while parsing message. Reason: {}", e.getMessage(), e);
                return null;
            }
        }).filter(Objects::nonNull).doOnNext(msg -> {
            try
            {
                logger.info(objectMapper.writeValueAsString(msg));
            }
            catch (Throwable e)
            {
                throw new RuntimeException(e);
            }
        }).doOnError(Throwable::printStackTrace).subscribe();
    }

    public static class MarketSnapshotEvent
    {
        private MarketSnapshotEventType event;
        private MarketSnapshotEventData market;

        public MarketSnapshotEventType getEvent()
        {
            return event;
        }

        public void setEvent(MarketSnapshotEventType event)
        {
            this.event = event;
        }

        public MarketSnapshotEventData getMarket()
        {
            return market;
        }

        public void setMarket(MarketSnapshotEventData market)
        {
            this.market = market;
        }
    }

    public enum MarketSnapshotEventType
    {
        TICK_SNAPSHOT, TICK_UPDATE, BAR_STATS_UPDATE
    }

    public static class MarketSnapshotEventData
    {
        private Long                         ts;
        private Map<String, BigDecimal>      ticks;
        private Map<String, MarketStatEntry> stats;

        public Long getTs()
        {
            return ts;
        }

        public void setTs(Long ts)
        {
            this.ts = ts;
        }

        public Map<String, BigDecimal> getTicks()
        {
            return ticks;
        }

        public void setTicks(Map<String, BigDecimal> ticks)
        {
            this.ticks = ticks;
        }

        public Map<String, MarketStatEntry> getStats()
        {
            return stats;
        }

        public void setStats(Map<String, MarketStatEntry> stats)
        {
            this.stats = stats;
        }
    }

    public static class MarketStatEntry
    {
        private Long d1H;
        private Long d1L;
        private Long d1PrevC;
        private Long d1V;
        private Long ts;

        public Long getD1H()
        {
            return d1H;
        }

        public void setD1H(Long d1H)
        {
            this.d1H = d1H;
        }

        public Long getD1L()
        {
            return d1L;
        }

        public void setD1L(Long d1L)
        {
            this.d1L = d1L;
        }

        public Long getD1PrevC()
        {
            return d1PrevC;
        }

        public void setD1PrevC(Long d1PrevC)
        {
            this.d1PrevC = d1PrevC;
        }

        public Long getD1V()
        {
            return d1V;
        }

        public void setD1V(Long d1V)
        {
            this.d1V = d1V;
        }

        public Long getTs()
        {
            return ts;
        }

        public void setTs(Long ts)
        {
            this.ts = ts;
        }
    }
}