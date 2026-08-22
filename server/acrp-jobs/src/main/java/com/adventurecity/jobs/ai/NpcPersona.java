package com.adventurecity.jobs.ai;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** One NPC as defined in npcs.yml: who they are, and the narrow set of things they may offer. */
public final class NpcPersona {

    private static final Random RANDOM = new Random();

    private final String id;
    private final String name;
    private String entityName;
    private final String persona;
    private final String greeting;
    private final List<String> intents;
    private final List<String> jobs;
    private final List<String> zones;
    private final List<String> fallbackLines;

    private NpcPersona(String id, String name, String entityName, String persona, String greeting,
                       List<String> intents, List<String> jobs, List<String> zones,
                       List<String> fallbackLines) {
        this.id = id;
        this.name = name;
        this.entityName = entityName;
        this.persona = persona;
        this.greeting = greeting;
        this.intents = Collections.unmodifiableList(intents);
        this.jobs = Collections.unmodifiableList(jobs);
        this.zones = Collections.unmodifiableList(zones);
        this.fallbackLines = Collections.unmodifiableList(fallbackLines);
    }

    public static NpcPersona parse(String id, ConfigurationSection section) {
        List<String> intents = new ArrayList<String>();
        for (String intent : section.getStringList("intents")) {
            intents.add(intent.trim().toUpperCase());
        }
        if (intents.isEmpty()) {
            intents.add(DialogueIntent.CHAT.name());
        }
        if (!intents.contains(DialogueIntent.CHAT.name())) {
            intents.add(DialogueIntent.CHAT.name());
        }

        List<String> jobs = new ArrayList<String>();
        for (String job : section.getStringList("jobs")) {
            jobs.add(job.trim().toLowerCase());
        }
        List<String> zones = new ArrayList<String>();
        for (String zone : section.getStringList("zones")) {
            zones.add(zone.trim().toLowerCase());
        }

        return new NpcPersona(
                id.toLowerCase(),
                section.getString("name", id),
                section.getString("entityName", ""),
                section.getString("persona", ""),
                section.getString("greeting", ""),
                intents,
                jobs,
                zones,
                section.getStringList("fallback"));
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** In-game entity name used to match a right-click. Set by /jobsadmin npc link. */
    public String entityName() {
        return entityName;
    }

    public void entityName(String entityName) {
        this.entityName = entityName == null ? "" : entityName;
    }

    public String persona() {
        return persona;
    }

    public String greeting() {
        return greeting;
    }

    public List<String> intents() {
        return intents;
    }

    public List<String> jobs() {
        return jobs;
    }

    public List<String> zones() {
        return zones;
    }

    public boolean allows(DialogueIntent intent) {
        return intents.contains(intent.name());
    }

    /** Written line used whenever the AI is off, throttled, over budget, or unreachable. */
    public String fallbackLine() {
        if (fallbackLines.isEmpty()) {
            return "";
        }
        return fallbackLines.get(RANDOM.nextInt(fallbackLines.size()));
    }

    public boolean hasFallback() {
        return !fallbackLines.isEmpty();
    }
}
