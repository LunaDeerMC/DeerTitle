package cn.lunadeer.deertitle.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class DialogOperationTest {
    @Test void oldFailureCannotReplaceNewPageOrReconnect() {
        DialogUi ui = new DialogUi(access(null));
        Player player = player(UUID.randomUUID());
        UUID old = ui.beginExternalOperation(player);
        UUID current = ui.beginExternalOperation(player);
        assertFalse(ui.isCurrentOperation(player, old));
        assertDoesNotThrow(() -> ui.operationFailed(player, old, "旧操作失败"));
        assertTrue(ui.isCurrentOperation(player, current));

        ui.forget(player.getUniqueId());
        Player reconnected = player(player.getUniqueId());
        UUID afterReconnect = ui.beginExternalOperation(reconnected);
        assertDoesNotThrow(() -> ui.operationFailed(reconnected, current, "断线前操作失败"));
        assertTrue(ui.isCurrentOperation(reconnected, afterReconnect));

        ui.close();
        assertFalse(ui.isCurrentOperation(reconnected, afterReconnect));
        assertFalse(ui.isCurrentOperation(reconnected, ui.beginExternalOperation(reconnected)));
    }

    @Test void operationTokenIsBoundToPlayer() {
        DialogUi ui = new DialogUi(access(null));
        Player owner = player(UUID.randomUUID());
        Player other = player(UUID.randomUUID());
        UUID token = ui.beginExternalOperation(owner);
        assertFalse(ui.isCurrentOperation(other, token));
        assertDoesNotThrow(() -> ui.operationFailed(other, token, "其他玩家的失败"));
        assertTrue(ui.isCurrentOperation(owner, token));
    }

    @Test void asynchronousLoadFailureIsIgnoredAfterNavigation() {
        AtomicReference<Consumer<String>> failure = new AtomicReference<>();
        DialogUi ui = new DialogUi(access(failure));
        Player player = player(UUID.randomUUID());
        ui.openInventory(player);
        assertNotNull(failure.get());
        UUID newerPage = ui.beginExternalOperation(player);
        assertDoesNotThrow(() -> failure.get().accept("数据库暂时不可用"));
        assertTrue(ui.isCurrentOperation(player, newerPage));
    }

    private static Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "hasPermission" -> true;
                case "toString" -> "DialogTestPlayer";
                case "hashCode" -> id.hashCode();
                case "equals" -> proxy == args[0];
                default -> throw new AssertionError("Unexpected player operation: " + method.getName());
            });
    }

    @SuppressWarnings("unchecked")
    private static UiAccess access(AtomicReference<Consumer<String>> failure) {
        return (UiAccess) Proxy.newProxyInstance(UiAccess.class.getClassLoader(), new Class<?>[] {UiAccess.class},
            (proxy, method, args) -> {
                if (method.getName().equals("async") && args.length == 4 && failure != null) {
                    failure.set((Consumer<String>) args[3]);
                    return null;
                }
                throw new AssertionError("Unexpected runtime operation: " + method.getName());
            });
    }
}
