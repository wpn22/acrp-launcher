package com.adventurecity.jobs;

import com.adventurecity.jobs.ai.AiBridgeClient;
import com.adventurecity.jobs.ai.DialogueService;
import com.adventurecity.jobs.ai.NpcRegistry;
import com.adventurecity.jobs.antiabuse.ActivityTracker;
import com.adventurecity.jobs.antiabuse.Cooldowns;
import com.adventurecity.jobs.command.AcCommand;
import com.adventurecity.jobs.command.DutyCommand;
import com.adventurecity.jobs.command.JobsAdminCommand;
import com.adventurecity.jobs.command.JobsCommand;
import com.adventurecity.jobs.command.TalkCommand;
import com.adventurecity.jobs.command.TaxiCommand;
import com.adventurecity.jobs.config.JobRegistry;
import com.adventurecity.jobs.config.PluginSettings;
import com.adventurecity.jobs.config.ZoneRegistry;
import com.adventurecity.jobs.contract.ContractEngine;
import com.adventurecity.jobs.dispatch.DispatchService;
import com.adventurecity.jobs.economy.EconomyService;
import com.adventurecity.jobs.economy.PayoutService;
import com.adventurecity.jobs.hud.ObjectiveHud;
import com.adventurecity.jobs.job.DutyManager;
import com.adventurecity.jobs.job.JobManager;
import com.adventurecity.jobs.job.PayrollTask;
import com.adventurecity.jobs.listener.NpcListener;
import com.adventurecity.jobs.listener.PlayerListener;
import com.adventurecity.jobs.listener.RouteListener;
import com.adventurecity.jobs.listener.SpotListener;
import com.adventurecity.jobs.route.RouteService;
import com.adventurecity.jobs.spot.SpotService;
import com.adventurecity.jobs.storage.PlayerDataManager;
import com.adventurecity.jobs.storage.SqlStorage;
import com.adventurecity.jobs.ui.MenuListener;
import com.adventurecity.jobs.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;

/**
 * ACRPJobs - the interactive jobs system for Adventure City Roleplay.
 *
 * <p>Doubles as the service locator: every manager takes this plugin and looks its collaborators up
 * lazily, which keeps construction order simple and makes /jobsadmin reload swap settings safely.</p>
 */
public final class ACRPJobsPlugin extends JavaPlugin {

    private static final String[] BUNDLED_JOBS = { "delivery.yml", "taxi.yml", "trucker.yml",
            "cleaner.yml", "gardener.yml", "electrician.yml", "builder.yml" };

    private PluginSettings settings;
    private Msg msg;
    private ZoneRegistry zones;
    private JobRegistry jobs;

    private SqlStorage storage;
    private PlayerDataManager players;
    private EconomyService economy;
    private PayoutService payouts;

    private ActivityTracker activity;
    private Cooldowns cooldowns;
    private ObjectiveHud hud;
    private JobManager jobManager;
    private DutyManager duty;
    private ContractEngine contracts;
    private DispatchService dispatch;
    private NpcRegistry npcs;
    private AiBridgeClient aiBridge;
    private DialogueService dialogue;
    private RouteService routes;
    private SpotService spots;

    private PayrollTask payrollTask;
    private BukkitTask tickTask;
    private BukkitTask payrollHandle;
    private BukkitTask autosaveTask;
    private BukkitTask routeTask;
    private BukkitTask spotTask;

    @Override
    public void onEnable() {
        saveDefaultResources();

        settings = new PluginSettings(getConfig());
        msg = new Msg(new File(getDataFolder(), "messages_ar.yml"), getLogger());
        zones = new ZoneRegistry(new File(getDataFolder(), "zones.yml"), getLogger());
        zones.load();
        jobs = new JobRegistry(new File(getDataFolder(), "jobs"), getLogger());
        jobs.load();

        storage = new SqlStorage(settings, getDataFolder(), getLogger());
        try {
            storage.init();
        } catch (Exception ex) {
            getLogger().severe("[ACRPJobs] Storage failed to start - the plugin will stay disabled.");
            getLogger().severe("[ACRPJobs] " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        players = new PlayerDataManager(this, storage, getLogger());
        economy = new EconomyService(this);
        payouts = new PayoutService(this);
        activity = new ActivityTracker();
        cooldowns = new Cooldowns();
        hud = new ObjectiveHud(this);
        jobManager = new JobManager(this);
        duty = new DutyManager(this);
        contracts = new ContractEngine(this);
        dispatch = new DispatchService(this);
        npcs = new NpcRegistry(new File(getDataFolder(), "npcs.yml"), getLogger());
        npcs.load();
        aiBridge = new AiBridgeClient(this);
        dialogue = new DialogueService(this);
        routes = new RouteService(this);
        // Anything wearing our tag is a leftover from a crash - clear it before loading our own.
        int swept = routes.sweep();
        if (swept > 0) {
            getLogger().info("[ACRPJobs] Removed " + swept + " route NPC(s) left over from a previous run.");
        }
        routes.load();

        spots = new SpotService(this);
        int sweptSpots = spots.sweep();
        if (sweptSpots > 0) {
            getLogger().info("[ACRPJobs] Removed " + sweptSpots + " work spot item(s) left over from a previous run.");
        }
        spots.load();

        registerCommands();
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new NpcListener(this), this);
        getServer().getPluginManager().registerEvents(new RouteListener(this), this);
        getServer().getPluginManager().registerEvents(new SpotListener(this), this);
        startTasks();

        // /reload or a late install: players are already online and need their data.
        for (Player player : Bukkit.getOnlinePlayers()) {
            activity.markActive(player);
            players.loadAsync(player.getUniqueId(), player.getName(), null);
        }

        getLogger().info("[ACRPJobs] Enabled - " + jobs.all().size() + " job(s), "
                + zones.all().size() + " zone(s), " + npcs.all().size() + " npc(s), "
                + routes.routeCount() + " route(s), "
                + spots.registry().size() + " spot pool(s)"
                + (aiBridge.enabled() ? ", AI dialogue on." : ", AI dialogue off."));
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (payrollHandle != null) {
            payrollHandle.cancel();
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (routeTask != null) {
            routeTask.cancel();
        }
        if (spotTask != null) {
            spotTask.cancel();
        }
        if (routes != null) {
            routes.shutdown();
        }
        if (spots != null) {
            spots.shutdown();
        }
        if (hud != null) {
            hud.clearAll();
        }
        if (dispatch != null) {
            dispatch.clear();
        }
        if (aiBridge != null) {
            aiBridge.shutdown();
        }
        if (players != null) {
            players.shutdown();
        }
        getLogger().info("[ACRPJobs] Disabled - player data flushed.");
    }

    private void saveDefaultResources() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages_ar.yml").isFile()) {
            saveResource("messages_ar.yml", false);
        }
        if (!new File(getDataFolder(), "npcs.yml").isFile()) {
            saveResource("npcs.yml", false);
        }
        if (!new File(getDataFolder(), "routes.yml").isFile()) {
            saveResource("routes.yml", false);
        }
        if (!new File(getDataFolder(), "spots.yml").isFile()) {
            saveResource("spots.yml", false);
        }
        File jobFolder = new File(getDataFolder(), "jobs");
        if (!jobFolder.isDirectory() && !jobFolder.mkdirs()) {
            getLogger().warning("[ACRPJobs] Could not create the jobs folder.");
        }
        for (String name : BUNDLED_JOBS) {
            if (!new File(jobFolder, name).isFile()) {
                saveResource("jobs/" + name, false);
            }
        }
    }

