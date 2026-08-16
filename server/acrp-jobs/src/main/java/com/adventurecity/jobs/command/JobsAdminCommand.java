package com.adventurecity.jobs.command;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.ai.NpcPersona;
import com.adventurecity.jobs.config.JobDefinition;
import com.adventurecity.jobs.config.Zone;
import com.adventurecity.jobs.storage.PlayerData;
import com.adventurecity.jobs.storage.PlayerDataManager;
import com.adventurecity.jobs.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** /jobsadmin - placing zones, reloading config and inspecting a player. */
public final class JobsAdminCommand implements CommandExecutor, TabCompleter {

    private final ACRPJobsPlugin plugin;

    public JobsAdminCommand(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("acrp.jobs.admin")) {
            plugin.msg().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if ("reload".equals(sub)) {
            plugin.reloadEverything();
            plugin.msg().send(sender, "general.reloaded");
            return true;
        }

        if ("stats".equals(sub)) {
            if (args.length < 2) {
                plugin.msg().send(sender, "general.usage", "usage", "/jobsadmin stats <لاعب>");
                return true;
            }
            stats(sender, args[1]);
            return true;
        }

        if ("zone".equals(sub)) {
            return zone(sender, args);
        }

        if ("npc".equals(sub)) {
            return npc(sender, args);
        }

        help(sender);
        return true;
    }

    /** Links an npcs.yml persona to whatever NPC entity the admin is looking at. */
    private boolean npc(CommandSender sender, String[] args) {
        if (args.length < 2) {
            help(sender);
            return true;
        }
        String action = args[1].toLowerCase();

        if ("list".equals(action)) {
            sender.sendMessage(plugin.msg().get("npc.list-header", "count", plugin.npcs().all().size()));
            for (NpcPersona persona : plugin.npcs().all()) {
                sender.sendMessage(plugin.msg().get("npc.list-line",
                        "npc", persona.id(),
                        "name", Msg.plain(persona.name()),
                        "entity", persona.entityName().isEmpty()
                                ? "-" : Msg.plain(persona.entityName())));
            }
            return true;
        }

        if (args.length < 3) {
            plugin.msg().send(sender, "general.usage", "usage", "/jobsadmin npc " + action + " <معرّف>");
            return true;
        }
        NpcPersona persona = plugin.npcs().get(args[2]);
        if (persona == null) {
            plugin.msg().send(sender, "npc.unknown");
            return true;
        }

        if ("unlink".equals(action)) {
            persona.entityName("");
            plugin.npcs().saveLinks();
            plugin.msg().send(sender, "npc.unlinked", "npc", persona.id());
            return true;
        }

        if ("link".equals(action)) {
            if (!(sender instanceof Player)) {
                plugin.msg().send(sender, "general.player-only");
                return true;
            }
            Entity target = lookedAtEntity((Player) sender);
            if (target == null) {
                plugin.msg().send(sender, "npc.look-at-npc");
                return true;
            }
            String name = target.getCustomName();
            if (name == null || name.isEmpty()) {
                plugin.msg().send(sender, "npc.no-name");
                return true;
            }
            persona.entityName(name);
            plugin.npcs().saveLinks();
            plugin.msg().send(sender, "npc.linked", "npc", persona.id(), "entity", Msg.plain(name));
            return true;
        }

        help(sender);
        return true;
    }

