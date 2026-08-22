package com.adventurecity.jobs.command;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.ai.NpcPersona;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /talk - start or end a conversation by name.
 *
 * <p>Right-clicking a linked NPC is the normal way in; this command is the fallback so the
 * dialogue system can be tested before any NPC has been placed on the map.</p>
 */
public final class TalkCommand implements CommandExecutor, TabCompleter {

    private final ACRPJobsPlugin plugin;

    public TalkCommand(ACRPJobsPlugin plugin) {
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

        if (args.length == 0 || "end".equalsIgnoreCase(args[0]) || "stop".equalsIgnoreCase(args[0])) {
            plugin.dialogue().end(player, true);
            return true;
        }

        NpcPersona persona = plugin.npcs().get(args[0]);
        if (persona == null) {
            plugin.msg().send(player, "npc.unknown");
            return true;
        }
        plugin.dialogue().start(player, persona, null);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length != 1) {
            return out;
        }
        String prefix = args[0].toLowerCase();
        if ("end".startsWith(prefix)) {
            out.add("end");
        }
        for (NpcPersona persona : plugin.npcs().all()) {
            if (persona.id().startsWith(prefix)) {
                out.add(persona.id());
            }
        }
        return out;
    }
}
