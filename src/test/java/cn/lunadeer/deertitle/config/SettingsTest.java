package cn.lunadeer.deertitle.config;

import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    @TempDir Path directory;

    @Test void initialCoinsDefaultToZeroAndOnlyApplyToInternalCurrency() {
        var config = new YamlConfiguration();
        assertEquals(0, Settings.load(config, directory).initialCoinsForNewPlayer());
        config.set("economy.initial-coins", 100);
        assertEquals(100, Settings.load(config, directory).initialCoinsForNewPlayer());
        config.set("economy.mode", "vault");
        var settings = Settings.load(config, directory);
        assertEquals(100, settings.initialCoins());
        assertEquals(0, settings.initialCoinsForNewPlayer());
    }

    @Test void rejectsInvalidInitialCoinsWithoutTruncatingOrOverflowing() {
        for (Object invalid : List.of(-1, 0.1, "1.5", "1000000000001", "9223372036854775808", "not-a-number", true)) {
            var config = new YamlConfiguration();
            config.set("economy.initial-coins", invalid);
            assertThrows(IllegalArgumentException.class, () -> Settings.load(config, directory), invalid.toString());
        }
        var config = new YamlConfiguration();
        config.set("economy.initial-coins", 1_000_000_000_000L);
        assertEquals(1_000_000_000_000L, Settings.load(config, directory).initialCoinsForNewPlayer());
    }
}
