package cn.lunadeer.deertitle.economy;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.database.model.PlayerInfoRecord;
import cn.lunadeer.deertitle.database.repository.RepositoryRegistry;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Locale;

public final class EconomyService {

    private final DeerTitlePlugin plugin;
    private final RepositoryRegistry repositories;
    private final Economy vaultEconomy;

    public EconomyService(DeerTitlePlugin plugin, RepositoryRegistry repositories) {
        this.plugin = plugin;
        this.repositories = repositories;
        this.vaultEconomy = resolveVaultEconomy();
    }

    public boolean isUsingVault() {
        return vaultEconomy != null;
    }

    public double getBalance(OfflinePlayer player) throws Exception {
        if (vaultEconomy != null) {
            return vaultEconomy.getBalance(player);
        }
        PlayerInfoRecord info = repositories.playerInfo().findOrCreate(
                player.getUniqueId(),
                player.getName(),
                plugin.getConfigService().config().economy.defaultBalance
        );
        return info.coin();
    }

    public boolean withdraw(OfflinePlayer player, double amount) throws Exception {
        if (amount <= 0) {
            return true;
        }
        if (vaultEconomy != null) {
            return vaultEconomy.withdrawPlayer(player, amount).transactionSuccess();
        }
        PlayerInfoRecord info = repositories.playerInfo().findOrCreate(
                player.getUniqueId(),
                player.getName(),
                plugin.getConfigService().config().economy.defaultBalance
        );
        if (info.coin() < amount) {
            return false;
        }
        repositories.playerInfo().updateCoin(player.getUniqueId(), info.coin() - amount);
        return true;
    }

    public void deposit(OfflinePlayer player, double amount) throws Exception {
        if (amount <= 0) {
            return;
        }
        if (vaultEconomy != null) {
            vaultEconomy.depositPlayer(player, amount);
            return;
        }
        PlayerInfoRecord info = repositories.playerInfo().findOrCreate(
                player.getUniqueId(),
                player.getName(),
                plugin.getConfigService().config().economy.defaultBalance
        );
        repositories.playerInfo().updateCoin(player.getUniqueId(), info.coin() + amount);
    }

    public void setBuiltInBalance(OfflinePlayer player, double amount) throws Exception {
        repositories.playerInfo().findOrCreate(
                player.getUniqueId(),
                player.getName(),
                plugin.getConfigService().config().economy.defaultBalance
        );
        repositories.playerInfo().updateCoin(player.getUniqueId(), amount);
    }

    public String format(double amount) {
        if (vaultEconomy != null) {
            return vaultEconomy.format(amount);
        }
        String symbol = plugin.getConfigService().config().economy.builtInCurrencySymbol;
        return symbol + String.format(Locale.US, "%.2f", amount);
    }

    private Economy resolveVaultEconomy() {
        if (!plugin.hasVault() || !plugin.getConfigService().config().economy.preferVault) {
            return null;
        }
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }
}
