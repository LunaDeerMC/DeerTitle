package cn.lunadeer.deertitle;

import cn.lunadeer.deertitle.command.DeerTitleCommand;
import cn.lunadeer.deertitle.config.Settings;
import cn.lunadeer.deertitle.domain.Currency;
import cn.lunadeer.deertitle.economy.MoneyProvider;
import cn.lunadeer.deertitle.economy.VaultMoneyProvider;
import cn.lunadeer.deertitle.platform.DatabaseFactory;
import cn.lunadeer.deertitle.platform.DriverCleanup;
import cn.lunadeer.deertitle.platform.TitleExpansion;
import cn.lunadeer.deertitle.storage.SqlDialect;
import cn.lunadeer.deertitle.storage.TitleStore;
import cn.lunadeer.deertitle.text.TitleText;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

public final class DeerTitlePlugin extends JavaPlugin {
    private HikariDataSource pool;
    private TitleStore store;
    private DeerTitleRuntime runtime;
    private DeerTitleCommand commands;
    private TitleExpansion expansion;
    private boolean mysqlUsed;

    @Override public void onEnable() {
        try {
            saveDefaultConfig();
            Settings settings = Settings.load(this);
            mysqlUsed = settings.database().type().equals("mysql");
            TitleText.parse(settings.prefix());
            TitleText.parse(settings.suffix());
            TitleText.parse(settings.emptyTitle());
            MoneyProvider money = null;
            if (settings.currency() == Currency.VAULT) {
                if (!getServer().getPluginManager().isPluginEnabled("Vault") || !VaultMoneyProvider.available())
                    throw new IllegalArgumentException("Vault 模式需要已启用的 Vault 和经济插件；不会自动切换称号币");
                money = new VaultMoneyProvider();
            }
            pool = DatabaseFactory.create(settings.database());
            store = new TitleStore(pool, SqlDialect.parse(settings.database().type()));
            store.initialize();
            runtime = new DeerTitleRuntime(this, settings, store, money);
            commands = new DeerTitleCommand(this, runtime);
            var command = java.util.Objects.requireNonNull(getCommand("deertitle"));
            command.setExecutor(commands);
            command.setTabCompleter(commands);
            runtime.start();
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                expansion = new TitleExpansion(runtime, getPluginMeta().getVersion());
                if (!expansion.register()) getLogger().warning("PlaceholderAPI 占位符注册失败，请检查重复标识。");
            } else getLogger().info("未安装 PlaceholderAPI；安装后可在聊天和 Tab 中展示称号。");
            getLogger().info("DeerTitle 已就绪（" + settings.database().type() + " / " + settings.currency().name().toLowerCase(java.util.Locale.ROOT) + "）");
        } catch (Exception ex) {
            String reason = ex instanceof IllegalArgumentException ? ex.getMessage() : ex.getClass().getSimpleName();
            getLogger().severe("DeerTitle 启动失败：" + reason);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    public DeerTitleCommand commands() { return commands; }

    @Override public void onDisable() {
        try {
            if (commands != null) commands.clear();
            if (expansion != null) expansion.unregister();
            if (runtime != null) runtime.close();
            if (store != null) store.close();
            else if (pool != null) pool.close();
        } finally { DriverCleanup.release(getClassLoader(), mysqlUsed); }
    }
}
