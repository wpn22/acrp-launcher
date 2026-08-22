package com.adventurecity.jobs.config;

import org.bukkit.configuration.ConfigurationSection;

/** One rank inside a job: higher rank means a bigger salary and a payout multiplier. */
public final class JobGrade {

    private final int level;
    private final String name;
    private final long salary;
    private final long xpRequired;
    private final double payMultiplier;

    public JobGrade(int level, String name, long salary, long xpRequired, double payMultiplier) {
        this.level = level;
        this.name = name;
        this.salary = salary;
        this.xpRequired = xpRequired;
        this.payMultiplier = payMultiplier;
    }

    public static JobGrade parse(ConfigurationSection section, int fallbackLevel) {
        return new JobGrade(
                section.getInt("level", fallbackLevel),
                section.getString("name", "رتبة " + fallbackLevel),
                Math.max(0L, section.getLong("salary", 0L)),
                Math.max(0L, section.getLong("xpRequired", 0L)),
                Math.max(0.1D, section.getDouble("payMultiplier", 1.0D)));
    }

    public int level() {
        return level;
    }

    public String name() {
        return name;
    }

    public long salary() {
        return salary;
    }

    public long xpRequired() {
        return xpRequired;
    }

    public double payMultiplier() {
        return payMultiplier;
    }
}
