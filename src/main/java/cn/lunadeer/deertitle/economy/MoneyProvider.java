package cn.lunadeer.deertitle.economy;

import java.math.BigDecimal;
import org.bukkit.entity.Player;

public interface MoneyProvider {
    enum Result { SUCCESS, DECLINED, ERROR }
    Result withdraw(Player player, BigDecimal amount);
}