    private void registerCommands() {
        JobsCommand jobsCommand = new JobsCommand(this);
        bind("jobs", jobsCommand, jobsCommand);
        bind("duty", new DutyCommand(this), null);
        AcCommand acCommand = new AcCommand(this);
        bind("ac", acCommand, acCommand);
        TaxiCommand taxiCommand = new TaxiCommand(this);
        bind("taxi", taxiCommand, taxiCommand);
        JobsAdminCommand adminCommand = new JobsAdminCommand(this);
        bind("jobsadmin", adminCommand, adminCommand);
        TalkCommand talkCommand = new TalkCommand(this);
        bind("talk", talkCommand, talkCommand);
    }

    private void bind(String name, org.bukkit.command.CommandExecutor executor,
                      org.bukkit.command.TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("[ACRPJobs] Command /" + name + " is missing from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        if (completer != null) {
            command.setTabCompleter(completer);
        }
    }

    private void startTasks() {
        // Contracts and dispatch: twice a second is responsive without being expensive.
        tickTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                contracts.tick();
                dispatch.tick();
                dialogue.tick();
                routes.editor().tickVisuals();
            }
        }, 20L, 10L);

        // Walkers move by teleport, so they need a shorter period than everything else - every
        // two ticks is smooth on the client. The spawn/despawn decision rides along once a second.
        routeTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                routes.tick();
            }
        }, 40L, 2L);

        // Work spots: four times a second is smooth enough for the pump jet, and the rotation
        // bookkeeping only runs every eighth pass.
        spotTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                spots.tick();
            }
        }, 60L, 5L);

        payrollTask = new PayrollTask(this);
        payrollHandle = payrollTask.runTaskTimer(this, 1200L, 1200L);

        long autosaveTicks = settings.autosaveSeconds * 20L;
        autosaveTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                players.saveDirtyAsync();
                cooldowns.purgeExpired();
            }
        }, autosaveTicks, autosaveTicks);
    }

    /**
     * Re-reads config.yml, messages, jobs and zones. Storage settings are deliberately not re-applied
     * live - changing the database needs a restart.
     */
    public void reloadEverything() {
        reloadConfig();
        settings = new PluginSettings(getConfig());
        msg.load();
        zones.load();
        jobs.load();
        npcs.load();
        routes.load();
        spots.load();
    }

    public PluginSettings settings() {
        return settings;
    }

    public Msg msg() {
        return msg;
    }

    public ZoneRegistry zones() {
        return zones;
    }

    public JobRegistry jobs() {
        return jobs;
    }

    public PlayerDataManager players() {
        return players;
    }

    public EconomyService economy() {
        return economy;
    }

    public PayoutService payouts() {
        return payouts;
    }

    public ActivityTracker activity() {
        return activity;
    }

    public Cooldowns cooldowns() {
        return cooldowns;
    }

    public ObjectiveHud hud() {
        return hud;
    }

    public JobManager jobManager() {
        return jobManager;
    }

    public DutyManager duty() {
        return duty;
    }

    public ContractEngine contracts() {
        return contracts;
    }

    public DispatchService dispatch() {
        return dispatch;
    }

    public PayrollTask payrollTask() {
        return payrollTask;
    }

    public NpcRegistry npcs() {
        return npcs;
    }

    public AiBridgeClient aiBridge() {
        return aiBridge;
    }

    public DialogueService dialogue() {
        return dialogue;
    }

    public RouteService routes() {
        return routes;
    }

    public SpotService spots() {
        return spots;
    }
}
