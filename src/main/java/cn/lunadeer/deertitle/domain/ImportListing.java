package cn.lunadeer.deertitle.domain;

import java.time.Instant;
import java.math.BigDecimal;

public record ImportListing(String legacyId, String legacyTitleId, BigDecimal price, Integer grantDurationDays, Long remainingStock, Instant endsAt, boolean enabled) {}
