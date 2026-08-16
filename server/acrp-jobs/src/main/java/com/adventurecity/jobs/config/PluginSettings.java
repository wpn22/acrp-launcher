package com.adventurecity.jobs.config;

import org.bukkit.configuration.file.FileConfiguration;

/** Typed snapshot of config.yml, re-created on every reload. */
public final class PluginSettings {

    public final String storageType;
    public final String mysqlHost;
    public final int mysqlPort;
    public final String mysqlDatabase;
    public final String mysqlUsername;
    public final String mysqlPassword;
    public final boolean mysqlUseSsl;
    public final int autosaveSeconds;

    public final String currencySymbol;
    public final long startingBalance;
    public final boolean allowPlayerTransfer;
    public final double transferTaxPercent;
    public final long minTransfer;

    public final int maxJobsPerPlayer;
    public final boolean requireZoneForDuty;
    public final boolean endDutyOnQuit;

    public final boolean payrollEnabled;
    public final int afkSeconds;

    public final long dailyEarningCap;
    public final int minContractSeconds;
    public final double maxBlocksPerSecond;
    public final boolean logSuspicious;

    public final boolean hudBossBar;
    public final boolean hudActionBar;
    public final boolean hudParticles;
    public final int particleRange;

    public final int dispatchExpireSeconds;
    public final int maxOpenCalls;

    public final boolean vehiclesEnabled;
    public final String vehicleSpawnCommand;
    public final String vehicleDespawnCommand;

    public PluginSettings(FileConfiguration config) {
        storageType = config.getString("storage.type", "sqlite").toLowerCase();
        mysqlHost = config.getString("storage.mysql.host", "127.0.0.1");
        mysqlPort = config.getInt("storage.mysql.port", 3306);
        mysqlDatabase = config.getString("storage.mysql.database", "acrp");
        mysqlUsername = config.getString("storage.mysql.username", "root");
        mysqlPassword = config.getString("storage.mysql.password", "");
        mysqlUseSsl = config.getBoolean("storage.mysql.useSSL", false);
        autosaveSeconds = Math.max(30, config.getInt("storage.autosaveSeconds", 300));

        currencySymbol = config.getString("economy.symbol", "AC");
        startingBalance = Math.max(0L, config.getLong("economy.startingBalance", 5000L));
        allowPlayerTransfer = config.getBoolean("economy.allowPlayerTransfer", true);
        transferTaxPercent = clampPercent(config.getDouble("economy.transferTaxPercent", 5.0D));
        minTransfer = Math.max(1L, config.getLong("economy.minTransfer", 100L));

        maxJobsPerPlayer = Math.max(1, config.getInt("jobs.maxJobsPerPlayer", 3));
        requireZoneForDuty = config.getBoolean("jobs.requireZoneForDuty", true);
        endDutyOnQuit = config.getBoolean("jobs.endDutyOnQuit", true);

        payrollEnabled = config.getBoolean("payroll.enabled", true);
        afkSeconds = Math.max(30, config.getInt("payroll.afkSeconds", 300));

        dailyEarningCap = Math.max(0L, config.getLong("antiAbuse.dailyEarningCap", 60000L));
        minContractSeconds = Math.max(0, config.getInt("antiAbuse.minContractSeconds", 20));
        maxBlocksPerSecond = Math.max(1.0D, config.getDouble("antiAbuse.maxBlocksPerSecond", 22.0D));
        logSuspicious = config.getBoolean("antiAbuse.logSuspicious", true);

        hudBossBar = config.getBoolean("hud.bossBar", true);
        hudActionBar = config.getBoolean("hud.actionBar", true);
        hudParticles = config.getBoolean("hud.particles", true);
        particleRange = Math.max(8, config.getInt("hud.particleRange", 64));

        dispatchExpireSeconds = Math.max(15, config.getInt("dispatch.expireSeconds", 180));
        maxOpenCalls = Math.max(1, config.getInt("dispatch.maxOpenCalls", 30));

        vehiclesEnabled = config.getBoolean("integration.vehicles.enabled", false);
        vehicleSpawnCommand = config.getString("integration.vehicles.spawnCommand", "");
        vehicleDespawnCommand = config.getString("integration.vehicles.despawnCommand", "");
    }

    public boolean isMysql() {
        return "mysql".equals(storageType);
    }

    private static double clampPercent(double value) {
        if (value < 0.0D) {
            return 0.0D;
        }
        return value > 100.0D ? 100.0D : value;
    }
}
