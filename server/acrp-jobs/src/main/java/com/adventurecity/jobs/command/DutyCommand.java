package com.adventurecity.jobs.command;

import com.adventurecity.jobs.ACRPJobsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /duty - clock in and out. */
public final class DutyCommand implements CommandExecutor {

    private final ACRPJobsPlugin plugin;

    public DutyCommand(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.msg().send(sender, "general.player-only");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("acrp.jobs.use")) {
            plugin.msg().send(player, "general.no-permission");
            return true;
        }
        if (plugin.players().get(player) == null) {
            plugin.msg().sendRaw(player, "&7جاري تحميل بياناتك... حاول بعد لحظة.");
            return true;
        }
        plugin.duty().toggle(player);
        return true;
    }
}
