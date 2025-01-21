package com.connector.common.stomp;

import com.connector.common.stomp.client.WSStompClientV11;
import com.connector.common.stomp.client.WSStompSubscriptionV11;
import com.connector.common.stomp.client.WSStompTransactionV11;
import com.connector.common.stomp.constant.StompAckMode;
import com.connector.common.stomp.constant.StompConnectionStatus;
import com.connector.common.stomp.constant.StompFrameType;
import com.connector.common.stomp.constant.StompSubscriptionStatus;
import com.connector.common.stomp.constant.StompVersion;
import com.connector.common.stomp.internal.config.StompConnectConfigV11;
import com.connector.common.websocket.client.WSClient;
import com.connector.common.websocket.constant.WSStatus;
import com.connector.common.websocket.internal.config.WSConnectConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executors;

public class StompClientTest
{
    private static final String TOPIC           = "/topic/public/market/snapshot";
    private static final String SUBSCRIPTION_ID = "ABC";
    private static final String TRANSACTION_ID  = "123";

    private static final WSClient               wsClient                       = new WSClient("test-client");
    private static final WSStompClientV11       stompClient                    = new WSStompClientV11("stomp-client", wsClient);
    private static final WSStompSubscriptionV11 wsStompSubscription            = new WSStompSubscriptionV11(TOPIC, SUBSCRIPTION_ID, StompAckMode.CLIENT, stompClient);
    private static final WSStompTransactionV11  wsStompTransactionSubscription = new WSStompTransactionV11(wsStompSubscription, TRANSACTION_ID);
    private static final WSStompTransactionV11  wsStompTransaction             = new WSStompTransactionV11(stompClient, TRANSACTION_ID);
    private static final Logger                 logger                         = LoggerFactory.getLogger(StompClientTest.class);
    private static final ObjectMapper           objectMapper                   = new ObjectMapper();

    public static void main(String[] args) throws Throwable
    {
        logger.info(wsStompTransaction.describeClient());
        logger.info(wsStompTransactionSubscription.describeClient());

        wsClient.connect(new WSConnectConfig("wss://dev.ex.io/feeds", client -> client, req -> req));
        wsClient.waitConnectionStatus(WSStatus.OPEN).block();

        stompClient.connectStomp(new StompConnectConfigV11<>(null, "dev.ex.io", Arrays.asList(StompVersion.STOMP_1_0, StompVersion.STOMP_1_1, StompVersion.STOMP_1_2), null, null, Duration.ofSeconds(60), Duration.ofSeconds(60), Executors.newSingleThreadScheduledExecutor()));
        stompClient.waitConnectionStatus(StompConnectionStatus.CONNECTED).block();

        // Log heartbeat of stomp client
        stompClient.deliverMessageStream().doOnNext(msg -> {
            if (msg.getType() == StompFrameType.HEARTBEAT)
            {
                logger.info("Heartbeat received {}", OffsetDateTime.now());
            }
        }).subscribe();

        // Create subscription and subscribe to a stomp topic
        wsStompSubscription.registerAdditionalRequestHandler(req -> {
            // Perform additional processing on the received message here
            return req;
        });
        wsStompSubscription.registerResponseHandler(resp -> {
            // Perform additional processing on the received response before ack message, for example persist to database, if error return exception to nack the response, etc...
            return null;
        });
        wsStompSubscription.waitReceipt(wsStompSubscription.subscribe(null)).block();

        // Start a processor to receive and process messages stream
        Disposable d = wsStompSubscription.deliverMessageStream().mapNotNull(msg -> {
            try
            {
                if (msg.getBody() == null)
                {
                    return null;
                }
                return objectMapper.readValue(msg.getBody(), MarketSnapshotEvent.class);
            }
            catch (JsonProcessingException e)
            {
                logger.error("Error while parsing message. Reason: {}", e.getMessage(), e);
                return null;
            }
        }).doOnNext(msg -> {
            try
            {
                logger.info("Processor #1: {}", objectMapper.writeValueAsString(msg));
            }
            catch (Throwable e)
            {
                throw new RuntimeException(e);
            }
        }).doOnError(Throwable::printStackTrace).subscribe();

        // Start a secondary processor for backup process topic message stream or specialized processing
        Disposable secondaryD = wsStompSubscription.deliverMessageStream().doOnNext(msg -> {
            logger.info("Processor #2: {}", msg.getBody());
        }).subscribe();

        // Unsubscribe from processing message stream if error happened somewhere in the main processing stream
        wsStompSubscription.waitSubscriptionStatus(StompSubscriptionStatus.UNSUBSCRIBED).subscribe(v -> {
            logger.info("Unsubscribed from stomp topic");

            // Destroy processor
            d.dispose();
            secondaryD.dispose();
        });
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