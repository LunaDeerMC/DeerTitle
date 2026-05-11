package cn.lunadeer.deertitle.command.admin;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.command.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public final class AdminCreateCommand extends SubCommand {

    @Override
    public String getName() {
        return "create";
    }

    @Override
    public String getUsage() {
        return "<title> || <description>";
    }

    @Override
    public String getDescription() {
        return "Create a new title.";
    }

    @Override
    public boolean requiresAdmin() {
        return true;
    }

    @Override
    public void execute(DeerTitlePlugin plugin, CommandSender sender, String[] args) throws Exception {
        requireAdmin(plugin, sender);
        if (args.length < 1) {
            send(plugin, sender, plugin.getConfigService().language().command.unknownSubcommand, "");
            return;
        }
        String[] parts = joinFrom(args, 0).split("\\|\\|", 2);
        String titleText = parts[0].trim();
        String description = parts.length > 1 ? parts[1].trim() : "";
        var title = plugin.getTitleService().createTitle(titleText, description);
        send(plugin, sender, plugin.getConfigService().language().title.created, title.id(), title.title());
    }

    @Override
    public List<String> suggestions(DeerTitlePlugin plugin, CommandSender sender, String[] args) {
        return List.of();
    }
}
