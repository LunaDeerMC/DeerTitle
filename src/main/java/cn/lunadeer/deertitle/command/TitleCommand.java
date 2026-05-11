package cn.lunadeer.deertitle.command;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.command.admin.*;
import cn.lunadeer.deertitle.command.player.*;
import cn.lunadeer.deertitle.ui.MainMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;

public final class TitleCommand implements TabExecutor {

    private final DeerTitlePlugin plugin;
    private final Map<String, SubCommand> commands;
    private final Map<String, SubCommand> adminCommands;

    public TitleCommand(DeerTitlePlugin plugin) {
        this.plugin = plugin;
        this.adminCommands = new LinkedHashMap<>();
        registerAdmin(new AdminCreateCommand());
        registerAdmin(new AdminSetDescCommand());
        registerAdmin(new AdminSetEnabledCommand());
        registerAdmin(new AdminGrantCommand());
        registerAdmin(new AdminRevokeCommand());
        registerAdmin(new AdminShopCommand());
        registerAdmin(new AdminCoinCommand());
        registerAdmin(new AdminCardCommand());

        this.commands = new LinkedHashMap<>();
        register(new HelpCommand(commands, adminCommands));
        register(new ListCommand());
        register(new WearCommand());
        register(new RemoveCommand());
        register(new CurrentCommand());
        register(new BalanceCommand());
        register(new ShopCommand());
        register(new BuyCommand());
        register(new ReloadCommand());
    }

    private void register(SubCommand cmd) {
        commands.put(cmd.getName(), cmd);
    }

    private void registerAdmin(SubCommand cmd) {
        adminCommands.put(cmd.getName(), cmd);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length == 0) {
                if (sender instanceof Player player) {
                    new MainMenu(plugin, player).open();
                    return true;
                }
                commands.get("help").execute(plugin, sender, new String[0]);
                return true;
            }

            if (args[0].equalsIgnoreCase("equip")) {
                args = shiftFirst(args, "wear");
            }

            if (args[0].equalsIgnoreCase("admin")) {
                String[] subArgs = tail(args);
                if (subArgs.length == 0) {
                    commands.get("help").execute(plugin, sender, new String[0]);
                    return true;
                }
                SubCommand cmd = adminCommands.get(subArgs[0].toLowerCase());
                if (cmd == null) {
                    SubCommand help = commands.get("help");
                    help.send(plugin, sender, plugin.getConfigService().language().command.unknownSubcommand, subArgs[0]);
                    return true;
                }
                cmd.execute(plugin, sender, tail(subArgs));
                return true;
            }

            SubCommand cmd = commands.get(args[0].toLowerCase());
            if (cmd == null) {
                SubCommand help = commands.get("help");
                help.send(plugin, sender, plugin.getConfigService().language().command.unknownSubcommand, args[0]);
                return true;
            }
            cmd.execute(plugin, sender, tail(args));
        } catch (Exception exception) {
            SubCommand base = commands.get("help"); // any command works, just need helper methods
            base.send(plugin, sender, plugin.getConfigService().language().general.internalError + " <gray>" + exception.getMessage() + "</gray>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        try {
            if (args.length == 0) {
                return List.of();
            }

            if (args.length == 1) {
                List<String> candidates = new ArrayList<>(commands.keySet());
                candidates.add("admin");
                candidates.add("equip");
                List<String> result = new ArrayList<>();
                StringUtil.copyPartialMatches(args[0], candidates, result);
                return result;
            }

            if (args[0].equalsIgnoreCase("equip")) {
                SubCommand wear = commands.get("wear");
                return filter(wear.suggestions(plugin, sender, tail(tail(args))), last(args));
            }

            if (args[0].equalsIgnoreCase("admin")) {
                if (args.length == 2) {
                    List<String> candidates = new ArrayList<>(adminCommands.keySet());
                    List<String> result = new ArrayList<>();
                    StringUtil.copyPartialMatches(args[1], candidates, result);
                    return result;
                }
                SubCommand cmd = adminCommands.get(args[1].toLowerCase());
                if (cmd == null) {
                    return List.of();
                }
                return filter(cmd.suggestions(plugin, sender, tail(tail(args))), last(args));
            }

            SubCommand cmd = commands.get(args[0].toLowerCase());
            if (cmd == null) {
                return List.of();
            }
            return filter(cmd.suggestions(plugin, sender, tail(args)), last(args));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> filter(List<String> candidates, String partial) {
        if (partial == null || partial.isEmpty()) {
            return candidates;
        }
        List<String> result = new ArrayList<>();
        StringUtil.copyPartialMatches(partial, candidates, result);
        return result;
    }

    private String[] tail(String[] args) {
        if (args.length <= 1) {
            return new String[0];
        }
        return Arrays.copyOfRange(args, 1, args.length);
    }

    private String[] shiftFirst(String[] args, String newFirst) {
        String[] result = new String[args.length];
        result[0] = newFirst;
        System.arraycopy(args, 1, result, 1, args.length - 1);
        return result;
    }

    private String last(String[] args) {
        return args.length > 0 ? args[args.length - 1] : "";
    }
}
