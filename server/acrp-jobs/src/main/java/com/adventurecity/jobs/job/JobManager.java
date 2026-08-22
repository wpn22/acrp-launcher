package com.adventurecity.jobs.job;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.config.JobDefinition;
import com.adventurecity.jobs.config.JobGrade;
import com.adventurecity.jobs.storage.JobProgress;
import com.adventurecity.jobs.storage.PlayerData;
import com.adventurecity.jobs.util.Msg;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Hiring, resigning, ranks and experience. */
public final class JobManager {

    private final ACRPJobsPlugin plugin;

    public JobManager(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Hires the player if needed and makes the job their active one. */
    public boolean join(Player player, JobDefinition job) {
        PlayerData data = plugin.players().get(player);
        if (data == null) {
            return false;
        }
        if (job.whitelist()
                && !player.hasPermission(job.permissionNode())
                && !player.hasPermission("acrp.jobs.job.*")
                && !player.hasPermission("acrp.jobs.admin")) {
            plugin.msg().send(player, "jobs.whitelist");
            return false;
        }
        if (job.id().equals(data.currentJob())) {
            plugin.msg().send(player, "jobs.already");
            return false;
        }

        boolean alreadyHired = data.hasJob(job.id());
        if (!alreadyHired && data.jobCount() >= plugin.settings().maxJobsPerPlayer) {
            plugin.msg().send(player, "jobs.max-jobs", "max", plugin.settings().maxJobsPerPlayer);
            return false;
        }

        // Switching jobs always ends the current shift - you cannot be on duty as two things at once.
        if (data.onDuty()) {
            plugin.duty().stop(player, false);
        }
        plugin.contracts().cancel(player, false);

        if (!alreadyHired) {
            data.addJob(new JobProgress(job.id(), 1, 0L, System.currentTimeMillis()));
        }
        data.currentJob(job.id());

        JobGrade grade = gradeOf(data, job);
        if (alreadyHired) {
            plugin.msg().send(player, "jobs.switched", "job", Msg.color(job.name()));
        } else {
            plugin.msg().send(player, "jobs.joined", "job", Msg.color(job.name()), "grade", grade.name());
            plugin.training().onHired(player, job);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
        return true;
    }

    /** Stops working the current job but keeps the employment record (rank and experience survive). */
    public void leave(Player player) {
        PlayerData data = plugin.players().get(player);
        if (data == null || data.currentJob() == null) {
            plugin.msg().send(player, "jobs.no-job");
            return;
        }
        JobDefinition job = plugin.jobs().get(data.currentJob());
        if (data.onDuty()) {
            plugin.duty().stop(player, false);
        }
        plugin.contracts().cancel(player, false);
        data.currentJob(null);
        plugin.msg().send(player, "jobs.left", "job", job == null ? "-" : Msg.color(job.name()));
    }

    /** Deletes the employment record entirely - rank and experience are lost. */
    public boolean quit(Player player, String jobId) {
        PlayerData data = plugin.players().get(player);
        if (data == null || !data.hasJob(jobId)) {
            plugin.msg().send(player, "jobs.not-hired");
            return false;
        }
        JobDefinition job = plugin.jobs().get(jobId);
        if (jobId.equals(data.currentJob())) {
            if (data.onDuty()) {
                plugin.duty().stop(player, false);
            }
            plugin.contracts().cancel(player, false);
            data.currentJob(null);
        }
        data.removeJob(jobId);
        plugin.players().deleteJobAsync(player.getUniqueId(), jobId);
        plugin.msg().send(player, "jobs.quit", "job", job == null ? jobId : Msg.color(job.name()));
        return true;
    }

    /** Rank is always derived from experience, so a config change re-ranks everyone automatically. */
    public JobGrade gradeOf(PlayerData data, JobDefinition job) {
        JobProgress progress = data.job(job.id());
        if (progress == null) {
            return job.grades().get(0);
        }
        JobGrade grade = job.gradeForXp(progress.xp());
        if (progress.gradeLevel() != grade.level()) {
            progress.gradeLevel(grade.level());
            data.dirty(true);
        }
        return grade;
    }

    public void addXp(Player player, JobDefinition job, int amount) {
        if (amount <= 0) {
            return;
        }
        PlayerData data = plugin.players().get(player);
        if (data == null) {
            return;
        }
        JobProgress progress = data.job(job.id());
        if (progress == null) {
            return;
        }

        JobGrade before = job.gradeForXp(progress.xp());
        progress.addXp(amount);
        data.dirty(true);
        JobGrade after = job.gradeForXp(progress.xp());

        if (after.level() > before.level()) {
            progress.gradeLevel(after.level());
            plugin.msg().send(player, "jobs.promoted", "grade", after.name(), "job", Msg.color(job.name()));
            plugin.msg().send(player, "jobs.promoted-detail",
                    "salary", plugin.economy().format(after.salary()),
                    "minutes", job.payrollIntervalMinutes());
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
            player.sendTitle(ChatColor.GOLD + "ترقية!", ChatColor.YELLOW + after.name(), 10, 50, 10);
            return;
        }

        JobGrade next = job.nextGrade(after);
        if (next == null) {
            plugin.msg().send(player, "jobs.xp-max", "xp", amount);
        } else {
            plugin.msg().send(player, "jobs.xp-gained",
                    "xp", amount,
                    "current", Msg.number(progress.xp()),
                    "needed", Msg.number(next.xpRequired()));
        }
    }

    /** The job the player is actively working, or null. */
    public JobDefinition currentJob(Player player) {
        PlayerData data = plugin.players().get(player);
        if (data == null || data.currentJob() == null) {
            return null;
        }
        return plugin.jobs().get(data.currentJob());
    }
}
