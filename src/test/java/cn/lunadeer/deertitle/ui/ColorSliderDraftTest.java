package cn.lunadeer.deertitle.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ColorSliderDraftTest {
    @Test void openingPreviewingAndSwitchingModesDoesNotQuantizeTheOriginalColor() {
        for (String hex : new String[]{"#77d9c7", "#a5a2ff", "#010203", "#fe0180", "#000000", "#ffffff", "#fffeff", "#fefefe"}) {
            ColorSliderDraft original = ColorSliderDraft.parse(hex);
            ColorSliderDraft current = original;
            for (int i = 0; i < 20; i++) {
                current = current.withHsv((float) current.hue(), (float) current.saturation(), (float) current.value());
                current = current.withRgb((float) current.rgb().red(), (float) current.rgb().green(), (float) current.rgb().blue());
            }
            assertEquals(original, current);
            assertEquals(hex, current.hex());
        }
    }

    @Test void editingOneHsvChannelPreservesThePrecisionOfUntouchedChannels() {
        ColorSliderDraft original = ColorSliderDraft.parse("#77d9c7");
        ColorSliderDraft changed = original.withHsv(240f, (float) original.saturation(), (float) original.value());
        assertEquals(240f, changed.hsv().hue());
        assertEquals(original.hsv().saturation(), changed.hsv().saturation());
        assertEquals(original.hsv().value(), changed.hsv().value());
        assertEquals(217, changed.rgb().blue());
        assertEquals(119, changed.rgb().red());
        assertEquals(119, changed.rgb().green());
    }

    @Test void blackRetainsHueAndSaturationAcrossPreviewAndModeSwitch() {
        ColorSliderDraft black = ColorSliderDraft.parse("#00ff00").withHsv(120f, 100f, 0f);
        assertEquals("#000000", black.hex());
        black = black.withRgb(0f, 0f, 0f).withHsv(120f, 100f, 0f);
        assertEquals("#00ff00", black.withHsv(120f, 100f, 100f).hex());
    }

    @Test void grayRetainsHueAndBothEndsOfHsvControlsAreReachable() {
        ColorSliderDraft color = ColorSliderDraft.parse("#ff0000").withHsv(240f, 0f, 100f);
        assertEquals("#ffffff", color.hex());
        color = color.withRgb(255f, 255f, 255f);
        assertEquals("#0000ff", color.withHsv(240f, 100f, 100f).hex());
        assertEquals("#ff0000", color.withHsv(0f, 100f, 100f).hex());
        assertEquals("#ff0000", color.withHsv(360f, 100f, 100f).hex());
    }

    @Test void roundedValuesNearEndpointsDoNotPreventSelectingExactEndpoints() {
        ColorSliderDraft nearWhite = ColorSliderDraft.parse("#fffeff");
        assertEquals("#ffffff", nearWhite.withHsv((float) nearWhite.hue(), 0f, 100f).hex());
        ColorSliderDraft gray = ColorSliderDraft.parse("#fefefe");
        assertEquals("#ffffff", gray.withHsv(0f, 0f, 100f).hex());
        ColorSliderDraft nearBlack = ColorSliderDraft.parse("#010101");
        assertEquals("#000000", nearBlack.withHsv(0f, 0f, 0f).hex());
        ColorSliderDraft nearFullHue = new ColorSliderDraft(new ColorValues.Rgb(255, 0, 1), new ColorValues.Hsv(359.8f, 100, 100));
        assertEquals("#ff0000", nearFullHue.withHsv(360f, 100f, 100f).hex());
    }
}
