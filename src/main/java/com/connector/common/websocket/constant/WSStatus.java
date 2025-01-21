package com.connector.common.websocket.constant;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Reference: https://square.github.io/okhttp/3.x/okhttp/okhttp3/WebSocket.html
public enum WSStatus
{
    CONNECTING(false, false, Collections.emptyList()), OPEN(false, true, Arrays.asList(WSLifecycle.MESSAGE, WSLifecycle.OPEN)), CLOSING(false, false, Collections.singletonList(WSLifecycle.CLOSING)), CLOSED(true, false, Collections.singletonList(WSLifecycle.CLOSE)), FAILED(true, false, Collections.singletonList(WSLifecycle.ERROR)), UNINITIALIZED(false, false, Collections.emptyList());

    private static final Map<WSLifecycle, WSStatus> wsLifecycleMap;

    static
    {
        wsLifecycleMap = Stream.of(WSStatus.values()).filter(v -> v.getLifecycles() != null && !v.getLifecycles().isEmpty()).flatMap(sts -> sts.getLifecycles().stream().map(lc -> new AbstractMap.SimpleEntry<>(lc, sts))).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    private final boolean          isFinal;
    private final boolean          isConnected;
    private final Set<WSLifecycle> lifecycles;

    WSStatus(boolean isFinal, boolean isConnected, List<WSLifecycle> lifecycles)
    {
        this.isFinal = isFinal;
        this.isConnected = isConnected;
        this.lifecycles = Collections.unmodifiableSet(new HashSet<>(lifecycles));
    }

    public static WSStatus fromLifecycle(WSLifecycle lifecycle)
    {
        return wsLifecycleMap.get(lifecycle);
    }

    public Set<WSLifecycle> getLifecycles()
    {
        return lifecycles;
    }

    public boolean isFinal()
    {
        return isFinal;
    }

    public boolean isConnected()
    {
        return isConnected;
    }
}
