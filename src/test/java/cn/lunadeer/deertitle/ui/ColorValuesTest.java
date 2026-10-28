package cn.lunadeer.deertitle.ui;

import static org.junit.jupiter.api.Assertions.*;

import cn.lunadeer.deertitle.text.TitleText;
import java.awt.Color;
import java.util.Locale;
import java.util.Random;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

class ColorValuesTest {
    @Test void parsesTheExistingNamedAndHexColorSyntax() {
        for (String name : NamedTextColor.NAMES.keys()) {
            assertDoesNotThrow(() -> TitleText.compose("色", "solid", name, name, false, false, false, false));
            assertEquals(NamedTextColor.NAMES.value(name).asHexString().toLowerCase(Locale.ROOT),
                    ColorValues.hex(ColorValues.parse(name)));
        }
        assertEquals("#aaaaaa", ColorValues.hex(ColorValues.parse("GREY")));
        assertEquals("#555555", ColorValues.hex(ColorValues.parse("dark_grey")));
        assertEquals("#a1b2c3", ColorValues.hex(ColorValues.parse("#A1B2C3")));
        assertEquals("#00010f", ColorValues.hex(new ColorValues.Rgb(0, 1, 15)));
        for (String invalid : new String[] {null, "", "#abc", "#1234567", "#gg0000", "red ", "#12345g", "<red>", "transparent"}) {
            assertThrows(IllegalArgumentException.class, () -> ColorValues.parse(invalid));
        }
    }

    @Test void primaryColorsAndTheHueWrapHaveExpectedValues() {
        int[][] colors = {{255, 0, 0}, {255, 255, 0}, {0, 255, 0}, {0, 255, 255}, {0, 0, 255}, {255, 0, 255}};
        for (int i = 0; i < colors.length; i++) {
            ColorValues.Rgb rgb = new ColorValues.Rgb(colors[i][0], colors[i][1], colors[i][2]);
            ColorValues.Hsv hsv = new ColorValues.Hsv(i * 60, 100, 100);
            assertEquals(hsv, ColorValues.toHsv(rgb));
            assertEquals(rgb, ColorValues.toRgb(hsv));
        }
        assertEquals(new ColorValues.Rgb(255, 0, 0), ColorValues.toRgb(new ColorValues.Hsv(360, 100, 100)));
    }

    @Test void blackWhiteAndGrayDoNotDependOnHue() {
        assertEquals(new ColorValues.Hsv(0, 0, 0), ColorValues.toHsv(new ColorValues.Rgb(0, 0, 0)));
        assertEquals(new ColorValues.Hsv(0, 0, 100), ColorValues.toHsv(new ColorValues.Rgb(255, 255, 255)));
        for (int hue = 0; hue <= 360; hue += 15) {
            assertEquals(new ColorValues.Rgb(0, 0, 0), ColorValues.toRgb(new ColorValues.Hsv(hue, 100, 0)));
            assertEquals(new ColorValues.Rgb(255, 255, 255), ColorValues.toRgb(new ColorValues.Hsv(hue, 0, 100)));
            assertEquals(new ColorValues.Rgb(128, 128, 128), ColorValues.toRgb(new ColorValues.Hsv(hue, 0, 50)));
        }
    }

    @Test void dialogValuesValidateBeforeRoundingAndHsvKeepsPrecision() {
        assertEquals(new ColorValues.Rgb(0, 128, 255), ColorValues.rgb(0f, 127.5f, 255f));
        assertEquals(new ColorValues.Hsv(193.736f, 71.923f, 83.271f), ColorValues.hsv(193.736f, 71.923f, 83.271f));
        for (Float bad : new Float[] {null, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -0.001f, 255.001f}) {
            assertThrows(IllegalArgumentException.class, () -> ColorValues.rgb(bad, 0f, 0f));
            assertThrows(IllegalArgumentException.class, () -> ColorValues.rgb(0f, bad, 0f));
            assertThrows(IllegalArgumentException.class, () -> ColorValues.rgb(0f, 0f, bad));
        }
        for (Float bad : new Float[] {null, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -0.001f, 360.001f}) {
            assertThrows(IllegalArgumentException.class, () -> ColorValues.hsv(bad, 0f, 0f));
        }
        for (Float bad : new Float[] {null, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -0.001f, 100.001f}) {
            assertThrows(IllegalArgumentException.class, () -> ColorValues.hsv(0f, bad, 0f));
            assertThrows(IllegalArgumentException.class, () -> ColorValues.hsv(0f, 0f, bad));
        }
        assertThrows(IllegalArgumentException.class, () -> new ColorValues.Rgb(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ColorValues.Rgb(0, 256, 0));
        assertThrows(IllegalArgumentException.class, () -> new ColorValues.Hsv(0, 0, Float.NaN));
    }

    @Test void rgbHsvRoundTripsPreserveEightBitColorsAndMatchTheJdkReference() {
        for (int red = 0; red <= 255; red += 17) {
            for (int green = 0; green <= 255; green += 17) {
                for (int blue = 0; blue <= 255; blue += 17) checkRgb(red, green, blue);
            }
        }
        Random random = new Random(0x5eed);
        for (int i = 0; i < 20_000; i++) checkRgb(random.nextInt(256), random.nextInt(256), random.nextInt(256));
    }

    @Test void fractionalHsvColorsAgreeWithTheIndependentJdkConversion() {
        Random random = new Random(0x7eed);
        for (int i = 0; i < 10_000; i++) {
            ColorValues.Hsv hsv = new ColorValues.Hsv(random.nextFloat() * 360, random.nextFloat() * 100, random.nextFloat() * 100);
            ColorValues.Rgb actual = ColorValues.toRgb(hsv);
            Color expected = new Color(Color.HSBtoRGB(hsv.hue() / 360, hsv.saturation() / 100, hsv.value() / 100));
            // JDK uses float intermediates: half-channel rounding boundaries may differ by one.
            assertTrue(Math.abs(actual.red() - expected.getRed()) <= 1);
            assertTrue(Math.abs(actual.green() - expected.getGreen()) <= 1);
            assertTrue(Math.abs(actual.blue() - expected.getBlue()) <= 1);
        }
    }

    private static void checkRgb(int red, int green, int blue) {
        ColorValues.Rgb rgb = new ColorValues.Rgb(red, green, blue);
        ColorValues.Hsv actual = ColorValues.toHsv(rgb);
        float[] reference = Color.RGBtoHSB(red, green, blue, null);
        assertEquals(reference[0] * 360, actual.hue(), 0.0001);
        assertEquals(reference[1] * 100, actual.saturation(), 0.0001);
        assertEquals(reference[2] * 100, actual.value(), 0.0001);
        assertEquals(rgb, ColorValues.toRgb(actual), () -> "RGB / HSV round trip changed " + rgb);
    }
}
