package cn.lunadeer.deertitle.ui;

import static org.junit.jupiter.api.Assertions.*;

import cn.lunadeer.deertitle.domain.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class FormValuesTest {
    @Test void priceCannotUseFloatingPointSpecialValuesOrFractionalCoins() {
        assertEquals(new BigDecimal("12.34"), FormValues.price("12.34", Currency.VAULT));
        assertEquals(new BigDecimal("12"), FormValues.price("12.00", Currency.COINS));
        for (String value : new String[] {"NaN", "Infinity", "1e9", "-1", "1.001", "1000000000000", ""}) {
            assertThrows(IllegalArgumentException.class, () -> FormValues.price(value, Currency.VAULT), value);
        }
        assertThrows(IllegalArgumentException.class, () -> FormValues.price("1.50", Currency.COINS));
    }

    @Test void remainingStockPreservesSoldCountAndDetectsOverflow() {
        assertEquals(112L, FormValues.totalStock("12", 100));
        assertEquals(100L, FormValues.totalStock("0", 100));
        assertNull(FormValues.totalStock("", 100));
        assertThrows(IllegalArgumentException.class, () -> FormValues.totalStock("9223372036854775807", 1));
        assertThrows(IllegalArgumentException.class, () -> FormValues.totalStock("-1", 100));
    }

    @Test void listingDatesUseConfiguredTimezoneAndStrictCalendar() {
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        assertEquals(Instant.parse("2026-10-28T00:30:00Z"), FormValues.date("2026-10-28 08:30", shanghai));
        assertNull(FormValues.date(" ", shanghai));
        assertThrows(IllegalArgumentException.class, () -> FormValues.date("2026-02-30 08:30", shanghai));
        assertThrows(IllegalArgumentException.class, () -> FormValues.date("2026-10-28 25:00", shanghai));
        Instant now = Instant.parse("2026-10-28T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> FormValues.dateRange(now, now));
        assertThrows(IllegalArgumentException.class, () -> FormValues.dateRange(now, now.minusSeconds(1)));
    }

    @Test void ambiguousOrMissingDstTimeIsRejectedInsteadOfMovedSilently() {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        assertThrows(IllegalArgumentException.class, () -> FormValues.date("2026-03-29 02:30", berlin));
        assertThrows(IllegalArgumentException.class, () -> FormValues.date("2026-10-25 02:30", berlin));
    }

    @Test void ownershipDurationIsIndependentOfSaleWindowAndCountsAreBounded() {
        assertNull(FormValues.duration(""));
        assertEquals(30, FormValues.duration("30"));
        assertThrows(IllegalArgumentException.class, () -> FormValues.duration("0"));
        assertThrows(IllegalArgumentException.class, () -> FormValues.duration("365001"));
        assertEquals(10000, FormValues.count("10000"));
        assertThrows(IllegalArgumentException.class, () -> FormValues.count("10001"));
        assertThrows(IllegalArgumentException.class, () -> FormValues.count(null));
        assertThrows(IllegalArgumentException.class, () -> FormValues.choice(null, List.of("solid"), "配色"));
        assertThrows(IllegalArgumentException.class, () -> FormValues.bool(null, "上架"));
    }
}
