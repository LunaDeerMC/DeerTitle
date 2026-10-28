package cn.lunadeer.deertitle.domain;

import java.util.UUID;
import java.time.Instant;

public record ImportOwnership(String legacyId, UUID playerId, String legacyTitleId, Instant expiresAt) {}
