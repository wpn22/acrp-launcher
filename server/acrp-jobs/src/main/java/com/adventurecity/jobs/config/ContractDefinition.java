package com.adventurecity.jobs.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** A repeatable mission belonging to a job. */
public final class ContractDefinition {

    private final String id;
    private final String name;
    private final Material icon;
    private final List<String> description;
    private final long basePay;
    private final long randomBonus;
    private final double payPerBlock;
    private final boolean chargePassenger;
    private final int xpReward;
    private final int cooldownSeconds;
    private final boolean dispatchOnly;
    private final List<StepDefinition> steps;

    private ContractDefinition(String id, String name, Material icon, List<String> description, long basePay,
                               long randomBonus, double payPerBlock, boolean chargePassenger, int xpReward,
                               int cooldownSeconds, boolean dispatchOnly, List<StepDefinition> steps) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.description = Collections.unmodifiableList(description);
        this.basePay = basePay;
        this.randomBonus = randomBonus;
        this.payPerBlock = payPerBlock;
        this.chargePassenger = chargePassenger;
        this.xpReward = xpReward;
        this.cooldownSeconds = cooldownSeconds;
        this.dispatchOnly = dispatchOnly;
        this.steps = Collections.unmodifiableList(steps);
    }

    /** Returns null (and logs) when the contract has no usable steps. */
    public static ContractDefinition parse(ConfigurationSection section, String jobId, Logger logger) {
        String id = section.getString("id");
        if (id == null || id.isEmpty()) {
            logger.warning("[ACRPJobs] " + jobId + ": a contract has no id - skipped.");
            return null;
        }

        List<StepDefinition> steps = new ArrayList<StepDefinition>();
        for (Map<?, ?> rawStep : section.getMapList("steps")) {
            StepDefinition step = StepDefinition.parse(toSection(rawStep), jobId, id, logger);
            if (step != null) {
                steps.add(step);
            }
        }
        if (steps.isEmpty()) {
            logger.warning("[ACRPJobs] " + jobId + "/" + id + ": no valid steps - contract skipped.");
            return null;
        }

        Material icon = Material.PAPER;
        String rawIcon = section.getString("icon");
        if (rawIcon != null && !rawIcon.isEmpty()) {
            Material matched = Material.matchMaterial(rawIcon);
            if (matched != null) {
                icon = matched;
            } else {
                logger.warning("[ACRPJobs] " + jobId + "/" + id + ": unknown icon '" + rawIcon + "'.");
            }
        }

        return new ContractDefinition(
                id,
                section.getString("name", id),
                icon,
                section.getStringList("description"),
                Math.max(0L, section.getLong("basePay", 0L)),
                Math.max(0L, section.getLong("randomBonus", 0L)),
                Math.max(0.0D, section.getDouble("payPerBlock", 0.0D)),
                section.getBoolean("chargePassenger", false),
                Math.max(0, section.getInt("xpReward", 0)),
                Math.max(0, section.getInt("cooldownSeconds", 0)),
                section.getBoolean("dispatchOnly", false),
                steps);
    }

    /**
     * Steps are written as a YAML list of maps, and Bukkit hands those back as plain Maps. Wrapping
     * each one in a detached MemoryConfiguration lets StepDefinition read them like any other section.
     */
    private static ConfigurationSection toSection(Map<?, ?> raw) {
        MemoryConfiguration section = new MemoryConfiguration();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            section.set(String.valueOf(entry.getKey()), entry.getValue());
        }
        return section;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Material icon() {
        return icon;
    }

    public List<String> description() {
        return description;
    }

    public long basePay() {
        return basePay;
    }

    public long randomBonus() {
        return randomBonus;
    }

    public double payPerBlock() {
        return payPerBlock;
    }

    /** When true the fare comes out of the passenger's wallet instead of thin air. */
    public boolean chargePassenger() {
        return chargePassenger;
    }

    public int xpReward() {
        return xpReward;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    /** Dispatch-only contracts never appear in the contract menu - they start from an accepted call. */
    public boolean dispatchOnly() {
        return dispatchOnly;
    }

    public List<StepDefinition> steps() {
        return steps;
    }
}
