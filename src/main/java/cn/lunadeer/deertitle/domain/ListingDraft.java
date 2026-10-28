package cn.lunadeer.deertitle.domain;

import java.time.Instant;
import java.math.BigDecimal;

public record ListingDraft(long titleId, Currency currency, BigDecimal price, Long totalStock, Instant startsAt, Instant endsAt, Integer grantDurationDays, boolean enabled) {}
