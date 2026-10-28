package cn.lunadeer.deertitle.text;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** The single, restricted MiniMessage boundary for stored and player-authored titles. */
public final class TitleText {
    private static final int HARD_SOURCE_LIMIT = 65_536;
    private static final Set<String> COLORS = Set.of("black", "dark_blue", "dark_green", "dark_aqua",
            "dark_red", "dark_purple", "gold", "gray", "grey", "dark_gray", "dark_grey", "blue",
            "green", "aqua", "red", "light_purple", "yellow", "white");
    private static final Set<String> DECORATIONS = Set.of("bold", "b", "italic", "i", "em",
            "underlined", "u", "strikethrough", "st", "obfuscated", "obf");
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Pattern RAINBOW_PHASE = Pattern.compile("!?-?[0-9]{1,6}|!");
    private static final MiniMessage FORMAT = MiniMessage.builder().tags(TagResolver.resolver(
            StandardTags.color(), StandardTags.decorations(), StandardTags.gradient(),
            StandardTags.rainbow(), StandardTags.reset())).build();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private TitleText() {}

    public static Component parse(String source) {
        checkSource(source, HARD_SOURCE_LIMIT);
        checkTags(source);
        return FORMAT.deserialize(source).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Returns accepted MiniMessage unchanged; throws a player-readable validation error otherwise. */
    public static String validate(String source, int maxVisibleCodePoints, int maxSourceLength) {
        if (maxVisibleCodePoints < 1 || maxSourceLength < 1) {
            throw new IllegalArgumentException("称号长度限制必须大于 0。");
        }
        checkSource(source, Math.min(maxSourceLength, HARD_SOURCE_LIMIT));
        String visible = PLAIN.serialize(parse(source));
        if (visible.isBlank()) throw new IllegalArgumentException("称号不能为空。");
        if (visible.codePointCount(0, visible.length()) > maxVisibleCodePoints) {
            throw new IllegalArgumentException("称号最多允许 " + maxVisibleCodePoints + " 个字符。");
        }
        return source;
    }

    public static String plain(String source) {
        return PLAIN.serialize(parse(source));
    }

    public static String legacy(String source) {
        return LEGACY.serialize(parse(source));
    }

    /** Escape plain text before placing it inside formatting tags. */
    public static String literal(String text) {
        Objects.requireNonNull(text, "text");
        return text.replace("\\", "\\\\").replace("<", "\\<");
    }

    public static String compose(String plainText, String mode, String colorStart, String colorEnd,
                                 boolean bold, boolean italic, boolean underline, boolean strike) {
        checkSource(plainText, HARD_SOURCE_LIMIT);
        String text = literal(plainText);
        String style = Objects.requireNonNull(mode, "mode").toLowerCase(Locale.ROOT);
        text = switch (style) {
            case "solid" -> "<" + checkedColor(colorStart) + ">" + text + "</" + checkedColor(colorStart) + ">";
            case "gradient" -> "<gradient:" + checkedColor(colorStart) + ":" + checkedColor(colorEnd)
                    + ">" + text + "</gradient>";
            case "rainbow" -> "<rainbow>" + text + "</rainbow>";
            default -> throw new IllegalArgumentException("请选择纯色、渐变或彩虹。");
        };
        if (strike) text = "<strikethrough>" + text + "</strikethrough>";
        if (underline) text = "<underlined>" + text + "</underlined>";
        if (italic) text = "<italic>" + text + "</italic>";
        if (bold) text = "<bold>" + text + "</bold>";
        return text;
    }

    static String canonicalize(String source) {
        return FORMAT.serialize(parse(source));
    }

    static boolean isColor(String value) {
        return value != null && (HEX.matcher(value).matches() || COLORS.contains(value.toLowerCase(Locale.ROOT)));
    }

    private static String checkedColor(String color) {
        if (!isColor(color)) throw new IllegalArgumentException("颜色应为 #RRGGBB 或 MiniMessage 颜色名称。");
        return color.toLowerCase(Locale.ROOT);
    }

    private static void checkSource(String source, int maximum) {
        if (source == null) throw new IllegalArgumentException("称号不能为空。");
        if (source.length() > maximum) throw new IllegalArgumentException("称号格式过长，最多允许 " + maximum + " 个字符。");
        for (int i = 0; i < source.length();) {
            int cp = source.codePointAt(i);
            if (Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029
                    || (Character.isSurrogate(source.charAt(i)) && Character.charCount(cp) == 1)) {
                throw new IllegalArgumentException("称号必须为单行，不能包含控制字符或无效 Unicode。");
            }
            i += Character.charCount(cp);
        }
    }

    private static void checkTags(String source) {
        Deque<String> nesting = new ArrayDeque<>();
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '\\' && i + 1 < source.length()) {
                i++;
                continue;
            }
            if (ch != '<') continue;
            int end = source.indexOf('>', i + 1);
            if (end < 0) throw new IllegalArgumentException("MiniMessage 标签未结束；显示 < 请写成 \\<。");
            String raw = source.substring(i + 1, end);
            checkTag(raw);
            if (raw.equalsIgnoreCase("reset") || raw.equalsIgnoreCase("r")) nesting.clear();
            else if (raw.startsWith("/")) {
                String tagName = nestingName(raw.substring(1));
                if (nesting.contains(tagName)) {
                    while (!nesting.pop().equals(tagName)) { /* close the matching formatting scope */ }
                }
            } else {
                nesting.push(nestingName(raw));
                if (nesting.size() > 64) throw new IllegalArgumentException("称号样式嵌套过深。");
            }
            i = end;
        }
    }

    private static String nestingName(String raw) {
        String name = raw.split(":", 2)[0].toLowerCase(Locale.ROOT);
        if (name.startsWith("!")) name = name.substring(1);
        return switch (name) {
            case "b" -> "bold";
            case "i", "em" -> "italic";
            case "u" -> "underlined";
            case "st" -> "strikethrough";
            case "obf" -> "obfuscated";
            case "c", "colour" -> "color";
            default -> name;
        };
    }

    private static void checkTag(String raw) {
        String tag = raw.toLowerCase(Locale.ROOT);
        boolean closing = tag.startsWith("/");
        if (closing) tag = tag.substring(1);
        String[] parts = tag.split(":", -1);
        String name = parts[0];
        if (name.startsWith("!")) name = name.substring(1);
        if (closing && parts.length != 1) throw invalidTag();
        if (isColor(name)) {
            if (parts.length != 1 || tag.startsWith("!")) throw invalidTag();
            return;
        }
        if (DECORATIONS.contains(name)) {
            if (parts.length > 2 || (parts.length == 2 && !parts[1].equals("true") && !parts[1].equals("false"))) {
                throw invalidTag();
            }
            return;
        }
        if (tag.startsWith("!")) throw invalidTag();
        switch (name) {
            case "reset", "r" -> {
                if (closing || parts.length != 1) throw invalidTag();
            }
            case "color", "colour", "c" -> {
                if (!closing && (parts.length != 2 || !isColor(parts[1]))) throw invalidTag();
            }
            case "gradient" -> {
                if (closing) return;
                if (parts.length < 3 || parts.length > 18) throw invalidTag();
                int colorCount = parts.length - 1;
                if (!isColor(parts[parts.length - 1])) {
                    try {
                        double phase = Double.parseDouble(parts[parts.length - 1]);
                        if (!Double.isFinite(phase) || phase < -1 || phase > 1) throw invalidTag();
                    } catch (NumberFormatException ex) {
                        throw invalidTag();
                    }
                    colorCount--;
                }
                if (colorCount < 2) throw invalidTag();
                for (int i = 1; i <= colorCount; i++) if (!isColor(parts[i])) throw invalidTag();
            }
            case "rainbow" -> {
                if (parts.length > 2 || (parts.length == 2 && !RAINBOW_PHASE.matcher(parts[1]).matches())) throw invalidTag();
            }
            default -> throw invalidTag();
        }
    }

    private static IllegalArgumentException invalidTag() {
        return new IllegalArgumentException("称号仅支持颜色、文字样式、渐变和彩虹；其他标签请作为纯文本输入。");
    }
}
