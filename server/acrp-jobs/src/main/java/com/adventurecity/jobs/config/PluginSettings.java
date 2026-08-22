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

    /** Master switch over the only world write this plugin is capable of. Off by default. */
    public final boolean allowBlockChanges;

    public final boolean spotsEnabled;
    public final int maxActiveSpotEntities;
    public final int spotActiveCount;
    public final int spotRespawnMinutes;
    public final double spotActivationRange;
    public final int pumpSeconds;
    public final String trashMaterial;

    public final boolean routesEnabled;
    public final int maxActiveWalkers;
    public final double routeActivationRange;
    public final double routeSpeed;
    public final int routePopulation;

    public final boolean vehiclesEnabled;
    public final String vehicleSpawnCommand;
    public final String vehicleDespawnCommand;

    public final boolean aiEnabled;
    public final String aiUrl;
    public final String aiToken;
    public final int aiTimeoutMs;
    public final int aiConversationSeconds;
    public final double aiConversationRange;

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

        allowBlockChanges = config.getBoolean("world.allowBlockChanges", false);

        spotsEnabled = config.getBoolean("spots.enabled", true);
        // Only TRASH pools cost entities; dirt, plants and lamps are particles, so this cap is
        // about rubbish bags only.
        maxActiveSpotEntities = Math.max(0, Math.min(80, config.getInt("spots.maxActiveEntities", 20)));
        spotActiveCount = Math.max(0, config.getInt("spots.defaults.activeCount", 9));
        spotRespawnMinutes = Math.max(0, config.getInt("spots.defaults.respawnMinutes", 10));
        spotActivationRange = Math.max(8.0D, config.getDouble("spots.defaults.activationRange", 48.0D));
        pumpSeconds = Math.max(1, Math.min(30, config.getInt("spots.pumpSeconds", 3)));
        trashMaterial = config.getString("spots.trashMaterial", "PAPER");

        routesEnabled = config.getBoolean("routes.enabled", true);
        // The one number that decides what the route system costs the server. 50 is a hard ceiling
        // so a typo cannot fill the map with entities.
        maxActiveWalkers = Math.max(0, Math.min(50, config.getInt("routes.maxActiveWalkers", 5)));
        routeActivationRange = Math.max(8.0D, config.getDouble("routes.defaults.activationRange", 48.0D));
        routeSpeed = Math.max(0.3D, config.getDouble("routes.defaults.speed", 3.2D));
        routePopulation = Math.max(0, Math.min(20, config.getInt("routes.defaults.population", 3)));

        vehiclesEnabled = config.getBoolean("integration.vehicles.enabled", false);
        vehicleSpawnCommand = config.getString("integration.vehicles.spawnCommand", "");
        vehicleDespawnCommand = config.getString("integration.vehicles.despawnCommand", "");

        aiEnabled = config.getBoolean("ai.enabled", false);
        aiUrl = config.getString("ai.url", "http://127.0.0.1:8787/dialogue");
        aiToken = config.getString("ai.token", "");
        aiTimeoutMs = Math.max(1000, config.getInt("ai.timeoutMs", 8000));
        aiConversationSeconds = Math.max(15, config.getInt("ai.conversationSeconds", 90));
        aiConversationRange = Math.max(2.0D, config.getDouble("ai.conversationRange", 12.0D));
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
