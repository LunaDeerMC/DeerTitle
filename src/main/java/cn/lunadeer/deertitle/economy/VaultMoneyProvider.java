package cn.lunadeer.deertitle.economy;

import java.math.BigDecimal;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** This class is only loaded when Vault mode is explicitly selected. */
public final class VaultMoneyProvider implements MoneyProvider {
    @Override public Result withdraw(Player player, BigDecimal amount) {
        var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null || !registration.getProvider().isEnabled()) return Result.DECLINED;
        if (amount.signum() == 0) return Result.SUCCESS;
        try {
            EconomyResponse response = registration.getProvider().withdrawPlayer(player, amount.doubleValue());
            if (response == null) return Result.UNKNOWN;
            return response.transactionSuccess() ? Result.SUCCESS : Result.DECLINED;
        } catch (RuntimeException ex) {
            // A provider may have charged before throwing. Never repeat or refund speculatively.
            return Result.UNKNOWN;
        }
    }

    public static boolean available() {
        var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        return registration != null && registration.getProvider().isEnabled();
    }
}
