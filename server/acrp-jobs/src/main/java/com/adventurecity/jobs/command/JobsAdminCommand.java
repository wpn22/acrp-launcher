package com.adventurecity.jobs.command;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.admin.SelfTest;
import com.adventurecity.jobs.ai.NpcPersona;
import com.adventurecity.jobs.config.JobDefinition;
import com.adventurecity.jobs.config.Zone;
import com.adventurecity.jobs.route.Route;
import com.adventurecity.jobs.route.RouteEditor;
import com.adventurecity.jobs.spot.SpotEditor;
import com.adventurecity.jobs.spot.SpotPool;
import com.adventurecity.jobs.spot.SpotRotation;
import com.adventurecity.jobs.spot.SpotType;
import com.adventurecity.jobs.storage.JobProgress;
import com.adventurecity.jobs.storage.PlayerData;
import com.adventurecity.jobs.storage.PlayerDataManager;
import com.adventurecity.jobs.training.TrainingPlan;
import com.adventurecity.jobs.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
import java.util.regex.Pattern;

/** /jobsadmin - placing zones, reloading config and inspecting a player. */
public final class JobsAdminCommand implements CommandExecutor, TabCompleter {

    /** Route ids end up as YAML keys and as command arguments, so keep them plain. */
    private static final Pattern ROUTE_ID = Pattern.compile("[a-z0-9_]{1,32}");

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

        if ("route".equals(sub)) {
            return route(sender, args);
        }

        if ("spot".equals(sub)) {
            return spot(sender, args);
        }

        if ("test".equals(sub)) {
            return test(sender, args);
        }

