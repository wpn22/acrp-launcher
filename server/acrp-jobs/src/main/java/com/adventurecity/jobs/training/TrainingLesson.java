package com.adventurecity.jobs.training;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One thing the supervisor explains, and the thing the worker has to do before the next one. */
public final class TrainingLesson {

    private final List<String> text;
    private final TrainingTrigger trigger;
    private final String hint;

    private TrainingLesson(List<String> text, TrainingTrigger trigger, String hint) {
        this.text = Collections.unmodifiableList(text);
        this.trigger = trigger;
        this.hint = hint;
    }

    /** Returns null (and logs nothing) for a lesson with no words - there is nothing to say. */
    public static TrainingLesson parse(ConfigurationSection section) {
        List<String> text = new ArrayList<String>(section.getStringList("text"));
        if (text.isEmpty()) {
            String single = section.getString("text");
            if (single != null && !single.isEmpty()) {
                text.add(single);
            }
        }
        if (text.isEmpty()) {
            return null;
        }
        TrainingTrigger trigger = TrainingTrigger.fromString(section.getString("trigger"));
        return new TrainingLesson(text, trigger == null ? TrainingTrigger.TALK : trigger,
                section.getString("hint", ""));
    }

    public List<String> text() {
        return text;
    }

    public TrainingTrigger trigger() {
        return trigger;
    }

    /** The short "do this now" line shown under the lesson. Empty to show nothing. */
    public String hint() {
        return hint;
    }
}
