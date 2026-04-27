package cn.lunadeer.deertitle;

import cn.lunadeer.deertitle.configuration.ConfigService;
import cn.lunadeer.deertitle.command.TitleCommand;
import cn.lunadeer.deertitle.database.DatabaseManager;
import cn.lunadeer.deertitle.database.MigrationRunner;
import cn.lunadeer.deertitle.database.repository.RepositoryRegistry;
import cn.lunadeer.deertitle.display.TitleDisplayListener;
import cn.lunadeer.deertitle.economy.EconomyService;
import cn.lunadeer.deertitle.listener.TitleCardListener;
import cn.lunadeer.deertitle.listener.MenuListener;
import cn.lunadeer.deertitle.placeholder.DeerTitlePlaceholderExpansion;
import cn.lunadeer.deertitle.service.ShopService;
import cn.lunadeer.deertitle.service.TitleCardService;
import cn.lunadeer.deertitle.service.TitleService;
import cn.lunadeer.deertitle.text.TextFormatter;
import cn.lunadeer.deertitle.utils.scheduler.Scheduler;
import org.bukkit.plugin.java.JavaPlugin;

public final class DeerTitlePlugin extends JavaPlugin {

    private ConfigService configService;
    private DatabaseManager databaseManager;
    private RepositoryRegistry repositories;
    private TextFormatter textFormatter;
    private TitleService titleService;
    private EconomyService economyService;
    private ShopService shopService;
    private TitleCardService titleCardService;
    private DeerTitlePlaceholderExpansion placeholderExpansion;

    @Override
    public void onEnable() {
        try {
            this.configService = new ConfigService(this);
            this.textFormatter = new TextFormatter();
            new Scheduler(this);
            reloadRuntime(false);
            TitleCommand titleCommand = new TitleCommand(this);
            if (getCommand("deertitle") != null) {
                getCommand("deertitle").setExecutor(titleCommand);
                getCommand("deertitle").setTabCompleter(titleCommand);
            }
            getServer().getPluginManager().registerEvents(new TitleDisplayListener(this), this);
            getServer().getPluginManager().registerEvents(new TitleCardListener(this), this);
            getServer().getPluginManager().registerEvents(new MenuListener(this), this);
            getLogger().info(
                    "DeerTitle enabled. Folia=" + isFolia()
                            + ", PlaceholderAPI=" + hasPlaceholderApi()
                            + ", Vault=" + hasVault()
                            + ", Database=" + databaseManager.getType().name()
            );
        } catch (Exception exception) {
            getLogger().severe("Failed to initialize DeerTitle: " + exception.getMessage());
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        teardownRuntime();
        getLogger().info("DeerTitle disabled.");
    }

    public void reloadRuntime() throws Exception {
        reloadRuntime(true);
    }

    private void reloadRuntime(boolean logReload) throws Exception {
        teardownRuntime();
        configService.load();
        databaseManager = new DatabaseManager(this, configService.config().database);
        databaseManager.start();
        new MigrationRunner(this, databaseManager).migrate();
        repositories = new RepositoryRegistry(databaseManager);
        titleService = new TitleService(this, repositories, textFormatter);
        economyService = new EconomyService(this, repositories);
        shopService = new ShopService(this, repositories, titleService, economyService);
        titleCardService = new TitleCardService(this, repositories, titleService, textFormatter);
        registerPlaceholderExpansion();
        initializeOnlinePlayers();
        scheduleTasks();
        if (logReload) {
            getLogger().info("DeerTitle runtime reloaded.");
        }
    }

    private void teardownRuntime() {
        Scheduler.cancelAll();
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (databaseManager != null) {
            databaseManager.close();
            databaseManager = null;
        }
    }

    private void registerPlaceholderExpansion() {
        if (hasPlaceholderApi()) {
            placeholderExpansion = new DeerTitlePlaceholderExpansion(this);
            placeholderExpansion.register();
        }
    }

    private void initializeOnlinePlayers() throws Exception {
        for (var player : getServer().getOnlinePlayers()) {
            titleService.ensurePlayerRecord(player);
            if (!hasPlaceholderApi()) {
                titleService.refreshPlayerListName(player);
            }
        }
    }

    private void scheduleTasks() {
        Scheduler.runTaskRepeat(() -> {
            try {
                TitleService.ExpiryCleanupResult result = titleService.pruneExpiredTitles();
                if (!hasPlaceholderApi()) {
                    for (var playerId : result.affectedPlayers()) {
                        var player = getServer().getPlayer(playerId);
                        if (player != null && player.isOnline()) {
                            Scheduler.runEntityTask(() -> {
                                try {
                                    titleService.refreshPlayerListName(player);
                                } catch (Exception exception) {
                                    getLogger().warning("Failed to refresh expired title display for " + player.getName() + ": " + exception.getMessage());
                                }
                            }, player);
                        }
                    }
                }
            } catch (Exception exception) {
                getLogger().warning("Failed to prune expired titles: " + exception.getMessage());
            }
        }, 40L, configService.config().tasks.expireCheckIntervalTicks);
        if (!hasPlaceholderApi()) {
            Scheduler.runTaskRepeat(() -> {
                for (var player : getServer().getOnlinePlayers()) {
                    Scheduler.runEntityTask(() -> {
                        try {
                            titleService.refreshPlayerListName(player);
                        } catch (Exception exception) {
                            getLogger().warning("Failed to refresh title tab name for " + player.getName() + ": " + exception.getMessage());
                        }
                    }, player);
                }
            }, 40L, configService.config().tasks.tabRefreshIntervalTicks);
        }
    }

    public ConfigService getConfigService() {
        return configService;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public RepositoryRegistry getRepositories() {
        return repositories;
    }

    public TextFormatter getTextFormatter() {
        return textFormatter;
    }

    public TitleService getTitleService() {
        return titleService;
    }

    public EconomyService getEconomyService() {
        return economyService;
    }

    public ShopService getShopService() {
        return shopService;
    }

    public TitleCardService getTitleCardService() {
        return titleCardService;
    }

    public boolean isFolia() {
        return isClassPresent("io.papermc.paper.threadedregions.RegionizedServer");
    }

    public boolean hasPlaceholderApi() {
        return getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public boolean hasVault() {
        return getServer().getPluginManager().getPlugin("Vault") != null;
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
