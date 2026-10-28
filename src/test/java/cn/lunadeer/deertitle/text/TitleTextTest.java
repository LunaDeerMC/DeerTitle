package cn.lunadeer.deertitle.text;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

class TitleTextTest {
    @Test
    void validatesColorsDecorationsAndGradientWithoutChangingSource() {
        String source = "<bold><gradient:#ff0000:#0000ff>鹿鸣</gradient></bold>";
        assertEquals(source, TitleText.validate(source, 2, 200));
        List<Glyph> glyphs = glyphs(TitleText.parse(source));
        assertEquals("鹿鸣", TitleText.plain(source));
        assertEquals(0xff0000, glyphs.get(0).color());
        assertEquals(0x0000ff, glyphs.get(1).color());
        assertTrue(glyphs.stream().allMatch(Glyph::bold));
    }

    @Test
    void rejectsInteractiveAndNonFormattingTagsRatherThanSilentlyIgnoringThem() {
        for (String tag : List.of("click:run_command:'/op somebody'", "hover:show_text:'x'",
                "insertion:x", "font:minecraft:uniform", "selector:@a", "nbt:entity:@s:x",
                "score:x:y", "translatable:key", "keybind:key.jump", "newline", "unknown")) {
            assertThrows(IllegalArgumentException.class, () -> TitleText.validate("<" + tag + ">鹿", 20, 300), tag);
        }
    }

    @Test
    void literalEscapesTagsAndBackslashesWithoutChangingVisibleText() {
        String literal = "<click:run_command:'/op x'>C:\\test</click>";
        Component result = TitleText.parse(TitleText.literal(literal));
        assertEquals(literal, TitleText.plain(TitleText.literal(literal)));
        assertNoActions(result);
    }

    @Test
    void visibleLengthUsesUnicodeCodePointsAndRejectsEmptyOrMultilineText() {
        assertEquals("<red>鹿🦌", TitleText.validate("<red>鹿🦌", 2, 100));
        assertThrows(IllegalArgumentException.class, () -> TitleText.validate("鹿🦌", 1, 100));
        assertThrows(IllegalArgumentException.class, () -> TitleText.validate("<red>  ", 4, 100));
        for (String invalid : List.of("a\nb", "a\rb", "a\tb", "a\u2028b", "\uD800")) {
            assertThrows(IllegalArgumentException.class, () -> TitleText.validate(invalid, 20, 100));
        }
        assertThrows(IllegalArgumentException.class, () -> TitleText.validate("<red>鹿", 20, 3));
    }

    @Test
    void rejectsMalformedTagsAndExcessiveGradientArguments() {
        for (String invalid : List.of("<red", "<gradient:red>", "<gradient:red:blue:NaN>x",
                "<gradient:red:blue:2>x", "<bold:maybe>x", "<color:no_such_color>x", "</red:x>x")) {
            assertThrows(IllegalArgumentException.class, () -> TitleText.validate(invalid, 100, 300), invalid);
        }
        assertEquals("x", TitleText.plain("<gradient:red:blue:0.5>x"));
        assertEquals("x", TitleText.plain("<rainbow:!2>x"));
        assertEquals("x", TitleText.plain("<rainbow:!>x"));
        assertThrows(IllegalArgumentException.class, () -> TitleText.parse("<bold>".repeat(65) + "x"));
        assertThrows(IllegalArgumentException.class, () -> TitleText.parse("<bold></red>".repeat(65) + "x"));
    }

    @Test
    void editorCompositionIsSafeInEveryMode() {
        for (String mode : List.of("solid", "gradient", "rainbow")) {
            String source = TitleText.compose("鹿<red>🦌", mode, "#ff8800", "blue", true, true, true, true);
            assertEquals("鹿<red>🦌", TitleText.plain(source));
            assertEquals(source, TitleText.validate(source, 20, 500));
            assertNoActions(TitleText.parse(source));
        }
        assertThrows(IllegalArgumentException.class,
                () -> TitleText.compose("x", "solid", "red><click:run_command:x", "blue", false, false, false, false));
    }

    @Test
    void legacyOutputIncludesTrueRgbWithoutLeakingMiniMessageMarkup() {
        String rendered = TitleText.legacy("<#12abef>鹿");
        assertEquals("§x§1§2§a§b§e§f鹿", rendered);
    }

    static void assertNoActions(Component node) {
        assertNull(node.clickEvent());
        assertNull(node.hoverEvent());
        assertNull(node.insertion());
        assertNull(node.font());
        node.children().forEach(TitleTextTest::assertNoActions);
    }

    record Glyph(int codePoint, int color, boolean bold) {}

    static List<Glyph> glyphs(Component component) {
        List<Glyph> result = new ArrayList<>();
        visit(component, 0xffffff, false, result);
        return result;
    }

    private static void visit(Component component, int inheritedColor, boolean inheritedBold, List<Glyph> result) {
        TextColor color = component.color();
        int actualColor = color == null ? inheritedColor : color.value();
        TextDecoration.State bold = component.decoration(TextDecoration.BOLD);
        boolean actualBold = bold == TextDecoration.State.NOT_SET ? inheritedBold : bold == TextDecoration.State.TRUE;
        if (component instanceof TextComponent text) {
            text.content().codePoints().forEach(cp -> result.add(new Glyph(cp, actualColor, actualBold)));
        }
        component.children().forEach(child -> visit(child, actualColor, actualBold, result));
    }
}
