package com.adventurecity.jobs.training;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.ai.NpcPersona;
import com.adventurecity.jobs.config.JobDefinition;
import com.adventurecity.jobs.storage.JobProgress;
import com.adventurecity.jobs.storage.PlayerData;
import com.adventurecity.jobs.util.Msg;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Field training: a real city worker teaches the job, on the job.
 *
 * <p>The first lesson has to be heard face to face - the worker walks their round and the newcomer
 * has to go and find them. After that the supervisor keeps in touch by radio while the newcomer
 * actually works, because making somebody walk back across town between every sentence is not
 * realism, it is tedium.</p>
 *
 * <p>Lessons never advance on a click. Each one waits for the real action it describes, so the
 * explanation and the work stay welded together.</p>
 */
public final class TrainingService {

    private final ACRPJobsPlugin plugin;

    public TrainingService(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- lookups

    /** The job this persona trains, or null when they are an ordinary citizen. */
    public JobDefinition jobOf(NpcPersona persona) {
        if (persona == null) {
            return null;
        }
        for (JobDefinition job : plugin.jobs().all()) {
            TrainingPlan plan = job.training();
            if (plan != null && plan.trainer().equals(persona.id())) {
                return job;
            }
        }
        return null;
    }

    public boolean isTrainer(NpcPersona persona) {
        return jobOf(persona) != null;
    }

    private JobProgress progressFor(Player player, JobDefinition job) {
        PlayerData data = plugin.players().get(player);
        return data == null ? null : data.job(job.id());
    }

    // ---------------------------------------------------------------- hiring

    /** Called when a player joins a job: tells them who to go and find. */
    public void onHired(Player player, JobDefinition job) {
        TrainingPlan plan = job.training();
        if (plan == null) {
            return;
        }
        NpcPersona trainer = plugin.npcs().get(plan.trainer());
        if (trainer == null) {
            return;
        }
        if (!plan.intro().isEmpty()) {
            player.sendMessage(Msg.color(plan.intro()));
        }
        plugin.msg().send(player, "training.go-find", "trainer", trainer.name());
    }

    // ---------------------------------------------------------------- meeting the trainer

    /**
     * Right-clicking a supervisor. Delivers the lesson they are on, or turns them back into an
     * ordinary conversation once there is nothing left to teach.
     *
     * @return true when training handled the click, false to fall through to normal dialogue
     */
    public boolean meet(Player player, NpcPersona persona) {
        JobDefinition job = jobOf(persona);
        if (job == null) {
            return false;
        }
        TrainingPlan plan = job.training();

        JobProgress progress = progressFor(player, job);
        if (progress == null) {
            // Not hired yet - the supervisor points them at the job centre instead of teaching.
            plugin.msg().send(player, "training.not-hired",
                    "trainer", persona.name(), "job", Msg.color(job.name()));
            return true;
        }

        int index = progress.lesson();
        if (index >= plan.size()) {
            plugin.msg().send(player, "training.finished", "trainer", persona.name());
            return false;
        }

        deliver(player, persona, job, plan.lesson(index), index);
        return true;
    }

    // ---------------------------------------------------------------- progress

    /** Every hook in the plugin funnels into here. Advances only if this is what was being waited for. */
    public void onAction(Player player, TrainingTrigger trigger) {
        PlayerData data = plugin.players().get(player);
        if (data == null || data.currentJob() == null) {
            return;
        }
        JobDefinition job = plugin.jobs().get(data.currentJob());
        if (job == null || job.training() == null) {
            return;
        }
        TrainingPlan plan = job.training();
        JobProgress progress = data.job(job.id());
        if (progress == null) {
            return;
        }

        int index = progress.lesson();
        TrainingLesson current = plan.lesson(index);
        // Nothing outstanding, or this action is not the one being waited for.
        if (current == null || current.trigger() != trigger) {
            return;
        }
        // The very first lesson is only ever delivered face to face, so it cannot be skipped past
        // by clocking in before meeting anybody.
        if (index == 0 && !progress.metTrainer()) {
            return;
        }

        progress.lesson(index + 1);
        data.dirty(true);

        NpcPersona trainer = plugin.npcs().get(plan.trainer());
        String name = trainer == null ? plan.trainer() : trainer.name();
        plugin.msg().send(player, "training.step-done", "trainer", name);

        TrainingLesson next = plan.lesson(index + 1);
        if (next == null) {
            plugin.msg().send(player, "training.completed", "job", Msg.color(job.name()));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
            return;
        }
        deliver(player, trainer, job, next, index + 1);
    }

    // ---------------------------------------------------------------- delivery

    private void deliver(Player player, NpcPersona trainer, JobDefinition job,
                         TrainingLesson lesson, int index) {
        TrainingPlan plan = job.training();
        String name = trainer == null ? plan.trainer() : trainer.name();

        JobProgress progress = progressFor(player, job);
        if (progress != null && !progress.metTrainer()) {
            progress.metTrainer(true);
            PlayerData data = plugin.players().get(player);
            if (data != null) {
                data.dirty(true);
            }
        }

        plugin.msg().send(player, "training.header",
                "trainer", name, "step", index + 1, "total", plan.size());
        for (String line : lesson.text()) {
            player.sendMessage(plugin.msg().get("training.line", "trainer", name, "text", Msg.color(line)));
        }
        if (!lesson.hint().isEmpty()) {
            plugin.msg().send(player, "training.hint", "hint", Msg.color(lesson.hint()));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.7F, 1.1F);
    }

    /** /jobs learn - hear the current lesson again without hunting the supervisor down. */
    public void repeat(Player player) {
        PlayerData data = plugin.players().get(player);
        JobDefinition job = data == null ? null : plugin.jobs().get(data.currentJob());
        if (job == null) {
            plugin.msg().send(player, "jobs.no-job");
            return;
        }
        TrainingPlan plan = job.training();
        JobProgress progress = data.job(job.id());
        if (plan == null || progress == null) {
            plugin.msg().send(player, "training.none");
            return;
        }
        TrainingLesson lesson = plan.lesson(progress.lesson());
        if (lesson == null) {
            plugin.msg().send(player, "training.all-done", "job", Msg.color(job.name()));
            return;
        }
        if (!progress.metTrainer()) {
            NpcPersona trainer = plugin.npcs().get(plan.trainer());
            plugin.msg().send(player, "training.go-find",
                    "trainer", trainer == null ? plan.trainer() : trainer.name());
            return;
        }
        deliver(player, plugin.npcs().get(plan.trainer()), job, lesson, progress.lesson());
    }
}
