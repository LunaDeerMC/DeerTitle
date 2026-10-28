package cn.lunadeer.deertitle.text;

import java.util.Locale;
import java.util.Map;

/** Converts MiniPlayerTitle's legacy display grammar without enabling arbitrary MiniMessage tags. */
public final class LegacyTextConverter {
    private static final int MAX_SOURCE = 16_384;
    private static final Map<Character, String> COLORS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"), Map.entry('f', "white"));
    private static final Map<Character, String> STYLES = Map.of('k', "obfuscated", 'l', "bold",
            'm', "strikethrough", 'n', "underlined", 'o', "italic");

    private LegacyTextConverter() {}

    public static String convert(String legacy) {
        if (legacy == null || legacy.isEmpty()) return "";
        if (legacy.length() > MAX_SOURCE) throw new IllegalArgumentException("旧称号文本过长，无法安全导入。");
        return TitleText.canonicalize(convertFragment(legacy, 0));
    }

    private static String convertFragment(String source, int depth) {
        if (depth > 8) throw new IllegalArgumentException("旧称号渐变嵌套过深。");
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < source.length();) {
            if (source.startsWith("<gradient:", i)) {
                int openEnd = source.indexOf('>', i + 10);
                if (openEnd >= 0) {
                    String colors = source.substring(i + 10, openEnd);
                    if (validGradientColors(colors)) {
                        int close = source.indexOf("</gradient>", openEnd + 1);
                        if (close < 0) close = source.length();
                        output.append("<gradient:").append(colors.toLowerCase(Locale.ROOT)).append('>')
                                .append(convertFragment(source.substring(openEnd + 1, close), depth + 1))
                                .append("</gradient>");
                        i = close == source.length() ? close : close + 11;
                        continue;
                    }
                }
            }
            char ch = source.charAt(i);
            if ((ch == '&' || ch == '§') && i + 1 < source.length()) {
                char code = Character.toLowerCase(source.charAt(i + 1));
                if (code == 'x' && repeatedHex(source, i, ch)) {
                    output.append("<reset><#");
                    for (int digit = 0; digit < 6; digit++) output.append(source.charAt(i + 3 + digit * 2));
                    output.append('>');
                    i += 14;
                    continue;
                }
                if (code == 'x') {
                    int end = i + 2;
                    while (end + 1 < source.length() && end < i + 14
                            && source.charAt(end) == ch && isHex(source.charAt(end + 1))) end += 2;
                    output.append(TitleText.literal(source.substring(i, end)));
                    i = end;
                    continue;
                }
                if (code == '#' && hexAt(source, i + 2)) {
                    output.append("<reset><#").append(source, i + 2, i + 8).append('>');
                    i += 8;
                    continue;
                }
                if (COLORS.containsKey(code)) {
                    output.append("<reset><").append(COLORS.get(code)).append('>');
                    i += 2;
                    continue;
                }
                if (STYLES.containsKey(code)) {
                    output.append('<').append(STYLES.get(code)).append('>');
                    i += 2;
                    continue;
                }
                if (code == 'r') {
                    output.append("<reset>");
                    i += 2;
                    continue;
                }
            }
            if (ch == '#' && hexAt(source, i + 1)) {
                output.append("<reset><#").append(source, i + 1, i + 7).append('>');
                i += 7;
                continue;
            }
            // Escape unknown tag delimiters, but keep processing legacy colors inside literal brackets.
            // Escaping the whole <...> fragment would expose embedded RGB codes as visible text.
            if (ch == '\\' || ch == '<') output.append('\\');
            output.append(ch);
            i++;
        }
        return output.toString();
    }

    private static boolean validGradientColors(String source) {
        String[] values = source.split(":", -1);
        if (values.length < 2 || values.length > 16) return false;
        for (String value : values) {
            if (value.length() != 7 || value.charAt(0) != '#' || !hexAt(value, 1)) return false;
        }
        return true;
    }

    private static boolean repeatedHex(String source, int offset, char marker) {
        if (source.length() - offset < 14) return false;
        for (int digit = 0; digit < 6; digit++) {
            if (source.charAt(offset + 2 + digit * 2) != marker || !isHex(source.charAt(offset + 3 + digit * 2))) return false;
        }
        return true;
    }

    private static boolean hexAt(String source, int offset) {
        if (source.length() - offset < 6) return false;
        for (int i = offset; i < offset + 6; i++) if (!isHex(source.charAt(i))) return false;
        return true;
    }

    private static boolean isHex(char value) {
        return value >= '0' && value <= '9' || value >= 'a' && value <= 'f' || value >= 'A' && value <= 'F';
    }
}