        help(sender);
        return true;
    }

    /**
     * /jobsadmin test - checks whatever a machine can check, plus the shortcuts that make the
     * parts needing human eyes quick to reach.
     */
    private boolean test(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase() : "all";

        if ("rotate".equals(action)) {
            if (args.length < 3) {
                plugin.msg().send(sender, "general.usage", "usage", "/jobsadmin test rotate <مجموعة|all>");
                return true;
            }
            int pools = 0;
            int slots = 0;
            for (SpotPool pool : plugin.spots().registry().all()) {
                if (!"all".equalsIgnoreCase(args[2]) && !pool.id().equalsIgnoreCase(args[2])) {
                    continue;
                }
                pools++;
                slots += pool.forceReturnsDue();
            }
            if (pools == 0) {
                plugin.msg().send(sender, "spot.unknown");
                return true;
            }
            sender.sendMessage(Msg.color("&aخلّيت &f" + slots + "&a خانة مستحقة الآن في &f"
                    + pools + "&a مجموعة - النقاط بترجع خلال ثانيتين بدون انتظار."));
            return true;
        }

        if ("kit".equals(action)) {
            if (!(sender instanceof Player)) {
                plugin.msg().send(sender, "general.player-only");
                return true;
            }
            Player player = (Player) sender;
            plugin.spots().editor().giveWand(player);
            plugin.routes().editor().giveWand(player);
            plugin.spots().pump().give(player);
            sender.sendMessage(Msg.color("&aأخذت عدّة التجربة: عصا النقاط، عصا المسارات، والمضخة."));
            return true;
        }

        if ("tp".equals(action)) {
            if (!(sender instanceof Player)) {
                plugin.msg().send(sender, "general.player-only");
                return true;
            }
            if (args.length < 3) {
                plugin.msg().send(sender, "general.usage", "usage", "/jobsadmin test tp <منطقة>");
                return true;
            }
            Zone zone = plugin.zones().get(args[2]);
            if (zone == null) {
                plugin.msg().send(sender, "admin.zone-unknown");
                return true;
            }
            Location target = zone.toLocation();
            if (target == null) {
                sender.sendMessage(Msg.color("&cعالم المنطقة &f" + zone.id() + "&c غير محمّل."));
                return true;
            }
            ((Player) sender).teleport(target);
            sender.sendMessage(Msg.color("&aانتقلت لـ &f" + zone.id()));
            return true;
        }

        if ("help".equals(action)) {
            testHelp(sender);
            return true;
        }

        new SelfTest(plugin, sender).run(action);
        return true;
    }

    private void testHelp(CommandSender sender) {
        sender.sendMessage(Msg.color("&8&m---------------------------------"));
        sender.sendMessage(Msg.color("&b/jobsadmin test &8- &7افحص كل شي وأعطني تقرير"));
        sender.sendMessage(Msg.color("&b/jobsadmin test <قسم> &8- &7افحص قسم واحد"));
        sender.sendMessage(Msg.color("&7  الأقسام: &fconfig jobs zones spots routes training npcs economy"));
        sender.sendMessage(Msg.color("&b/jobsadmin test rotate <مجموعة|all> &8- &7رجّع النقاط المنظّفة فوراً"));
        sender.sendMessage(Msg.color("&7  (بدل ما تنتظر respawnMinutes وأنت تجرّب)"));
        sender.sendMessage(Msg.color("&b/jobsadmin test kit &8- &7خذ العصي والمضخة"));
        sender.sendMessage(Msg.color("&b/jobsadmin test tp <منطقة> &8- &7انتقل لأي منطقة"));
        sender.sendMessage(Msg.color("&8&m---------------------------------"));
    }

    /** /jobsadmin spot - marking and tuning the work that appears around the city. */
    private boolean spot(CommandSender sender, String[] args) {
        if (args.length < 2) {
            spotHelp(sender);
            return true;
        }
        String action = args[1].toLowerCase();

        if ("list".equals(action)) {
            spotList(sender);
            return true;
        }

        if (!(sender instanceof Player)) {
            plugin.msg().send(sender, "general.player-only");
            return true;
        }
        Player player = (Player) sender;
        SpotEditor editor = plugin.spots().editor();

        if ("new".equals(action) || "edit".equals(action)) {
            if (args.length < 3) {
                plugin.msg().send(sender, "general.usage", "usage",
                        "/jobsadmin spot new <اسم> <TRASH|DIRT|PLANT|LAMP>");
                return true;
            }
            String id = args[2].toLowerCase();
            if (!ROUTE_ID.matcher(id).matches()) {
                plugin.msg().send(sender, "general.usage", "usage",
                        "/jobsadmin spot new <اسم بحروف إنجليزية وأرقام و _> <النوع>");
                return true;
            }
            SpotType type = args.length > 3 ? SpotType.fromString(args[3]) : null;
            if (type == null && !plugin.spots().registry().exists(id)) {
                plugin.msg().send(sender, "spot.type-unknown");
                return true;
            }
            editor.begin(player, id, type);
            return true;
        }

        if ("add".equals(action)) {
            editor.addPoint(player, player.getLocation());
            return true;
        }

        if ("undo".equals(action)) {
            editor.undo(player);
            return true;
        }

        if ("done".equals(action) || "finish".equals(action)) {
            editor.done(player);
            return true;
        }

        if ("confirm".equals(action)) {
            if (args.length < 3) {
                plugin.msg().send(sender, "general.usage", "usage", "/jobsadmin spot confirm yes|no");
                return true;
            }
            String answer = args[2].toLowerCase();
            editor.confirm(player, "yes".equals(answer) || "y".equals(answer) || "نعم".equals(answer));
            return true;
        }

        if ("cancel".equals(action)) {
            editor.cancel(player);
            return true;
        }

        if ("wand".equals(action)) {
            editor.giveWand(player);
            return true;
        }

        if ("show".equals(action)) {
            if (args.length < 3) {
                plugin.msg().send(sender, "general.usage", "usage", "/jobsadmin spot show <اسم>");
                return true;
            }
            SpotPool pool = plugin.spots().registry().get(args[2]);
            if (pool == null) {
                plugin.msg().send(sender, "spot.unknown");
                return true;
            }
            editor.preview(player, pool);
            return true;
        }

        if ("del".equals(action) || "delete".equals(action) || "remove".equals(action)) {
            if (args.length < 3) {
                plugin.msg().send(sender, "general.usage", "usage", "/jobsadmin spot del <اسم>");
                return true;
            }
            if (!plugin.spots().registry().remove(args[2])) {
                plugin.msg().send(sender, "spot.unknown");
                return true;
            }
            plugin.spots().load();
            plugin.msg().send(sender, "spot.deleted", "pool", args[2].toLowerCase());
            return true;
        }

        if ("set".equals(action)) {
            return spotSet(sender, args);
        }

        spotHelp(sender);
        return true;
    }

    private void spotList(CommandSender sender) {
        if (plugin.spots().registry().all().isEmpty()) {
            plugin.msg().send(sender, "spot.list-none");
            return;
        }
        sender.sendMessage(plugin.msg().get("spot.list-header",
                "count", plugin.spots().registry().size(),
                "bodies", plugin.spots().bodyCount(),
                "max", plugin.settings().maxActiveSpotEntities));
        long now = System.currentTimeMillis();
        for (SpotPool pool : plugin.spots().registry().all()) {
            long next = SpotRotation.secondsUntilNext(pool, now);
            sender.sendMessage(plugin.msg().get("spot.list-line",
                    "pool", pool.id(),
                    "type", pool.type().name(),
                    "points", pool.pointCount(),
                    "active", SpotRotation.countActive(pool),
                    "target", Math.min(pool.activeCount(), pool.pointCount()),
                    "next", next < 0L ? "-" : next + "ث",
                    "world", pool.worldName()));
        }
    }

    /** Changes one property of a saved pool without re-marking its points. */
    private boolean spotSet(CommandSender sender, String[] args) {
        if (args.length < 5) {
            plugin.msg().send(sender, "general.usage", "usage", "/jobsadmin spot set <اسم> <خاصية> <قيمة>");
            return true;
        }
        SpotPool pool = plugin.spots().registry().get(args[2]);
        if (pool == null) {
            plugin.msg().send(sender, "spot.unknown");
            return true;
        }

        String key = args[3].toLowerCase();
        String value = args[4];
        try {
            if ("activecount".equals(key)) {
                pool.activeCount(Integer.parseInt(value));
                value = String.valueOf(pool.activeCount());
            } else if ("respawnminutes".equals(key)) {
                pool.respawnMinutes(Integer.parseInt(value));
                value = String.valueOf(pool.respawnMinutes());
            } else if ("activationrange".equals(key)) {
                pool.activationRange(Double.parseDouble(value));
                value = String.valueOf(pool.activationRange());
            } else if ("type".equals(key)) {
                SpotType type = SpotType.fromString(value);
                if (type == null) {
                    plugin.msg().send(sender, "spot.type-unknown");
                    return true;
                }
                pool.type(type);
                value = pool.type().name();
            } else if ("swapblock".equals(key)) {
                pool.swapBlock(Boolean.parseBoolean(value));
                value = String.valueOf(pool.swapBlock());
            } else {
                plugin.msg().send(sender, "spot.set-unknown");
                return true;
            }
        } catch (NumberFormatException ex) {
            plugin.msg().send(sender, "general.invalid-number");
            return true;
        }

        plugin.spots().registry().save();
        plugin.spots().load();
        plugin.msg().send(sender, "spot.set-done", "key", key, "value", value);
        return true;
    }

    private void spotHelp(CommandSender sender) {
        sender.sendMessage(Msg.color("&8&m---------------------------------"));
        sender.sendMessage(Msg.color("&b/jobsadmin spot new <اسم> <النوع> &8- &7ابدأ التحديد (ياخذ العصا)"));
        sender.sendMessage(Msg.color("&7  الأنواع: &fTRASH &7زبالة &8| &fDIRT &7وساخة &8| &fPLANT &7زرع &8| &fLAMP &7إنارة"));
        sender.sendMessage(Msg.color("&7  نقرة يمين = نقطة &8| &7نقرة يسار = تراجع"));
        sender.sendMessage(Msg.color("&b/jobsadmin spot add &8- &7أضف موقعك كنقطة"));
        sender.sendMessage(Msg.color("&b/jobsadmin spot done &8- &7إنهاء + سؤال نعم/لا"));
        sender.sendMessage(Msg.color("&b/jobsadmin spot set <اسم> activeCount 9"));
        sender.sendMessage(Msg.color("&b/jobsadmin spot set <اسم> respawnMinutes 10"));
        sender.sendMessage(Msg.color("&b/jobsadmin spot show <اسم> &8- &7اعرض النقاط (برتقالي = فيها شغل)"));
        sender.sendMessage(Msg.color("&b/jobsadmin spot list &8| &bdel <اسم>"));
        sender.sendMessage(Msg.color("&8&m---------------------------------"));
    }

    /** /jobsadmin route - drawing and managing the paths the city walkers follow. */
    private boolean route(CommandSender sender, String[] args) {
        if (args.length < 2) {
            routeHelp(sender);
            return true;
        }
        String action = args[1].toLowerCase();

        if ("list".equals(action)) {
            routeList(sender);
            return true;
        }

        if (!(sender instanceof Player)) {
            plugin.msg().send(sender, "general.player-only");
            return true;
        }
        Player player = (Player) sender;
        RouteEditor editor = plugin.routes().editor();

        if ("new".equals(action) || "edit".equals(action)) {
            if (args.length < 3) {
                plugin.msg().send(sender, "general.usage", "usage", "/jobsadmin route new <اسم>");
                return true;
            }
            String id = args[2].toLowerCase();
            if (!ROUTE_ID.matcher(id).matches()) {
                plugin.msg().send(sender, "general.usage", "usage",
                        "/jobsadmin route new <اسم بحروف إنجليزية وأرقام و _>");
                return true;
            }
            editor.begin(player, id);
            return true;
        }

        if ("add".equals(action)) {
            editor.addCorner(player, player.getLocation());
            return true;
        }

        if ("undo".equals(action)) {
            editor.undo(player);
            return true;
        }

        if ("done".equals(action) || "finish".equals(action)) {
            editor.done(player);
            return true;
        }

        if ("confirm".equals(action)) {
            if (args.length < 3) {
                plugin.msg().send(sender, "general.usage", "usage", "/jobsadmin route confirm yes|no");
                return true;
            }
            String answer = args[2].toLowerCase();
            editor.confirm(player, "yes".equals(answer) || "y".equals(answer) || "نعم".equals(answer));
            return true;
        }

        if ("cancel".equals(action)) {
            editor.cancel(player);
            return true;
        }

        if ("wand".equals(action)) {
            editor.giveWand(player);
            return true;
        }

        if ("show".equals(action)) {
            if (args.length < 3) {
                plugin.msg().send(sender, "general.usage", "usage", "/jobsadmin route show <اسم>");
                return true;
            }
            Route route = plugin.routes().registry().get(args[2]);
            if (route == null) {
                plugin.msg().send(sender, "route.unknown");
                return true;
            }
            editor.preview(player, route);
            return true;
        }

        if ("del".equals(action) || "delete".equals(action) || "remove".equals(action)) {
            if (args.length < 3) {
                plugin.msg().send(sender, "general.usage", "usage", "/jobsadmin route del <اسم>");
                return true;
            }
            if (!plugin.routes().registry().remove(args[2])) {
                plugin.msg().send(sender, "route.unknown");
                return true;
            }
            plugin.routes().rebuild();
            plugin.msg().send(sender, "route.deleted", "route", args[2].toLowerCase());
            return true;
        }

        if ("set".equals(action)) {
            return routeSet(sender, args);
        }

        routeHelp(sender);
        return true;
    }

    private void routeList(CommandSender sender) {
        if (plugin.routes().registry().all().isEmpty()) {
            plugin.msg().send(sender, "route.list-none");
            return;
        }
        sender.sendMessage(plugin.msg().get("route.list-header",
                "count", plugin.routes().registry().size(),
                "active", plugin.routes().activeCount(),
                "max", plugin.settings().maxActiveWalkers));
        for (Route route : plugin.routes().registry().all()) {
            sender.sendMessage(plugin.msg().get("route.list-line",
                    "route", route.id(),
                    "mode", route.mode(),
                    "corners", route.cornerCount(),
                    "population", route.population(),
                    "active", plugin.routes().activeCount(route.id()),
                    "world", route.worldName()));
        }
    }

    /** Changes one property of a saved route without touching its corners. */
    private boolean routeSet(CommandSender sender, String[] args) {
        if (args.length < 5) {
            plugin.msg().send(sender, "general.usage", "usage", "/jobsadmin route set <اسم> <خاصية> <قيمة>");
            return true;
        }
        Route route = plugin.routes().registry().get(args[2]);
        if (route == null) {
            plugin.msg().send(sender, "route.unknown");
            return true;
        }

        String key = args[3].toLowerCase();
        // Names may contain spaces, so everything after the key is one value split on commas.
        StringBuilder joined = new StringBuilder();
        for (int i = 4; i < args.length; i++) {
            if (joined.length() > 0) {
                joined.append(' ');
            }
            joined.append(args[i]);
        }
        String value = joined.toString();

        try {
            if ("mode".equals(key)) {
                route.mode(value);
                value = route.mode();
            } else if ("speed".equals(key)) {
                route.speed(Double.parseDouble(value));
                value = String.valueOf(route.speed());
            } else if ("population".equals(key)) {
                route.population(Integer.parseInt(value));
                value = String.valueOf(route.population());
            } else if ("entity".equals(key)) {
                route.entityType(value);
                value = route.entityType();
            } else if ("persona".equals(key)) {
                route.persona("-".equals(value) ? "" : value);
                value = route.persona().isEmpty() ? "-" : route.persona();
            } else if ("names".equals(key)) {
                route.names(Arrays.asList(value.split(",")));
                value = route.names().toString();
            } else if ("activationrange".equals(key)) {
                route.activationRange(Double.parseDouble(value));
                value = String.valueOf(route.activationRange());
            } else if ("pausechance".equals(key)) {
                route.pauseChance(Double.parseDouble(value));
                value = String.valueOf(route.pauseChance());
            } else {
                plugin.msg().send(sender, "route.set-unknown");
                return true;
            }
        } catch (NumberFormatException ex) {
            plugin.msg().send(sender, "general.invalid-number");
            return true;
        }

        plugin.routes().registry().save();
        plugin.routes().rebuild();
        plugin.msg().send(sender, "route.set-done", "key", key, "value", value);
        return true;
    }

    private void routeHelp(CommandSender sender) {
        sender.sendMessage(Msg.color("&8&m---------------------------------"));
        sender.sendMessage(Msg.color("&b/jobsadmin route new <اسم> &8- &7ابدأ الرسم (ياخذ العصا)"));
        sender.sendMessage(Msg.color("&7  نقرة يمين = زاوية &8| &7نقرة يسار = تراجع"));
        sender.sendMessage(Msg.color("&b/jobsadmin route add &8- &7أضف موقعك كزاوية (مفيد وأنت طاير)"));
        sender.sendMessage(Msg.color("&b/jobsadmin route undo &8- &7تراجع عن آخر زاوية"));
        sender.sendMessage(Msg.color("&b/jobsadmin route done &8- &7إنهاء + سؤال نعم/لا"));
        sender.sendMessage(Msg.color("&b/jobsadmin route cancel &8- &7ألغِ الرسم"));
        sender.sendMessage(Msg.color("&b/jobsadmin route show <اسم> &8- &7اعرض مسار محفوظ"));
        sender.sendMessage(Msg.color("&b/jobsadmin route set <اسم> <خاصية> <قيمة>"));
        sender.sendMessage(Msg.color("&b/jobsadmin route list &8| &bdel <اسم>"));
        sender.sendMessage(Msg.color("&8&m---------------------------------"));
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
        List<String> missingPools = new ArrayList<String>();

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
        for (String pool : plugin.jobs().requiredSpotPools()) {
            if (!plugin.spots().registry().exists(pool)) {
                missingPools.add(pool);
            }
        }

        sender.sendMessage(Msg.color("&8&m---------------------------------"));
        if (missingIds.isEmpty() && missingGroups.isEmpty() && missingPools.isEmpty()) {
            sender.sendMessage(Msg.color("&aكل المناطق والنقاط المطلوبة موجودة. النظام جاهز للعب."));
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
            for (String pool : missingPools) {
                sender.sendMessage(Msg.color("&7- نقاط &f" + pool
                        + " &8➜ &7/jobsadmin spot new " + pool + " <TRASH|DIRT|PLANT|LAMP>"));
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
        if (job != null) {
            JobProgress progress = data.job(job.id());
            TrainingPlan plan = job.training();
            if (plan == null) {
                line(sender, "التدريب", "الوظيفة ما لها تدريب");
            } else if (progress == null) {
                line(sender, "التدريب", "-");
            } else if (progress.lesson() >= plan.size()) {
                line(sender, "التدريب", "مكتمل (" + plan.size() + "/" + plan.size() + ")");
            } else if (!progress.metTrainer()) {
                line(sender, "التدريب", "ما قابل المشرف بعد (" + plan.trainer() + ")");
            } else {
                line(sender, "التدريب", "الدرس " + (progress.lesson() + 1) + "/" + plan.size());
            }
        }
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
        sender.sendMessage(Msg.color("&b/jobsadmin route new <اسم> &8- &7ارسم مسار يمشون عليه"));
        sender.sendMessage(Msg.color("&b/jobsadmin route list &8- &7كل المسارات وحالتها"));
        sender.sendMessage(Msg.color("&b/jobsadmin spot new <اسم> <النوع> &8- &7حدد نقاط زبالة/وساخة/زرع/إنارة"));
        sender.sendMessage(Msg.color("&b/jobsadmin spot list &8- &7كل مجموعات النقاط وحالتها"));
        sender.sendMessage(Msg.color("&b/jobsadmin test &8- &7افحص كل شي وأعطني تقرير"));
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
            for (String sub : Arrays.asList("zone", "npc", "route", "spot", "test", "reload", "stats")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    out.add(sub);
                }
            }
            return out;
        }
        if ("route".equals(args[0].toLowerCase())) {
            return routeComplete(args);
        }
        if ("spot".equals(args[0].toLowerCase())) {
            return spotComplete(args);
        }
        if ("test".equals(args[0].toLowerCase())) {
            return testComplete(args);
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

    private List<String> testComplete(String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 2) {
            for (String sub : Arrays.asList("all", "config", "jobs", "zones", "spots", "routes",
                    "training", "npcs", "economy", "rotate", "kit", "tp", "help")) {
                if (sub.startsWith(args[1].toLowerCase())) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 3 && "rotate".equals(args[1].toLowerCase())) {
            out.add("all");
            for (SpotPool pool : plugin.spots().registry().all()) {
                if (pool.id().startsWith(args[2].toLowerCase())) {
                    out.add(pool.id());
                }
            }
            return out;
        }
        if (args.length == 3 && "tp".equals(args[1].toLowerCase())) {
            for (Zone zone : plugin.zones().all()) {
                if (zone.id().startsWith(args[2].toLowerCase())) {
                    out.add(zone.id());
                }
            }
        }
        return out;
    }

    private List<String> spotComplete(String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 2) {
            for (String sub : Arrays.asList("new", "edit", "add", "undo", "done", "confirm",
                    "cancel", "wand", "show", "set", "list", "del")) {
                if (sub.startsWith(args[1].toLowerCase())) {
                    out.add(sub);
                }
            }
            return out;
        }

        String action = args[1].toLowerCase();
        if (args.length == 3) {
            if ("confirm".equals(action)) {
                for (String answer : Arrays.asList("yes", "no")) {
                    if (answer.startsWith(args[2].toLowerCase())) {
                        out.add(answer);
                    }
                }
                return out;
            }
            if ("edit".equals(action) || "show".equals(action) || "set".equals(action)
                    || "del".equals(action)) {
                for (SpotPool pool : plugin.spots().registry().all()) {
                    if (pool.id().startsWith(args[2].toLowerCase())) {
                        out.add(pool.id());
                    }
                }
            }
            return out;
        }

        if (args.length == 4 && ("new".equals(action) || "edit".equals(action))) {
            for (SpotType type : SpotType.values()) {
                if (type.name().startsWith(args[3].toUpperCase())) {
                    out.add(type.name());
                }
            }
            return out;
        }

        if (args.length == 4 && "set".equals(action)) {
            for (String key : Arrays.asList("activeCount", "respawnMinutes", "activationRange",
                    "type", "swapBlock")) {
                if (key.toLowerCase().startsWith(args[3].toLowerCase())) {
                    out.add(key);
                }
            }
            return out;
        }

        if (args.length == 5 && "set".equals(action)) {
            String key = args[3].toLowerCase();
            if ("type".equals(key)) {
                for (SpotType type : SpotType.values()) {
                    out.add(type.name());
                }
            } else if ("swapblock".equals(key)) {
                out.addAll(Arrays.asList("true", "false"));
            }
        }
        return out;
    }

    private List<String> routeComplete(String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 2) {
            for (String sub : Arrays.asList("new", "edit", "add", "undo", "done", "confirm",
                    "cancel", "wand", "show", "set", "list", "del")) {
                if (sub.startsWith(args[1].toLowerCase())) {
                    out.add(sub);
                }
            }
            return out;
        }

        String action = args[1].toLowerCase();
        if (args.length == 3) {
            if ("confirm".equals(action)) {
                for (String answer : Arrays.asList("yes", "no")) {
                    if (answer.startsWith(args[2].toLowerCase())) {
                        out.add(answer);
                    }
                }
                return out;
            }
            if ("edit".equals(action) || "show".equals(action) || "set".equals(action)
                    || "del".equals(action)) {
                for (Route route : plugin.routes().registry().all()) {
                    if (route.id().startsWith(args[2].toLowerCase())) {
                        out.add(route.id());
                    }
                }
            }
            return out;
        }

        if (args.length == 4 && "set".equals(action)) {
            for (String key : Arrays.asList("mode", "speed", "population", "entity", "persona",
                    "names", "activationRange", "pauseChance")) {
                if (key.toLowerCase().startsWith(args[3].toLowerCase())) {
                    out.add(key);
                }
            }
            return out;
        }

        if (args.length == 5 && "set".equals(action)) {
            String key = args[3].toLowerCase();
            if ("mode".equals(key)) {
                out.addAll(Arrays.asList("LOOP", "PINGPONG"));
            } else if ("persona".equals(key)) {
                out.add("-");
                for (NpcPersona persona : plugin.npcs().all()) {
                    out.add(persona.id());
                }
            } else if ("entity".equals(key)) {
                out.addAll(Arrays.asList("VILLAGER", "ZOMBIE", "SKELETON", "PIG_ZOMBIE"));
            }
        }
        return out;
    }
}
