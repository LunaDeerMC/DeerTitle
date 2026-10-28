package cn.lunadeer.deertitle.ui;

/** Keeps precise colors while the HSV controls display whole degrees and percentages. */
record ColorSliderDraft(ColorValues.Rgb rgb, ColorValues.Hsv hsv) {
    static ColorSliderDraft parse(String color) {
        ColorValues.Rgb rgb = ColorValues.parse(color);
        return new ColorSliderDraft(rgb, ColorValues.toHsv(rgb));
    }

    String hex() { return ColorValues.hex(rgb); }
    int hue() { return displayed(hsv.hue(), 360); }
    int saturation() { return displayed(hsv.saturation(), 100); }
    int value() { return displayed(hsv.value(), 100); }

    ColorSliderDraft withRgb(Float red, Float green, Float blue) {
        ColorValues.Rgb next = ColorValues.rgb(red, green, blue);
        // Preserve hue/saturation when switching through RGB with a gray or black color.
        return next.equals(rgb) ? this : new ColorSliderDraft(next, ColorValues.toHsv(next));
    }

    ColorSliderDraft withHsv(Float hue, Float saturation, Float value) {
        ColorValues.Hsv submitted = ColorValues.hsv(hue, saturation, value);
        ColorValues.Hsv next = new ColorValues.Hsv(
                unchanged(submitted.hue(), hsv.hue(), 360),
                unchanged(submitted.saturation(), hsv.saturation(), 100),
                unchanged(submitted.value(), hsv.value(), 100));
        return next.equals(hsv) ? this : new ColorSliderDraft(ColorValues.toRgb(next), next);
    }

    private static float unchanged(float submitted, float original, int maximum) {
        return submitted == displayed(original, maximum) ? original : submitted;
    }

    private static int displayed(float value, int maximum) {
        // Reserve the endpoints for exact zero/maximum, so dragging there always
        // works even when an almost-gray/white source would round to that endpoint.
        if (value == 0 || value == maximum) return (int) value;
        return Math.clamp(Math.round(value), 1, maximum - 1);
    }
}
