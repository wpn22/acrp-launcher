package com.adventurecity.jobs.job;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.config.JobDefinition;
import com.adventurecity.jobs.config.JobGrade;
import com.adventurecity.jobs.economy.Payout;
import com.adventurecity.jobs.storage.PlayerData;
import com.adventurecity.jobs.storage.TxType;
import com.adventurecity.jobs.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Runs once a minute and pays the duty salary when a player has accumulated their job's interval.
 * AFK minutes are not counted at all, so standing still simply never earns.
 */
public final class PayrollTask extends BukkitRunnable {

    private final ACRPJobsPlugin plugin;
    private final Set<UUID> afkWarned = new HashSet<UUID>();

    public PayrollTask(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.settings().payrollEnabled) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.players().get(player);
            if (data == null || data.currentJob() == null) {
                continue;
            }
            JobDefinition job = plugin.jobs().get(data.currentJob());
            if (job == null) {
                continue;
            }
            if (job.payrollRequiresDuty() && !data.onDuty()) {
                continue;
            }

            if (plugin.activity().isAfk(player, plugin.settings().afkSeconds)) {
                if (afkWarned.add(player.getUniqueId())) {
                    plugin.msg().send(player, "duty.salary-afk");
                }
                continue;
            }
            afkWarned.remove(player.getUniqueId());

            data.dutyMinutes(data.dutyMinutes() + 1);
            if (data.dutyMinutes() < job.payrollIntervalMinutes()) {
                continue;
            }
            data.dutyMinutes(0);

            JobGrade grade = plugin.jobManager().gradeOf(data, job);
            if (grade.salary() <= 0L) {
                continue;
            }
            Payout payout = plugin.payouts().pay(data, grade.salary(), job.payrollTaxPercent(),
                    TxType.SALARY, "راتب " + job.id() + " (" + grade.name() + ")");

            if (payout.paid()) {
                plugin.msg().send(player, "duty.salary-paid",
                        "amount", plugin.economy().format(payout.net),
                        "job", Msg.color(job.name()),
                        "tax", plugin.economy().format(payout.tax));
            } else if (payout.capReached) {
                plugin.msg().send(player, "contract.daily-cap");
            }
        }
    }

    public void forget(UUID uuid) {
        afkWarned.remove(uuid);
    }
}
