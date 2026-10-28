package cn.lunadeer.deertitle.ui;

import cn.lunadeer.deertitle.domain.Title;
import cn.lunadeer.deertitle.text.TitleText;

/** Immutable draft belongs to a single dialog session and is never a database entity. */
record EditorDraft(Long titleId, long version, boolean admin, String name, String source,
                   String text, String mode, String startColor, String endColor,
                   boolean bold, boolean italic, boolean underline, boolean strike,
                   String description, boolean enabled) {
    static EditorDraft create(boolean admin) {
        return new EditorDraft(null, 0, admin, "", "<gradient:#77d9c7:#a5a2ff>新的称号</gradient>",
            "新的称号", "gradient", "#77d9c7", "#a5a2ff", false, false, false, false, "", true);
    }

    static EditorDraft edit(Title title, boolean admin) {
        return new EditorDraft(title.id(), title.version(), admin, title.name(), title.miniMessage(),
            TitleText.plain(title.miniMessage()), "minimessage", "#77d9c7", "#a5a2ff",
            false, false, false, false, title.description(), title.enabled());
    }

    EditorDraft mode(String value) {
        return new EditorDraft(titleId, version, admin, name, source, TitleText.plain(source), value,
            startColor, endColor, bold, italic, underline, strike, description, enabled);
    }

    EditorDraft colors(String start, String end) {
        String composed = TitleText.compose(text, mode, start, end, bold, italic, underline, strike);
        return new EditorDraft(titleId, version, admin, name, composed, text, mode,
            start, end, bold, italic, underline, strike, description, enabled);
    }
}
