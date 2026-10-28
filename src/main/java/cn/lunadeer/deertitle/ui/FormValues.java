package cn.lunadeer.deertitle.ui;

import cn.lunadeer.deertitle.domain.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

/** Server-side parsing; dialog widgets and client-side limits are not trusted. */
final class FormValues {
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
        .withResolverStyle(ResolverStyle.STRICT);
    private static final BigDecimal MAX_PRICE = new BigDecimal("999999999999.99");

    private FormValues() {}

    static String text(String value, int maxLength, String label) {
        if (value == null || value.length() > maxLength || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(label + "格式无效。");
        }
        return value;
    }

    static boolean bool(Boolean value, String label) {
        if (value == null) throw new IllegalArgumentException(label + "格式无效。");
        return value;
    }

    static String choice(String value, List<String> allowed, String label) {
        if (value == null || !allowed.contains(value)) throw new IllegalArgumentException(label + "无效。");
        return value;
    }

    static int count(String value) {
        return (int) integer(value, 1, 10000, "购买次数");
    }

    static long integer(String value, long min, long max, String label) {
        try {
            String input = text(value, 32, label).trim();
            if (!input.matches("[0-9]+")) throw new NumberFormatException();
            long number = Long.parseLong(input);
            if (number < min || number > max) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + "必须是 " + min + " 至 " + max + " 的整数。");
        }
    }

    static BigDecimal price(String value, Currency currency) {
        try {
            String input = text(value, 32, "价格").trim();
            if (!input.matches("[0-9]+(?:\\.[0-9]{1,2})?")) throw new NumberFormatException();
            BigDecimal amount = new BigDecimal(input).stripTrailingZeros();
            if (amount.signum() < 0 || amount.compareTo(MAX_PRICE) > 0) throw new NumberFormatException();
            if (currency == Currency.COINS && amount.scale() > 0) {
                throw new IllegalArgumentException("称号币价格必须是整数。");
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("价格必须是不超过 999999999999.99 的非负金额，最多两位小数。");
        }
    }

    static Long totalStock(String remaining, long sold) {
        if (text(remaining, 32, "剩余库存").isBlank()) return null;
        long quantity = integer(remaining, 0, Long.MAX_VALUE, "剩余库存");
        try {
            return Math.addExact(sold, quantity);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("库存总量过大。");
        }
    }

    static Integer duration(String value) {
        if (text(value, 16, "持有天数").isBlank()) return null;
        return (int) integer(value, 1, 365000, "持有天数");
    }

    static Instant date(String value, ZoneId zone) {
        if (text(value, 32, "日期").isBlank()) return null;
        try {
            LocalDateTime local = LocalDateTime.parse(value.trim(), DATE);
            var offsets = zone.getRules().getValidOffsets(local);
            if (offsets.size() != 1) {
                throw new IllegalArgumentException("该时间处于夏令时切换区间，请选择没有歧义的时间。");
            }
            return local.toInstant(offsets.getFirst());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("日期格式应为 yyyy-MM-dd HH:mm。");
        }
    }

    static void dateRange(Instant startsAt, Instant endsAt) {
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间。");
        }
    }
}
