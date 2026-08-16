package com.adventurecity.jobs.command;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.dispatch.DispatchCall;
import com.adventurecity.jobs.dispatch.DispatchService;
import com.adventurecity.jobs.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** /taxi - passengers call, drivers accept. The same flow police and EMS will use for 911. */
public final class TaxiCommand implements CommandExecutor, TabCompleter {

    private final ACRPJobsPlugin plugin;

    public TaxiCommand(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.msg().send(sender, "general.player-only");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("acrp.jobs.taxi")) {
            plugin.msg().send(player, "general.no-permission");
            return true;
        }

        String sub = args.length == 0 ? "call" : args[0].toLowerCase();

        if ("call".equals(sub)) {
            plugin.dispatch().createCall(player, DispatchService.CHANNEL_TAXI);
            return true;
        }
        if ("cancel".equals(sub)) {
            plugin.dispatch().cancelCall(player);
            return true;
        }
        if ("accept".equals(sub)) {
            if (args.length < 2) {
                plugin.msg().send(player, "general.usage", "usage", "/taxi accept <رقم>");
                return true;
            }
            try {
                plugin.dispatch().accept(player, Integer.parseInt(args[1].trim()));
            } catch (NumberFormatException ex) {
                plugin.msg().send(player, "general.invalid-number");
            }
            return true;
        }
        if ("list".equals(sub)) {
            List<DispatchCall> open = plugin.dispatch().open(DispatchService.CHANNEL_TAXI);
            if (open.isEmpty()) {
                plugin.msg().sendRaw(player, "&7ما فيه طلبات مفتوحة حاليا.");
                return true;
            }
            for (DispatchCall call : open) {
                plugin.msg().send(player, "taxi.driver-alert",
                        "player", call.callerName(),
                        "distance", distance(player, call),
                        "id", call.id());
            }
            return true;
        }

        player.sendMessage(Msg.color("&b/taxi call &8- &7اطلب تاكسي"));
        player.sendMessage(Msg.color("&b/taxi cancel &8- &7ألغِ طلبك"));
        player.sendMessage(Msg.color("&b/taxi accept <رقم> &8- &7للسائقين: استلم الطلب"));
        player.sendMessage(Msg.color("&b/taxi list &8- &7الطلبات المفتوحة"));
        return true;
    }

    private String distance(Player player, DispatchCall call) {
        if (call.location().getWorld() == null || !call.location().getWorld().equals(player.getWorld())) {
            return "-";
        }
        return String.valueOf((int) call.location().distance(player.getLocation()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            for (String sub : Arrays.asList("call", "accept", "cancel", "list")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 2 && "accept".equals(args[0].toLowerCase())) {
            for (DispatchCall call : plugin.dispatch().open(DispatchService.CHANNEL_TAXI)) {
                out.add(String.valueOf(call.id()));
            }
        }
        return out;
    }
}
