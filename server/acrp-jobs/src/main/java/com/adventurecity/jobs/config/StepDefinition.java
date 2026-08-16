package com.adventurecity.jobs.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.logging.Logger;

/** One step of a contract, as written in a job YAML file. */
public final class StepDefinition {

    private final StepType type;
    private final String title;
    private final String zone;
    private final String zoneGroup;
    private final double radius;
    private final Material item;
    private final int amount;
    private final String itemName;
    private final int seconds;
    private final boolean markRideStart;
    private final boolean targetPlayer;

    private StepDefinition(StepType type, String title, String zone, String zoneGroup, double radius,
                           Material item, int amount, String itemName, int seconds,
                           boolean markRideStart, boolean targetPlayer) {
        this.type = type;
        this.title = title;
        this.zone = zone;
        this.zoneGroup = zoneGroup;
        this.radius = radius;
        this.item = item;
        this.amount = amount;
        this.itemName = itemName;
        this.seconds = seconds;
        this.markRideStart = markRideStart;
        this.targetPlayer = targetPlayer;
    }

    /** Returns null (and logs) when the section is not a usable step, so one bad step cannot break the server. */
    public static StepDefinition parse(ConfigurationSection section, String jobId, String contractId, Logger logger) {
        StepType type = StepType.fromString(section.getString("type"));
        if (type == null) {
            logger.warning("[ACRPJobs] " + jobId + "/" + contractId + ": unknown step type '"
                    + section.getString("type") + "' - step skipped.");
            return null;
        }

        Material material = null;
        String rawItem = section.getString("item");
        if (rawItem != null && !rawItem.isEmpty()) {
            material = Material.matchMaterial(rawItem);
            if (material == null) {
                logger.warning("[ACRPJobs] " + jobId + "/" + contractId + ": unknown item '" + rawItem
                        + "' - falling back to PAPER.");
                material = Material.PAPER;
            }
        }
        if ((type == StepType.PICKUP_ITEM) && material == null) {
            material = Material.PAPER;
        }

        return new StepDefinition(
                type,
                section.getString("title", "مهمة"),
                section.getString("zone", null),
                section.getString("zoneGroup", null),
                Math.max(0.0D, section.getDouble("radius", 0.0D)),
                material,
                Math.max(1, section.getInt("amount", 1)),
                section.getString("itemName", null),
                Math.max(1, section.getInt("seconds", 10)),
                section.getBoolean("markRideStart", false),
                "PLAYER".equalsIgnoreCase(section.getString("target", "")));
    }

    public StepType type() {
        return type;
    }

    public String title() {
        return title;
    }

    public String zone() {
        return zone;
    }

    public String zoneGroup() {
        return zoneGroup;
    }

    /** 0 means "use the zone's own radius". */
    public double radius() {
        return radius;
    }

    public Material item() {
        return item;
    }

    public int amount() {
        return amount;
    }

    public String itemName() {
        return itemName;
    }

    public int seconds() {
        return seconds;
    }

    /** Marks where a paid-by-distance ride began (taxi pickup). */
    public boolean markRideStart() {
        return markRideStart;
    }

    /** For CONFIRM steps: measure distance against the dispatch player instead of a zone. */
    public boolean targetPlayer() {
        return targetPlayer;
    }

    public boolean needsZone() {
        return type == StepType.GOTO_ZONE || type == StepType.DELIVER_ITEM;
    }
}
