package cn.lunadeer.deertitle.utils.configuration;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public abstract class ConfigurationFile extends ConfigurationPart {

    private YamlConfiguration yaml;

    public YamlConfiguration getYaml() {
        return yaml;
    }

    public void setYaml(YamlConfiguration yaml) {
        this.yaml = yaml;
    }

    public void save(File file) throws Exception {
        yaml.options().width(250);
        yaml.save(file);
    }
}
