package com.adventurecity.jobs.command;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.config.JobDefinition;
import com.adventurecity.jobs.config.JobGrade;
import com.adventurecity.jobs.contract.ActiveContract;
import com.adventurecity.jobs.storage.JobProgress;
import com.adventurecity.jobs.storage.PlayerData;
import com.adventurecity.jobs.storage.PlayerDataManager;
import com.adventurecity.jobs.ui.ContractMenu;
import com.adventurecity.jobs.ui.JobCenterMenu;
import com.adventurecity.jobs.ui.MyJobMenu;
import com.adventurecity.jobs.util.Msg;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** /jobs - the job center and everything a working player needs. */
public final class JobsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "join", "leave", "quit", "confirm", "cancel", "info", "contracts", "help");

    private final ACRPJobsPlugin plugin;

    public JobsCommand(ACRPJobsPlugin plugin) {
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

        if (args.length == 0) {
            new JobCenterMenu(plugin).open(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        if ("join".equals(sub)) {
            if (args.length < 2) {
                plugin.msg().send(player, "general.usage", "usage", "/jobs join <وظيفة>");
                return true;
            }
            JobDefinition job = plugin.jobs().get(args[1]);
            if (job == null) {
                plugin.msg().send(player, "jobs.unknown");
                return true;
            }
            plugin.jobManager().join(player, job);
            return true;
        }
        if ("leave".equals(sub)) {
            plugin.jobManager().leave(player);
            return true;
        }
        if ("quit".equals(sub)) {
            if (args.length < 2) {
                plugin.msg().send(player, "general.usage", "usage", "/jobs quit <وظيفة>");
                return true;
            }
            plugin.jobManager().quit(player, args[1].toLowerCase());
            return true;
        }
        if ("confirm".equals(sub)) {
            plugin.contracts().confirm(player);
            return true;
        }
        if ("cancel".equals(sub)) {
            plugin.contracts().cancel(player, true);
            return true;
        }
        if ("contracts".equals(sub) || "tasks".equals(sub)) {
            new ContractMenu(plugin).open(player);
            return true;
        }
        if ("info".equals(sub)) {
            sendInfo(player);
            return true;
        }
        if ("me".equals(sub) || "my".equals(sub)) {
            new MyJobMenu(plugin).open(player);
            return true;
        }
        sendHelp(player);
        return true;
    }

    private void sendInfo(Player player) {
        PlayerData data = plugin.players().get(player);
        JobDefinition job = data == null ? null : plugin.jobs().get(data.currentJob());
        if (job == null) {
            plugin.msg().send(player, "jobs.no-job");
            return;
        }
        JobGrade grade = plugin.jobManager().gradeOf(data, job);
        JobProgress progress = data.job(job.id());
        JobGrade next = job.nextGrade(grade);

        player.sendMessage(Msg.color("&8&m---------------------------------"));
        player.sendMessage(Msg.color("&b وظيفتك: &f" + job.name()));
        player.sendMessage(Msg.color("&b الرتبة: &f" + grade.name()
                + " &7(راتب " + plugin.economy().format(grade.salary())
                + " كل " + job.payrollIntervalMinutes() + " دقيقة)"));
        if (next != null && progress != null) {
            player.sendMessage(Msg.color("&b الخبرة: &f" + Msg.number(progress.xp())
                    + "&7/&f" + Msg.number(next.xpRequired()) + " &7➜ " + next.name()));
        }
        player.sendMessage(Msg.color("&b الدوام: " + (data.onDuty() ? "&aعلى الدوام" : "&cخارج الدوام")));
        player.sendMessage(Msg.color("&b أرباح اليوم: &a"
                + plugin.economy().format(data.earnedToday(PlayerDataManager.epochDay()))));

        ActiveContract contract = plugin.contracts().get(player);
        if (contract != null) {
            player.sendMessage(Msg.color("&b المهمة: &f" + contract.definition().name()
                    + " &7(" + (contract.index() + 1) + "/" + contract.stepCount() + ")"));
            player.sendMessage(Msg.color("&b الخطوة: &e" + contract.step().title()));
        }
        player.sendMessage(Msg.color("&8&m---------------------------------"));
    }

    private void sendHelp(Player player) {
        player.sendMessage(Msg.color("&8&m---------------------------------"));
        player.sendMessage(Msg.color("&b/jobs &8- &7مركز الوظائف"));
        player.sendMessage(Msg.color("&b/jobs info &8- &7معلومات وظيفتك"));
        player.sendMessage(Msg.color("&b/jobs contracts &8- &7المهام المتاحة"));
        player.sendMessage(Msg.color("&b/jobs confirm &8- &7تأكيد خطوة المهمة"));
        player.sendMessage(Msg.color("&b/jobs cancel &8- &7إلغاء المهمة"));
        player.sendMessage(Msg.color("&b/duty &8- &7بدء أو إنهاء الدوام"));
        player.sendMessage(Msg.color("&b/ac &8- &7رصيدك"));
        player.sendMessage(Msg.color("&8&m---------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 2 && ("join".equals(args[0].toLowerCase()) || "quit".equals(args[0].toLowerCase()))) {
            for (JobDefinition job : plugin.jobs().all()) {
                if (job.id().startsWith(args[1].toLowerCase())) {
                    out.add(job.id());
                }
            }
        }
        return out;
    }

    /** Shared by several commands: strips colour so console output stays readable. */
    static String plain(String input) {
        return ChatColor.stripColor(Msg.color(input));
    }
}
