package cn.lunadeer.deertitle.configuration;

import cn.lunadeer.deertitle.DeerTitlePlugin;

public final class ConfigService {

    private final DeerTitlePlugin plugin;
    private PluginConfig config;
    private Language language;

    public ConfigService(DeerTitlePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() throws Exception {
        this.config = PluginConfig.load(plugin);
        this.language = Language.load(plugin, config.general.languageCode);
    }

    public void reload() throws Exception {
        load();
    }

    public PluginConfig config() {
        return config;
    }

    public Language language() {
        return language;
    }
}
