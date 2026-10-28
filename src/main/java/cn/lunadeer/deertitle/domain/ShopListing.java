package cn.lunadeer.deertitle.domain;

import java.time.Instant;
import java.math.BigDecimal;

public record ShopListing(long id, Title title, Currency currency, BigDecimal price, Long totalStock, long sold, Instant startsAt, Instant endsAt, Integer grantDurationDays, boolean enabled, long version) {
    public boolean available(Instant now) { return enabled && title.enabled() && (startsAt == null || !startsAt.isAfter(now)) && (endsAt == null || endsAt.isAfter(now)) && (totalStock == null || sold < totalStock); }
    public Long remainingStock() { return totalStock == null ? null : Math.max(0L, totalStock - sold); }
}
