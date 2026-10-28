package cn.lunadeer.deertitle.platform;

import cn.lunadeer.deertitle.domain.PlayerProfile;
import cn.lunadeer.deertitle.domain.Title;

import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Online players only. Placeholder evaluation never opens a database connection. */
public final class PlayerCache {
    public record Snapshot(PlayerProfile profile, Title equipped, Instant expiresAt, long generation, FormattedTitle display) {
        public Snapshot(PlayerProfile profile, Title equipped, Instant expiresAt, long generation) {
            this(profile, equipped, expiresAt, generation, null);
        }
    }
    private record Session(long generation, Snapshot snapshot, boolean refreshing, Instant loadedAt) {}
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong sequence = new java.util.concurrent.atomic.AtomicLong();
    private final Duration maxAge;
    private final Clock clock;

    public PlayerCache() { this(Duration.ofSeconds(15), Clock.systemUTC()); }
    public PlayerCache(Duration maxAge, Clock clock) { this.maxAge = maxAge; this.clock = clock; }

    public long join(UUID player) {
        long generation = sequence.incrementAndGet();
        sessions.put(player, new Session(generation, null, false, null));
        return generation;
    }
    public void quit(UUID player) { sessions.remove(player); }
    public Snapshot get(UUID player) {
        Session session = sessions.get(player);
        return session == null || session.loadedAt() == null || !session.loadedAt().plus(maxAge).isAfter(clock.instant())
                ? null : session.snapshot();
    }
    public Long generation(UUID player) { Session session = sessions.get(player); return session == null ? null : session.generation(); }
    public Map<UUID, Long> online() {
        Map<UUID, Long> result = new java.util.HashMap<>();
        sessions.forEach((uuid, session) -> result.put(uuid, session.generation()));
        return Map.copyOf(result);
    }
    public Long invalidate(UUID player) {
        Session updated = sessions.computeIfPresent(player, (uuid, old) -> new Session(sequence.incrementAndGet(), null, false, null));
        return updated == null ? null : updated.generation();
    }
    public boolean begin(UUID player, long generation) {
        var started = new java.util.concurrent.atomic.AtomicBoolean();
        sessions.computeIfPresent(player, (uuid, old) -> {
            if (old.generation() != generation || old.refreshing()) return old;
            started.set(true);
            return new Session(generation, old.snapshot(), true, old.loadedAt());
        });
        return started.get();
    }
    public void finish(UUID player, long generation) {
        sessions.computeIfPresent(player, (uuid, old) -> old.generation() == generation
                ? new Session(generation, old.snapshot(), false, old.loadedAt()) : old);
    }
    public void put(UUID player, Snapshot snapshot) {
        // Session check, insertion and refresh completion must be one atomic per-player action.
        sessions.computeIfPresent(player, (uuid, old) -> old.generation() == snapshot.generation()
                ? new Session(old.generation(), snapshot, false, clock.instant()) : old);
    }
    public void clear() { sessions.clear(); }
}
