package cn.lunadeer.deertitle.ui;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import net.kyori.adventure.text.format.NamedTextColor;

/** Validated color values used by both native slider editors; HSV uses degrees and percentages. */
final class ColorValues {
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    private ColorValues() {}

    record Rgb(int red, int green, int blue) {
        Rgb {
            range(red, 255, "红色");
            range(green, 255, "绿色");
            range(blue, 255, "蓝色");
        }
    }

    record Hsv(float hue, float saturation, float value) {
        Hsv {
            range(hue, 360, "色相");
            range(saturation, 100, "饱和度");
            range(value, 100, "明度");
        }
    }

    static Rgb parse(String color) {
        if (color == null) throw invalidColor();
        int value;
        if (HEX.matcher(color).matches()) {
            value = Integer.parseInt(color.substring(1), 16);
        } else {
            String name = color.toLowerCase(Locale.ROOT).replace("grey", "gray");
            NamedTextColor named = NamedTextColor.NAMES.value(name);
            if (named == null) throw invalidColor();
            value = named.value();
        }
        return new Rgb(value >> 16 & 255, value >> 8 & 255, value & 255);
    }

    static String hex(Rgb color) {
        Objects.requireNonNull(color, "color");
        return String.format(Locale.ROOT, "#%02x%02x%02x", color.red(), color.green(), color.blue());
    }

    static Rgb rgb(Float red, Float green, Float blue) {
        return new Rgb(Math.round(required(red, 255, "红色")),
                Math.round(required(green, 255, "绿色")), Math.round(required(blue, 255, "蓝色")));
    }

    static Hsv hsv(Float hue, Float saturation, Float value) {
        return new Hsv(required(hue, 360, "色相"), required(saturation, 100, "饱和度"),
                required(value, 100, "明度"));
    }

    static Hsv toHsv(Rgb color) {
        Objects.requireNonNull(color, "color");
        int max = Math.max(color.red(), Math.max(color.green(), color.blue()));
        int min = Math.min(color.red(), Math.min(color.green(), color.blue()));
        int delta = max - min;
        double hue = 0;
        if (delta != 0) {
            if (max == color.red()) hue = 60.0 * (color.green() - color.blue()) / delta;
            else if (max == color.green()) hue = 120.0 + 60.0 * (color.blue() - color.red()) / delta;
            else hue = 240.0 + 60.0 * (color.red() - color.green()) / delta;
            if (hue < 0) hue += 360;
        }
        return new Hsv((float) hue, max == 0 ? 0 : (float) (100.0 * delta / max),
                (float) (100.0 * max / 255));
    }

    static Rgb toRgb(Hsv color) {
        Objects.requireNonNull(color, "color");
        double hue = color.hue() % 360.0 / 60.0;
        double value = color.value() / 100.0;
        double chroma = value * color.saturation() / 100.0;
        double secondary = chroma * (1 - Math.abs(hue % 2 - 1));
        double offset = value - chroma;
        double red, green, blue;
        switch ((int) hue) {
            case 0 -> { red = chroma; green = secondary; blue = 0; }
            case 1 -> { red = secondary; green = chroma; blue = 0; }
            case 2 -> { red = 0; green = chroma; blue = secondary; }
            case 3 -> { red = 0; green = secondary; blue = chroma; }
            case 4 -> { red = secondary; green = 0; blue = chroma; }
            default -> { red = chroma; green = 0; blue = secondary; }
        }
        return new Rgb((int) Math.round((red + offset) * 255),
                (int) Math.round((green + offset) * 255), (int) Math.round((blue + offset) * 255));
    }

    private static float required(Float value, int maximum, String label) {
        if (value == null) throw new IllegalArgumentException(label + "格式无效。");
        range(value, maximum, label);
        return value;
    }

    private static void range(float value, int maximum, String label) {
        if (!Float.isFinite(value) || value < 0 || value > maximum) {
            throw new IllegalArgumentException(label + "必须在 0 至 " + maximum + " 之间。");
        }
    }

    private static IllegalArgumentException invalidColor() {
        return new IllegalArgumentException("颜色应为 #RRGGBB 或 MiniMessage 颜色名称。");
    }
}
