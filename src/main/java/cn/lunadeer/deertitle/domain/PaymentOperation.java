package cn.lunadeer.deertitle.domain;

import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;

public record PaymentOperation(UUID id, UUID playerId, PaymentKind kind, PaymentState state, Long listingId, Long titleId, int credits, BigDecimal amount, Integer grantDurationDays, Instant createdAt, String failure) {}
