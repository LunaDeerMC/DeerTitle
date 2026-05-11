package cn.lunadeer.deertitle.command.player;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.command.SubCommand;
import cn.lunadeer.deertitle.configuration.Language;
import cn.lunadeer.deertitle.utils.compat.BukkitCompat;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

public final class HelpCommand extends SubCommand {

    private final Map<String, SubCommand> playerCommands;
    private final Map<String, SubCommand> adminCommands;

    public HelpCommand(Map<String, SubCommand> playerCommands, Map<String, SubCommand> adminCommands) {
        this.playerCommands = playerCommands;
        this.adminCommands = adminCommands;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getUsage() {
        return "[subcommand]";
    }

    @Override
    public String getDescription() {
        return "Show help for commands.";
    }

    @Override
    public void execute(DeerTitlePlugin plugin, CommandSender sender, String[] args) throws Exception {
        if (args.length >= 1 && !args[0].isBlank()) {
            showSubcommandHelp(plugin, sender, args[0]);
            return;
        }
        Language language = plugin.getConfigService().language();
        BukkitCompat.sendMessage(sender, plugin.getTextFormatter().deserialize("<gold>=== DeerTitle Commands ===</gold>"), plugin.getTextFormatter());
        for (SubCommand cmd : playerCommands.values()) {
            BukkitCompat.sendMessage(sender,
                    plugin.getTextFormatter().deserialize("<yellow>/title " + cmd.getName() + " " + cmd.getUsage() + "</yellow> <gray>- " + cmd.getDescription() + "</gray>"),
                    plugin.getTextFormatter());
        }
        if (sender.hasPermission("deertitle.admin")) {
            BukkitCompat.sendMessage(sender, plugin.getTextFormatter().deserialize("<gold>=== DeerTitle Admin ===</gold>"), plugin.getTextFormatter());
            for (SubCommand cmd : adminCommands.values()) {
                BukkitCompat.sendMessage(sender,
                        plugin.getTextFormatter().deserialize("<yellow>/title admin " + cmd.getName() + " " + cmd.getUsage() + "</yellow> <gray>- " + cmd.getDescription() + "</gray>"),
                        plugin.getTextFormatter());
            }
        }
    }

    private void showSubcommandHelp(DeerTitlePlugin plugin, CommandSender sender, String name) {
        SubCommand cmd = playerCommands.get(name.toLowerCase());
        String prefix = "/title ";
        if (cmd == null) {
            cmd = adminCommands.get(name.toLowerCase());
            prefix = "/title admin ";
        }
        if (cmd == null) {
            send(plugin, sender, plugin.getConfigService().language().command.unknownSubcommand, name);
            return;
        }
        BukkitCompat.sendMessage(sender,
                plugin.getTextFormatter().deserialize("<yellow>" + prefix + cmd.getName() + " " + cmd.getUsage() + "</yellow> <gray>- " + cmd.getDescription() + "</gray>"),
                plugin.getTextFormatter());
    }

    @Override
    public List<String> suggestions(DeerTitlePlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        return List.of();
    }
}
