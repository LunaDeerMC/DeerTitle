package cn.lunadeer.deertitle.database.model;

import java.time.LocalDate;

public record DateParts(int year, int month, int day) {

    public static DateParts permanent() {
        return new DateParts(-1, -1, -1);
    }

    public static DateParts from(LocalDate date) {
        if (date == null) {
            return permanent();
        }
        return new DateParts(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    public boolean isPermanent() {
        return year < 0 || month < 0 || day < 0;
    }

    public LocalDate asLocalDate() {
        if (isPermanent()) {
            return null;
        }
        return LocalDate.of(year, month, day);
    }
}
