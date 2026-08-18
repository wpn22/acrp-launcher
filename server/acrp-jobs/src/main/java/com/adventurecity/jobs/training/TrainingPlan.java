package com.adventurecity.jobs.training;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * How a job teaches itself: which city worker does the teaching, and what they say.
 *
 * <p>Written in the job's own YAML file, so a new job arrives with its own training and no code -
 * the same rule the rest of the system runs on.</p>
 */
public final class TrainingPlan {

    private final String trainer;
    private final String intro;
    private final List<TrainingLesson> lessons;

    private TrainingPlan(String trainer, String intro, List<TrainingLesson> lessons) {
        this.trainer = trainer;
        this.intro = intro;
        this.lessons = Collections.unmodifiableList(lessons);
    }

    /** Returns null when the job has no training block, which is a perfectly valid job. */
    public static TrainingPlan parse(ConfigurationSection section, String jobId, Logger logger) {
        if (section == null) {
            return null;
        }
        String trainer = section.getString("trainer", "").trim().toLowerCase();
        if (trainer.isEmpty()) {
            logger.warning("[ACRPJobs] " + jobId + ": training has no 'trainer' - training disabled.");
            return null;
        }

        List<TrainingLesson> lessons = new ArrayList<TrainingLesson>();
        for (Map<?, ?> raw : section.getMapList("lessons")) {
            TrainingLesson lesson = TrainingLesson.parse(toSection(raw));
            if (lesson != null) {
                lessons.add(lesson);
            }
        }
        if (lessons.isEmpty()) {
            logger.warning("[ACRPJobs] " + jobId + ": training has no lessons - training disabled.");
            return null;
        }
        return new TrainingPlan(trainer, section.getString("intro", ""), lessons);
    }

    /**
     * Lessons are a YAML list of maps and Bukkit hands those back as plain Maps, so each one is
     * wrapped in a detached configuration - the same trick ContractDefinition uses for steps.
     */
    private static ConfigurationSection toSection(Map<?, ?> raw) {
        MemoryConfiguration section = new MemoryConfiguration();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            section.set(String.valueOf(entry.getKey()), entry.getValue());
        }
        return section;
    }

    /** The npcs.yml persona who teaches this job. */
    public String trainer() {
        return trainer;
    }

    /** Shown when the player is hired, telling them who to look for. */
    public String intro() {
        return intro;
    }

    public List<TrainingLesson> lessons() {
        return lessons;
    }

    public int size() {
        return lessons.size();
    }

    public TrainingLesson lesson(int index) {
        if (index < 0 || index >= lessons.size()) {
            return null;
        }
        return lessons.get(index);
    }
}