    /**
     * Closest entity roughly along the player's line of sight. Bukkit 1.12.2 has no ray-trace
     * helper, so this compares direction vectors instead.
     */
    private Entity lookedAtEntity(Player player) {
        Vector eye = player.getEyeLocation().toVector();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : player.getNearbyEntities(6.0D, 6.0D, 6.0D)) {
            Vector toEntity = entity.getLocation().add(0.0D, 1.0D, 0.0D).toVector().subtract(eye);
            double distance = toEntity.length();
            if (distance < 0.5D) {
                continue;
            }
            if (toEntity.normalize().dot(direction) < 0.94D) {
                continue;
            }
            if (distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean zone(CommandSender sender, String[] args) {
        if (args.length < 2) {
            help(sender);
            return true;
        }
        String action = args[1].toLowerCase();

        if ("list".equals(action)) {
            if (plugin.zones().all().isEmpty()) {
                plugin.msg().send(sender, "admin.zone-none");
                return true;
            }
            sender.sendMessage(plugin.msg().get("admin.zone-list-header",
                    "count", plugin.zones().all().size()));
            for (Zone zone : plugin.zones().all()) {
                sender.sendMessage(plugin.msg().get("admin.zone-list-line",
                        "zone", zone.id(),
                        "group", zone.group().isEmpty() ? "-" : zone.group(),
                        "world", zone.worldName(),
                        "x", (int) zone.x(),
                        "y", (int) zone.y(),
                        "z", (int) zone.z(),
                        "radius", (int) zone.radius()));
            }
            return true;
        }

        if ("check".equals(action)) {
            check(sender);
            return true;
        }

        if ("set".equals(action)) {
            if (!(sender instanceof Player)) {
                plugin.msg().send(sender, "general.player-only");
                return true;
            }
            if (args.length < 3) {
                plugin.msg().send(sender, "general.usage", "usage", "/jobsadmin zone set <اسم> [نصف القطر] [مجموعة]");
                return true;
            }
            Player player = (Player) sender;
            double radius = 6.0D;
            if (args.length > 3) {
                try {
                    radius = Double.parseDouble(args[3]);
                } catch (NumberFormatException ex) {
                    plugin.msg().send(sender, "general.invalid-number");
                    return true;
                }
            }
            String group = args.length > 4 ? args[4].toLowerCase() : null;
            Zone zone = plugin.zones().set(args[2].toLowerCase(), player.getLocation(), radius, group);
            plugin.msg().send(sender, "admin.zone-set", "zone", zone.id(), "radius", (int) zone.radius());
            return true;
        }

        if ("group".equals(action)) {
            if (args.length < 4) {
                plugin.msg().send(sender, "general.usage", "usage", "/jobsadmin zone group <اسم> <مجموعة>");
                return true;
            }
            Zone zone = plugin.zones().get(args[2]);
            if (zone == null) {
                plugin.msg().send(sender, "admin.zone-unknown");
                return true;
            }
            zone.group(args[3].toLowerCase());
            plugin.zones().save();
            plugin.msg().send(sender, "admin.zone-set", "zone", zone.id(), "radius", (int) zone.radius());
            return true;
        }

        if ("del".equals(action) || "delete".equals(action) || "remove".equals(action)) {
            if (args.length < 3) {
                plugin.msg().send(sender, "general.usage", "usage", "/jobsadmin zone del <اسم>");
                return true;
            }
            if (plugin.zones().remove(args[2])) {
                plugin.msg().send(sender, "admin.zone-deleted", "zone", args[2].toLowerCase());
            } else {
                plugin.msg().send(sender, "admin.zone-unknown");
            }
            return true;
        }

        help(sender);
        return true;
    }

    /** Lists everything the loaded jobs need but the map does not have yet. */
    private void check(CommandSender sender) {
        Set<String> ids = plugin.jobs().requiredZoneIds();
        Set<String> groups = plugin.jobs().requiredZoneGroups();
        List<String> missingIds = new ArrayList<String>();
        List<String> missingGroups = new ArrayList<String>();

        for (String id : ids) {
            if (!plugin.zones().exists(id)) {
                missingIds.add(id);
            }
        }
        for (String group : groups) {
            if (!plugin.zones().groupExists(group)) {
                missingGroups.add(group);
            }
        }

        sender.sendMessage(Msg.color("&8&m---------------------------------"));
        if (missingIds.isEmpty() && missingGroups.isEmpty()) {
            sender.sendMessage(Msg.color("&aكل المناطق المطلوبة موجودة. النظام جاهز للعب."));
        } else {
            sender.sendMessage(Msg.color("&cناقص عليك:"));
            for (String id : missingIds) {
                sender.sendMessage(Msg.color("&7- منطقة &f" + id
                        + " &8➜ &7/jobsadmin zone set " + id + " 6"));
            }
            for (String group : missingGroups) {
                sender.sendMessage(Msg.color("&7- مجموعة &f" + group
                        + " &8➜ &7/jobsadmin zone set <اسم> 5 " + group));
            }
        }
        sender.sendMessage(Msg.color("&8&m---------------------------------"));
    }

    private void stats(final CommandSender sender, final String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            plugin.msg().send(sender, "general.player-not-found", "player", name);
            return;
        }
        final PlayerData data = plugin.players().get(target);
        if (data == null) {
            plugin.msg().send(sender, "general.player-not-found", "player", name);
            return;
        }

        sender.sendMessage(plugin.msg().get("admin.stats-header", "player", target.getName()));
        line(sender, "الرصيد", plugin.economy().format(data.balance()));
        JobDefinition job = plugin.jobs().get(data.currentJob());
        line(sender, "الوظيفة", job == null ? "-" : ChatColor.stripColor(Msg.color(job.name())));
        if (job != null) {
            line(sender, "الرتبة", plugin.jobManager().gradeOf(data, job).name());
        }
        line(sender, "الدوام", data.onDuty() ? "نعم" : "لا");
        line(sender, "أرباح اليوم", plugin.economy().format(data.earnedToday(PlayerDataManager.epochDay())));
        line(sender, "خمول", plugin.activity().idleSeconds(target) + " ثانية");

        final java.util.UUID uuid = target.getUniqueId();
        plugin.players().query(
                () -> plugin.players().storage().contractStats(uuid, PlayerDataManager.startOfDayMillis()),
                stats -> {
                    if (stats != null) {
                        line(sender, "مهام اليوم", String.valueOf(stats[0]));
                        line(sender, "أجور المهام اليوم", plugin.economy().format(stats[1]));
                    }
                });
    }

    private void line(CommandSender sender, String key, String value) {
        sender.sendMessage(plugin.msg().get("admin.stats-line", "key", key, "value", value));
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Msg.color("&8&m---------------------------------"));
        sender.sendMessage(Msg.color("&b/jobsadmin zone set <اسم> [قطر] [مجموعة] &8- &7احفظ منطقة بمكانك"));
        sender.sendMessage(Msg.color("&b/jobsadmin zone group <اسم> <مجموعة> &8- &7ضع منطقة في مجموعة"));
        sender.sendMessage(Msg.color("&b/jobsadmin zone del <اسم> &8- &7احذف منطقة"));
        sender.sendMessage(Msg.color("&b/jobsadmin zone list &8- &7كل المناطق"));
        sender.sendMessage(Msg.color("&b/jobsadmin zone check &8- &7وش ناقص عشان الوظائف تشتغل"));
        sender.sendMessage(Msg.color("&b/jobsadmin npc link <معرّف> &8- &7اربط شخصية بالـ NPC اللي قدامك"));
        sender.sendMessage(Msg.color("&b/jobsadmin npc unlink <معرّف> &8- &7فك الربط"));
        sender.sendMessage(Msg.color("&b/jobsadmin npc list &8- &7كل الشخصيات وحالة ربطها"));
        sender.sendMessage(Msg.color("&b/jobsadmin reload &8- &7إعادة تحميل الإعدادات"));
        sender.sendMessage(Msg.color("&b/jobsadmin stats <لاعب> &8- &7إحصائيات لاعب"));
        sender.sendMessage(Msg.color("&8&m---------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (!sender.hasPermission("acrp.jobs.admin")) {
            return out;
        }
        if (args.length == 1) {
            for (String sub : Arrays.asList("zone", "npc", "reload", "stats")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 2 && "npc".equals(args[0].toLowerCase())) {
            for (String sub : Arrays.asList("link", "unlink", "list")) {
                if (sub.startsWith(args[1].toLowerCase())) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 3 && "npc".equals(args[0].toLowerCase())) {
            for (NpcPersona persona : plugin.npcs().all()) {
                if (persona.id().startsWith(args[2].toLowerCase())) {
                    out.add(persona.id());
                }
            }
            return out;
        }
        if (args.length == 2 && "zone".equals(args[0].toLowerCase())) {
            for (String sub : Arrays.asList("set", "group", "del", "list", "check")) {
                if (sub.startsWith(args[1].toLowerCase())) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 3 && "zone".equals(args[0].toLowerCase())) {
            String action = args[1].toLowerCase();
            if ("del".equals(action) || "group".equals(action)) {
                for (Zone zone : plugin.zones().all()) {
                    if (zone.id().startsWith(args[2].toLowerCase())) {
                        out.add(zone.id());
                    }
                }
            } else if ("set".equals(action)) {
                for (String id : plugin.jobs().requiredZoneIds()) {
                    if (id.startsWith(args[2].toLowerCase()) && !plugin.zones().exists(id)) {
                        out.add(id);
                    }
                }
            }
        }
        return out;
    }
}
