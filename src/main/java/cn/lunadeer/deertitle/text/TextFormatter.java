package cn.lunadeer.deertitle.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

public final class TextFormatter {

    private static final Map<Character, String> LEGACY_TAGS = createLegacyTagMap();

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private final PlainTextComponentSerializer plainSerializer = PlainTextComponentSerializer.plainText();

    public Component deserialize(String input) {
        if (input == null || input.isBlank()) {
            return Component.empty();
        }
        return miniMessage.deserialize(translateLegacySyntax(input));
    }

    public Component deserializeTemplate(String template, Object... arguments) {
        return deserialize(arguments == null || arguments.length == 0 ? template : MessageFormat.format(template, arguments));
    }

    public String serializeLegacy(Component component) {
        return legacySerializer.serialize(component == null ? Component.empty() : component);
    }

    public String serializePlain(Component component) {
        return plainSerializer.serialize(component == null ? Component.empty() : component);
    }

    private String translateLegacySyntax(String input) {
        StringBuilder output = new StringBuilder();
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if ((current == '&' || current == '§') && index + 1 < input.length()) {
                if (input.charAt(index + 1) == '#' && index + 7 < input.length()) {
                    String hex = input.substring(index + 2, index + 8);
                    if (isHex(hex)) {
                        output.append("<#").append(hex).append(">");
                        index += 7;
                        continue;
                    }
                }
                if (current == '§' && index + 13 < input.length() && Character.toLowerCase(input.charAt(index + 1)) == 'x') {
                    StringBuilder hex = new StringBuilder();
                    boolean valid = true;
                    for (int offset = 2; offset <= 12; offset += 2) {
                        if (input.charAt(index + offset) != '§') {
                            valid = false;
                            break;
                        }
                        char hexChar = input.charAt(index + offset + 1);
                        if (!isHex(String.valueOf(hexChar))) {
                            valid = false;
                            break;
                        }
                        hex.append(hexChar);
                    }
                    if (valid && hex.length() == 6) {
                        output.append("<#").append(hex).append(">");
                        index += 13;
                        continue;
                    }
                }
                char code = Character.toLowerCase(input.charAt(index + 1));
                String tag = LEGACY_TAGS.get(code);
                if (tag != null) {
                    output.append('<').append(tag).append('>');
                    index++;
                    continue;
                }
            }
            output.append(current);
        }
        return output.toString();
    }

    private boolean isHex(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = Character.toLowerCase(value.charAt(index));
            if (!((current >= '0' && current <= '9') || (current >= 'a' && current <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private static Map<Character, String> createLegacyTagMap() {
        Map<Character, String> tags = new HashMap<>();
        tags.put('0', "black");
        tags.put('1', "dark_blue");
        tags.put('2', "dark_green");
        tags.put('3', "dark_aqua");
        tags.put('4', "dark_red");
        tags.put('5', "dark_purple");
        tags.put('6', "gold");
        tags.put('7', "gray");
        tags.put('8', "dark_gray");
        tags.put('9', "blue");
        tags.put('a', "green");
        tags.put('b', "aqua");
        tags.put('c', "red");
        tags.put('d', "light_purple");
        tags.put('e', "yellow");
        tags.put('f', "white");
        tags.put('k', "obfuscated");
        tags.put('l', "bold");
        tags.put('m', "strikethrough");
        tags.put('n', "underlined");
        tags.put('o', "italic");
        tags.put('r', "reset");
        return tags;
    }
}
