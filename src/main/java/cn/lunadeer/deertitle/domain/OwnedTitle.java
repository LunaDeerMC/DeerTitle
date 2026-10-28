package cn.lunadeer.deertitle.domain;

import java.time.Instant;

public record OwnedTitle(Title title, Instant expiresAt) {
    public boolean expired(Instant now) { return expiresAt != null && !expiresAt.isAfter(now); }
}
