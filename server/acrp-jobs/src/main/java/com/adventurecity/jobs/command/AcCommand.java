package com.adventurecity.jobs.command;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.storage.TxType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /ac - balance, player transfers and the admin/console operations.
 *
 * <p>{@code /ac give <player> <amount> store} is the hook the web store calls, so store purchases
 * and job earnings land in the same wallet and the same audit log.</p>
 */
public final class AcCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ADMIN_SUBS = Arrays.asList("pay", "give", "take", "set", "top");

    private final ACRPJobsPlugin plugin;

    public AcCommand(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                plugin.msg().send(sender, "general.player-only");
                return true;
            }
            Player player = (Player) sender;
            plugin.msg().send(player, "economy.balance",
                    "amount", plugin.economy().format(plugin.economy().balance(player.getUniqueId())));
            return true;
        }

        String sub = args[0].toLowerCase();

        if ("top".equals(sub)) {
            sendTop(sender);
            return true;
        }

        if ("pay".equals(sub)) {
            return pay(sender, args);
        }

        boolean admin = "give".equals(sub) || "take".equals(sub) || "set".equals(sub);
        if (admin) {
            if (!sender.hasPermission("acrp.jobs.admin")) {
                plugin.msg().send(sender, "general.no-permission");
                return true;
            }
            if (args.length < 3) {
                plugin.msg().send(sender, "general.usage", "usage", "/ac " + sub + " <لاعب> <مبلغ> [سبب]");
                return true;
            }
            long amount = parseAmount(sender, args[2]);
            if (amount < 0L) {
                return true;
            }
            String reason = args.length > 3 ? join(args, 3) : ("admin:" + sender.getName());
            adminAdjust(sender, sub, args[1], amount, reason);
            return true;
        }

        // /ac <player> - look up someone else's balance
        if (!sender.hasPermission("acrp.jobs.admin")) {
            plugin.msg().send(sender, "general.no-permission");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.msg().send(sender, "general.player-not-found", "player", args[0]);
            return true;
        }
        plugin.msg().send(sender, "economy.balance-other",
                "player", target.getName(),
                "amount", plugin.economy().format(plugin.economy().balance(target.getUniqueId())));
        return true;
    }

    private boolean pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.msg().send(sender, "general.player-only");
            return true;
        }
        Player player = (Player) sender;
        if (!plugin.settings().allowPlayerTransfer) {
            plugin.msg().send(player, "economy.transfer-disabled");
            return true;
        }
        if (args.length < 3) {
            plugin.msg().send(player, "general.usage", "usage", "/ac pay <لاعب> <مبلغ>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.msg().send(player, "general.player-not-found", "player", args[1]);
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.msg().send(player, "economy.transfer-self");
            return true;
        }
        long amount = parseAmount(player, args[2]);
        if (amount <= 0L) {
            return true;
        }
        if (amount < plugin.settings().minTransfer) {
            plugin.msg().send(player, "economy.transfer-min",
                    "amount", plugin.economy().format(plugin.settings().minTransfer));
            return true;
        }
        if (!plugin.economy().withdraw(player.getUniqueId(), amount, TxType.TRANSFER_OUT,
                "تحويل إلى " + target.getName())) {
            plugin.msg().send(player, "economy.not-enough", "amount", plugin.economy().format(amount));
            return true;
        }

        long tax = Math.round(amount * (plugin.settings().transferTaxPercent / 100.0D));
        long received = Math.max(0L, amount - tax);
        plugin.economy().deposit(target.getUniqueId(), received, TxType.TRANSFER_IN,
                "تحويل من " + player.getName());

        plugin.msg().send(player, "economy.transfer-sent",
                "amount", plugin.economy().format(received),
                "player", target.getName(),
                "tax", plugin.economy().format(tax));
        plugin.msg().send(target, "economy.transfer-received",
                "amount", plugin.economy().format(received),
                "player", player.getName());
        return true;
    }

    /** Works for offline players by resolving the uuid from our own player table. */
    private void adminAdjust(final CommandSender sender, final String action, final String name,
                             final long amount, final String reason) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            apply(sender, action, online.getUniqueId(), online.getName(), amount, reason);
            return;
        }
        plugin.players().query(() -> plugin.players().storage().findUuidByName(name), new Consumer<UUID>() {
            @Override
            public void accept(UUID uuid) {
                if (uuid == null) {
                    plugin.msg().send(sender, "general.player-not-found", "player", name);
                    return;
                }
                apply(sender, action, uuid, name, amount, reason);
            }
        });
    }

    private void apply(final CommandSender sender, String action, UUID uuid, final String name,
                       final long amount, String reason) {
        final String type;
        long value;
        boolean absolute = false;
        if ("give".equals(action)) {
            type = TxType.ADMIN_GIVE;
            value = amount;
        } else if ("take".equals(action)) {
            type = TxType.ADMIN_TAKE;
            value = -amount;
        } else {
            type = TxType.ADMIN_SET;
            value = amount;
            absolute = true;
        }

        final String messageKey = "give".equals(action) ? "economy.admin-give"
                : "take".equals(action) ? "economy.admin-take" : "economy.admin-set";

        plugin.economy().adjustOffline(uuid, name, value, absolute, type, reason, balance -> {
            if (balance == null || balance.longValue() < 0L) {
                plugin.msg().send(sender, "general.player-not-found", "player", name);
                return;
            }
            plugin.msg().send(sender, messageKey,
                    "player", name,
                    "amount", plugin.economy().format(
                            "economy.admin-set".equals(messageKey) ? balance.longValue() : amount));
            Player target = Bukkit.getPlayerExact(name);
            if (target != null && !TxType.ADMIN_TAKE.equals(type)) {
                plugin.msg().send(target, "economy.admin-received",
                        "amount", plugin.economy().format(
                                TxType.ADMIN_SET.equals(type) ? balance.longValue() : amount));
            }
        });
    }

    private void sendTop(final CommandSender sender) {
        plugin.players().query(() -> plugin.players().storage().topBalances(10), rows -> {
            if (rows == null) {
                return;
            }
            sender.sendMessage(plugin.msg().get("economy.top-header"));
            int rank = 1;
            for (Object[] row : rows) {
                sender.sendMessage(plugin.msg().get("economy.top-line",
                        "rank", rank,
                        "player", String.valueOf(row[0]),
                        "amount", plugin.economy().format(((Long) row[1]).longValue())));
                rank++;
            }
        });
    }

    private long parseAmount(CommandSender sender, String raw) {
        try {
            long value = Long.parseLong(raw.replace(",", "").trim());
            if (value < 0L) {
                plugin.msg().send(sender, "general.invalid-number");
                return -1L;
            }
            return value;
        } catch (NumberFormatException ex) {
            plugin.msg().send(sender, "general.invalid-number");
            return -1L;
        }
    }

    private static String join(String[] args, int from) {
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            for (String sub : ADMIN_SUBS) {
                if (sub.startsWith(args[0].toLowerCase())
                        && ("pay".equals(sub) || "top".equals(sub) || sender.hasPermission("acrp.jobs.admin"))) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 2) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    out.add(player.getName());
                }
            }
        }
        return out;
    }
}
