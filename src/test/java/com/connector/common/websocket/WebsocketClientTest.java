package com.connector.common.websocket;

import com.connector.common.websocket.client.WSClient;
import com.connector.common.websocket.constant.WSStatus;
import com.connector.common.websocket.internal.config.WSConnectConfig;
import com.connector.common.websocket.internal.config.WSDisconnectConfig;
import com.connector.common.websocket.internal.model.WSRawMessage;
import com.connector.common.websocket.internal.model.WSResponse;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient.Builder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class WebsocketClientTest
{
    private final WSClient client = new WSClient("test-client");

    @BeforeEach
    public void setUp() throws Throwable
    {
        WSConnectConfig connectConfig = new WSConnectConfig("wss://ws.postman-echo.com/raw", client -> client, req -> req);
        client.connect(connectConfig);
        client.waitConnectionStatus(WSStatus.OPEN).block();
    }

    @AfterEach
    public void tearDown() throws Throwable
    {
        client.disconnect(new WSDisconnectConfig(false, 1000, "Normal disconnect"));
        client.waitConnectionStatus(WSStatus.CLOSED).block();
    }

    @Test
    public void testWebsocketEcho()
    {
        String msg = "Hello world!";
        client.sendMessage(WSRawMessage.text(msg));
        WSResponse receivedResponse = client.responseStream().blockFirst(Duration.ofSeconds(3));
        assert receivedResponse != null;
        Assertions.assertEquals(msg, receivedResponse.getBody().getStringData());
    }

    @Test
    public void testWebsocketSendReceiveMultiple() throws Throwable
    {
        List<WSResponse> receivedMessages = new ArrayList<>();
        client.responseStream().take(10).doOnNext(receivedMessages::add).subscribe();
        for (int i = 0; i < 10; i++)
        {
            String msg = "Hello world #" + i;
            client.sendMessage(WSRawMessage.text(msg));
        }

        Duration waitPeriod = Duration.ofSeconds(30);
        OffsetDateTime startTime = OffsetDateTime.now();
        while (receivedMessages.size() < 10 && Duration.between(startTime, OffsetDateTime.now()).compareTo(waitPeriod) < 0)
        {
            Thread.sleep(500);
        }
        client.disconnect(new WSDisconnectConfig(false, 1000, "force disconnect"));
        client.waitConnectionStatus(WSStatus.CLOSED).block();

        Assertions.assertEquals(10, receivedMessages.size());
        int i = 0;
        List<String> receivedMessagesStr = receivedMessages.stream().map(WSResponse::getBody).map(WSRawMessage::getStringData).collect(Collectors.toList());
        for (String response : receivedMessagesStr)
        {
            Assertions.assertEquals("Hello world #" + i++, response);
        }
    }

    @Test
    public void testWebsocketStatusTransition() throws Throwable
    {
        List<WSStatus> statusTransitions = new ArrayList<>();

        // Disconnect first to test the status transition from beginning to end
        client.disconnect(new WSDisconnectConfig(false, 1000, "Normal disconnect"));
        client.waitConnectionStatus(WSStatus.CLOSED).block();

        client.socketStatusStream().doOnNext(statusTransitions::add).subscribe();
        client.connect(new WSConnectConfig("wss://ws.postman-echo.com/raw", client -> new Builder(client).dispatcher(new Dispatcher(Executors.newSingleThreadScheduledExecutor())).build(), req -> req));
        client.waitConnectionStatus(WSStatus.OPEN).block();

        client.disconnect(new WSDisconnectConfig(false, 1000, "Normal disconnect"));
        client.waitConnectionStatus(WSStatus.CLOSED).block();

        client.socketStatusStream().filter(status -> status == WSStatus.CLOSED).blockFirst(Duration.ofSeconds(3));

        Assertions.assertEquals(Arrays.asList(WSStatus.CLOSED, WSStatus.CONNECTING, WSStatus.OPEN, WSStatus.CLOSING, WSStatus.CLOSED), statusTransitions);
    }
}

