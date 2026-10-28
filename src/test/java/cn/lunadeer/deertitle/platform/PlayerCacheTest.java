package cn.lunadeer.deertitle.platform;

import cn.lunadeer.deertitle.domain.PlayerProfile;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerCacheTest {
    private PlayerCache.Snapshot snapshot(UUID id, long generation) {
        return new PlayerCache.Snapshot(new PlayerProfile(id, "test", null, 0, 0, 0), null, null, generation);
    }
    @Test void oldQueryCannotResurrectQuitOrReconnectedPlayer() {
        PlayerCache cache = new PlayerCache(); UUID id = UUID.randomUUID();
        long old = cache.join(id); assertTrue(cache.begin(id, old)); cache.quit(id);
        cache.put(id, snapshot(id, old)); assertNull(cache.get(id)); assertTrue(cache.online().isEmpty());
        long current = cache.join(id); cache.put(id, snapshot(id, old)); assertNull(cache.get(id));
        cache.put(id, snapshot(id, current)); assertEquals(current, cache.get(id).generation());
    }
    @Test void invalidationRejectsLateDataWithoutBlockingFreshRefresh() {
        PlayerCache cache = new PlayerCache(); UUID id = UUID.randomUUID();
        long old = cache.join(id); assertTrue(cache.begin(id, old));
        long current = cache.invalidate(id); assertTrue(cache.begin(id, current));
        cache.put(id, snapshot(id, old)); cache.finish(id, old);
        assertFalse(cache.begin(id, current));
        cache.put(id, snapshot(id, current)); assertEquals(current, cache.get(id).generation());
    }
    @Test void simultaneousCompletionAndQuitNeverRetainsOfflinePlayer() {
        PlayerCache cache = new PlayerCache();
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (int i = 0; i < 2000; i++) {
                UUID id = UUID.randomUUID(); long generation = cache.join(id);
                CompletableFuture.allOf(CompletableFuture.runAsync(() -> cache.put(id, snapshot(id, generation)), executor),
                        CompletableFuture.runAsync(() -> cache.quit(id), executor)).join();
                assertNull(cache.get(id));
            }
        }
        assertTrue(cache.online().isEmpty());
    }
    @Test void databaseOutageDoesNotDisplayUnboundedStaleSnapshot() {
        var now = new AtomicReference<>(Instant.parse("2026-10-28T00:00:00Z"));
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return now.get(); }
        };
        PlayerCache cache = new PlayerCache(Duration.ofSeconds(15), clock); UUID id = UUID.randomUUID();
        long generation = cache.join(id); cache.put(id, snapshot(id, generation)); assertNotNull(cache.get(id));
        now.updateAndGet(t -> t.plusSeconds(16)); assertNull(cache.get(id));
    }
}
