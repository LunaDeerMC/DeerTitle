package cn.lunadeer.deertitle.platform;

import cn.lunadeer.deertitle.text.TitleText;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Immutable render products; hot PlaceholderAPI calls only return existing strings. */
public record FormattedTitle(String miniMessage, String legacy, String plain) {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    public static FormattedTitle render(String source) {
        var component = TitleText.parse(source);
        return new FormattedTitle(MiniMessage.miniMessage().serialize(component),
                LEGACY.serialize(component), PlainTextComponentSerializer.plainText().serialize(component));
    }
}
