package com.adventurecity.jobs.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Loads every jobs/*.yml file. Adding a job to the server is dropping a file in here + /jobsadmin reload. */
public final class JobRegistry {

    private final File folder;
    private final Logger logger;
    private final Map<String, JobDefinition> jobs = new LinkedHashMap<String, JobDefinition>();

    public JobRegistry(File folder, Logger logger) {
        this.folder = folder;
        this.logger = logger;
    }

    public void load() {
        jobs.clear();
        File[] files = folder.listFiles();
        if (files == null) {
            logger.warning("[ACRPJobs] jobs/ folder is missing - no jobs loaded.");
            return;
        }
        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase().endsWith(".yml")) {
                continue;
            }
            Reader reader = null;
            try {
                reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
                YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
                JobDefinition job = JobDefinition.parse(config, file.getName(), logger);
                if (job != null) {
                    if (jobs.containsKey(job.id())) {
                        logger.warning("[ACRPJobs] Duplicate job id '" + job.id() + "' in " + file.getName()
                                + " - the earlier one is kept.");
                        continue;
                    }
                    jobs.put(job.id(), job);
                }
            } catch (IOException ex) {
                logger.severe("[ACRPJobs] Could not read " + file.getName() + ": " + ex.getMessage());
            } catch (RuntimeException ex) {
                logger.severe("[ACRPJobs] Invalid YAML in " + file.getName() + ": " + ex.getMessage());
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ignored) {
                        // nothing useful to do
                    }
                }
            }
        }
        logger.info("[ACRPJobs] Loaded " + jobs.size() + " job(s): " + String.join(", ", jobs.keySet()));
    }

    public JobDefinition get(String id) {
        return id == null ? null : jobs.get(id.toLowerCase());
    }

    public Collection<JobDefinition> all() {
        return jobs.values();
    }

    public boolean exists(String id) {
        return get(id) != null;
    }

    /** Jobs that listen on the given dispatch channel (for example "taxi", later "911"). */
    public List<JobDefinition> byDispatchChannel(String channel) {
        List<JobDefinition> out = new ArrayList<JobDefinition>();
        if (channel == null || channel.isEmpty()) {
            return out;
        }
        for (JobDefinition job : jobs.values()) {
            if (channel.equalsIgnoreCase(job.dispatchChannel())) {
                out.add(job);
            }
        }
        return out;
    }

    /**
     * Every zone id and zone group the loaded jobs reference. Used by /jobsadmin zone check so the
     * owner can see exactly what still needs to be placed before the jobs are playable.
     */
    public Set<String> requiredZoneIds() {
        Set<String> out = new LinkedHashSet<String>();
        for (JobDefinition job : jobs.values()) {
            out.addAll(job.dutyZones());
            for (ContractDefinition contract : job.contracts().values()) {
                for (StepDefinition step : contract.steps()) {
                    if (step.zone() != null && !step.zone().isEmpty()) {
                        out.add(step.zone().toLowerCase());
                    }
                }
            }
        }
        return out;
    }

    /** Spot pools the loaded jobs reference, so /jobsadmin zone check can flag missing ones. */
    public Set<String> requiredSpotPools() {
        Set<String> out = new LinkedHashSet<String>();
        for (JobDefinition job : jobs.values()) {
            for (ContractDefinition contract : job.contracts().values()) {
                for (StepDefinition step : contract.steps()) {
                    if (step.pool() != null && !step.pool().isEmpty()) {
                        out.add(step.pool().toLowerCase());
                    }
                }
            }
        }
        return out;
    }

    public Set<String> requiredZoneGroups() {
        Set<String> out = new LinkedHashSet<String>();
        for (JobDefinition job : jobs.values()) {
            for (ContractDefinition contract : job.contracts().values()) {
                for (StepDefinition step : contract.steps()) {
                    if (step.zoneGroup() != null && !step.zoneGroup().isEmpty()) {
                        out.add(step.zoneGroup().toLowerCase());
                    }
                }
            }
        }
        return out;
    }
}
