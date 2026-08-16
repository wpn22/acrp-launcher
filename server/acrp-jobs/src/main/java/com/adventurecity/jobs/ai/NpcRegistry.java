package com.adventurecity.jobs.ai;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Loads npcs.yml.
 *
 * <p>NPCs are matched to in-game entities by <em>display name</em> rather than by entity id.
 * That is deliberate: it works with Citizens and CustomNPCs alike, needs no compile-time
 * dependency on either, and survives restarts (a Citizens NPC gets a fresh entity uuid every
 * time it respawns, so a uuid link would break).</p>
 */
public final class NpcRegistry {

    private final File file;
    private final Logger logger;
    private final Map<String, NpcPersona> npcs = new LinkedHashMap<String, NpcPersona>();

    public NpcRegistry(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void load() {
        npcs.clear();
        if (!file.isFile()) {
            return;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
            ConfigurationSection root = config.getConfigurationSection("npcs");
            if (root == null) {
                return;
            }
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                NpcPersona persona = NpcPersona.parse(id, section);
                npcs.put(persona.id(), persona);
            }
            logger.info("[ACRPJobs] Loaded " + npcs.size() + " NPC persona(s).");
        } catch (IOException ex) {
            logger.severe("[ACRPJobs] Could not read npcs.yml: " + ex.getMessage());
        } catch (RuntimeException ex) {
            logger.severe("[ACRPJobs] Invalid YAML in npcs.yml: " + ex.getMessage());
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

    /** Persists only the entity links - the rest of npcs.yml stays as the admin wrote it. */
    public void saveLinks() {
        if (!file.isFile()) {
            return;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
            for (NpcPersona persona : npcs.values()) {
                config.set("npcs." + persona.id() + ".entityName", persona.entityName());
            }
            config.save(file);
        } catch (IOException ex) {
            logger.severe("[ACRPJobs] Could not save npcs.yml: " + ex.getMessage());
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

    public NpcPersona get(String id) {
        return id == null ? null : npcs.get(id.toLowerCase());
    }

    /** Finds the NPC linked to an entity display name, ignoring colour codes. */
    public NpcPersona byEntityName(String entityName) {
        if (entityName == null || entityName.isEmpty()) {
            return null;
        }
        String plain = strip(entityName);
        for (NpcPersona persona : npcs.values()) {
            String linked = persona.entityName();
            if (!linked.isEmpty() && strip(linked).equalsIgnoreCase(plain)) {
                return persona;
            }
        }
        return null;
    }

    public Collection<NpcPersona> all() {
        return npcs.values();
    }

    public boolean isEmpty() {
        return npcs.isEmpty();
    }

    private static String strip(String value) {
        String stripped = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', value));
        return stripped == null ? "" : stripped.trim();
    }
}
