package cn.lunadeer.deertitle.configuration;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.utils.configuration.ConfigurationFile;
import cn.lunadeer.deertitle.utils.configuration.ConfigurationManager;
import cn.lunadeer.deertitle.utils.configuration.Headers;

import java.io.File;

@Headers({
        "Main configuration for DeerTitle.",
        "This file is generated and updated by the plugin.",
})
public final class PluginConfig extends ConfigurationFile {

    public General general = new General();
    public Database database = new Database();
    public Display display = new Display();
    public Economy economy = new Economy();
    public Shop shop = new Shop();
    public Card card = new Card();
    public Feedback feedback = new Feedback();
    public Ui ui = new Ui();
    public Tasks tasks = new Tasks();

    public static PluginConfig load(DeerTitlePlugin plugin) throws Exception {
        return (PluginConfig) ConfigurationManager.load(PluginConfig.class, new File(plugin.getDataFolder(), "config.yml"));
    }

    public static final class General extends cn.lunadeer.deertitle.utils.configuration.ConfigurationPart {
        public String languageCode = "zh_cn";
        public boolean debug = false;
    }

    public static final class Database extends cn.lunadeer.deertitle.utils.configuration.ConfigurationPart {
        public String type = "sqlite";
        public String sqliteFile = "database/deertitle.db";
        public String host = "127.0.0.1";
        public int port = 3306;
        public String database = "deertitle";
        public String username = "root";
        public String password = "change-me";
        public String parameters = "useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC";
        public int maximumPoolSize = 10;
        public long connectionTimeoutMillis = 10000L;
    }

    public static final class Display extends cn.lunadeer.deertitle.utils.configuration.ConfigurationPart {
        public boolean fallbackChatPrefix = true;
        public boolean fallbackTabPrefix = true;
        public String titleSeparator = " ";
        public String currentTitlePlaceholder = "%deertitle_current%";
        public String previewPlaceholder = "%deertitle_preview%";
        public boolean allowLegacyAmpersand = true;
        public boolean allowSectionSign = true;
        public boolean allowMiniMessage = true;
    }

    public static final class Economy extends cn.lunadeer.deertitle.utils.configuration.ConfigurationPart {
        public boolean preferVault = true;
        public String builtInCurrencyName = "Coin";
        public String builtInCurrencySymbol = "C";
        public double defaultBalance = 0D;
    }

    public static final class Shop extends cn.lunadeer.deertitle.utils.configuration.ConfigurationPart {
        public boolean allowFreeTitles = true;
        public int maxPageSize = 28;
    }

    public static final class Card extends cn.lunadeer.deertitle.utils.configuration.ConfigurationPart {
        public String material = "NAME_TAG";
        public boolean consumeOnUse = true;
        public boolean requireSneakToUse = false;
    }

    public static final class Feedback extends cn.lunadeer.deertitle.utils.configuration.ConfigurationPart {
        public boolean enableSounds = true;
        public String equipSuccessSound = "ITEM_ARMOR_EQUIP_CHAIN";
        public String removeSuccessSound = "ITEM_ARMOR_EQUIP_LEATHER";
        public String purchaseSuccessSound = "ENTITY_EXPERIENCE_ORB_PICKUP";
        public String cardUseSuccessSound = "ITEM_BOOK_PAGE_TURN";
        public String failureSound = "ENTITY_VILLAGER_NO";
        public float soundVolume = 1.0F;
        public float soundPitch = 1.0F;
    }

    public static final class Ui extends cn.lunadeer.deertitle.utils.configuration.ConfigurationPart {
        public int playerPageSize = 28;
        public int adminPageSize = 28;
        public boolean fillEmptySlots = true;
    }

    public static final class Tasks extends cn.lunadeer.deertitle.utils.configuration.ConfigurationPart {
        public long expireCheckIntervalTicks = 1200L;
        public long tabRefreshIntervalTicks = 100L;
    }
}
