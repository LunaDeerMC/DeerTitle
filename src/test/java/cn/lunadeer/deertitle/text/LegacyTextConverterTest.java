package cn.lunadeer.deertitle.text;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class LegacyTextConverterTest {
    @Test
    void supportsEveryLegacyRgbRepresentation() {
        for (String input : List.of("&#12abef鹿", "§#12abef鹿", "#12abef鹿",
                "&x&1&2&a&b&e&f鹿", "§x§1§2§a§b§e§f鹿", "&X&1&2&A&B&E&F鹿")) {
            String converted = LegacyTextConverter.convert(input);
            assertEquals("鹿", TitleText.plain(converted), input);
            assertEquals(0x12abef, TitleTextTest.glyphs(TitleText.parse(converted)).getFirst().color(), input);
        }
    }

    @Test
    void legacyColorCodesResetDecorationsAndExplicitResetReturnsWhite() {
        Component parsed = TitleText.parse(LegacyTextConverter.convert("&a绿&l粗&c红&r白"));
        List<TitleTextTest.Glyph> glyphs = TitleTextTest.glyphs(parsed);
        assertEquals(List.of(false, true, false, false), glyphs.stream().map(TitleTextTest.Glyph::bold).toList());
        assertEquals(List.of(0x55ff55, 0x55ff55, 0xff5555, 0xffffff), glyphs.stream().map(TitleTextTest.Glyph::color).toList());
    }

    @Test
    void preservesLegacyGradientAppearanceIncludingUnclosedAndSingleCharacter() {
        for (String source : List.of("<gradient:#ff0000:#0000ff>鹿鸣</gradient>",
                "<gradient:#ff0000:#0000ff>鹿鸣")) {
            String result = LegacyTextConverter.convert(source);
            List<TitleTextTest.Glyph> glyphs = TitleTextTest.glyphs(TitleText.parse(result));
            assertEquals("鹿鸣", TitleText.plain(result));
            assertEquals(0xff0000, glyphs.getFirst().color());
            assertEquals(0x0000ff, glyphs.getLast().color());
        }
        assertEquals("鹿", TitleText.plain(LegacyTextConverter.convert("<gradient:#ff0000:#0000ff>鹿</gradient>")));
        assertEquals("", TitleText.plain(LegacyTextConverter.convert("<gradient:#ff0000:#0000ff></gradient>")));
    }

    @Test
    void unknownOldTagsRemainLiteralAndCannotAcquireActions() {
        for (String raw : List.of("<click:run_command:'/op x'>鹿</click>", "<red>鹿</red>",
                "<font:minecraft:uniform>鹿", "<gradient:invalid>鹿</gradient>", "abc<")) {
            String result = LegacyTextConverter.convert(raw);
            assertEquals(raw, TitleText.plain(result));
            TitleTextTest.assertNoActions(TitleText.parse(result));
        }
    }

    @Test
    void preservesPlainPunctuationBackslashesAndSupplementaryCharacters() {
        for (String plain : List.of("A & Z", "C:\\files\\鹿", "🦌鹿", "##nohex", "&x&1&2", "")) {
            assertEquals(plain, TitleText.plain(LegacyTextConverter.convert(plain)));
        }
        assertEquals("", LegacyTextConverter.convert(null));
    }

    @Test
    void refusesUnsafeLengthAndControlCharactersInsteadOfSilentlyLosingData() {
        assertThrows(IllegalArgumentException.class, () -> LegacyTextConverter.convert("x".repeat(16_385)));
        assertThrows(IllegalArgumentException.class, () -> LegacyTextConverter.convert("a\nb"));
    }
}
