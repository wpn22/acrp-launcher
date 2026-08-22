package com.adventurecity.jobs.spot;

import org.bukkit.configuration.ConfigurationSection;
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
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Loads and saves spots.yml, the file written by <code>/jobsadmin spot</code>.
 *
 * <p>Same shape as {@link com.adventurecity.jobs.route.RouteRegistry}: the admin marks points in
 * game, the file is the record. Rotation state is never written - a restart re-seeds the pools.</p>
 */
public final class SpotRegistry {

    private final File file;
    private final Logger logger;
    private final Map<String, SpotPool> pools = new LinkedHashMap<String, SpotPool>();

    public SpotRegistry(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void load() {
        pools.clear();
        if (!file.isFile()) {
            return;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
            ConfigurationSection root = config.getConfigurationSection("pools");
            if (root == null) {
                return;
            }
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                SpotPool pool = parse(id, section);
                if (pool != null) {
                    pools.put(pool.id(), pool);
                }
            }
            logger.info("[ACRPJobs] Loaded " + pools.size() + " work spot pool(s).");
        } catch (IOException ex) {
            logger.severe("[ACRPJobs] Could not read spots.yml: " + ex.getMessage());
        } catch (RuntimeException ex) {
            logger.severe("[ACRPJobs] Invalid YAML in spots.yml: " + ex.getMessage());
        } finally {
            close(reader);
        }
    }

    private SpotPool parse(String id, ConfigurationSection section) {
        SpotPool pool = new SpotPool(id);

        SpotType type = SpotType.fromString(section.getString("type"));
        if (type == null) {
            logger.warning("[ACRPJobs] Spot pool '" + id + "' has an unknown type '"
                    + section.getString("type") + "' - using TRASH.");
            type = SpotType.TRASH;
        }
        pool.type(type);
        pool.worldName(section.getString("world", ""));
        pool.activeCount(section.getInt("activeCount", 9));
        pool.respawnMinutes(section.getInt("respawnMinutes", 10));
        pool.activationRange(section.getDouble("activationRange", 48.0D));
        pool.swapBlock(section.getBoolean("swapBlock", false));
        pool.points(readPoints(id, section.getList("points")));

        if (pool.worldName().isEmpty()) {
            logger.warning("[ACRPJobs] Spot pool '" + id + "' has no world - skipped.");
            return null;
        }
        if (pool.pointCount() == 0) {
            logger.warning("[ACRPJobs] Spot pool '" + id + "' has no points - it is loaded but"
                    + " nothing will appear. Mark some with /jobsadmin spot new " + id);
        }
        return pool;
    }

    /**
     * Accepts both the compact string this class writes (<code>"120.5,64.0,-88.5"</code>) and the
     * list form (<code>[120.5, 64.0, -88.5]</code>) in case the file was edited by hand.
     */
    private List<double[]> readPoints(String id, List<?> raw) {
        List<double[]> points = new ArrayList<double[]>();
        if (raw == null) {
            return points;
        }
        for (Object entry : raw) {
            double[] point = null;
            if (entry instanceof String) {
                point = parseString((String) entry);
            } else if (entry instanceof List) {
                point = parseList((List<?>) entry);
            }
            if (point == null) {
                logger.warning("[ACRPJobs] Spot pool '" + id + "' has an unreadable point - skipped: " + entry);
                continue;
            }
            points.add(point);
        }
        return points;
    }

    private static double[] parseString(String value) {
        String[] parts = value.split(",");
        if (parts.length < 3) {
            return null;
        }
        try {
            return new double[] {
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()) };
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static double[] parseList(List<?> value) {
        if (value.size() < 3) {
            return null;
        }
        double[] point = new double[3];
        for (int i = 0; i < 3; i++) {
            Object item = value.get(i);
            if (!(item instanceof Number)) {
                return null;
            }
            point[i] = ((Number) item).doubleValue();
        }
        return point;
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.options().header("نقاط العمل - تُحدَّد داخل اللعبة بـ /jobsadmin spot new <اسم> <النوع>\n"
                + "كل نقطة مكتوبة x,y,z . عدّل الأرقام هنا أو من /jobsadmin spot set");
        for (SpotPool pool : pools.values()) {
            String base = "pools." + pool.id() + ".";
            config.set(base + "type", pool.type().name());
            config.set(base + "world", pool.worldName());
            config.set(base + "activeCount", pool.activeCount());
            config.set(base + "respawnMinutes", pool.respawnMinutes());
            config.set(base + "activationRange", pool.activationRange());
            if (pool.type() == SpotType.LAMP) {
                config.set(base + "swapBlock", pool.swapBlock());
            }

            List<String> points = new ArrayList<String>();
            for (WorkSpot spot : pool.points()) {
                points.add(round(spot.x()) + "," + round(spot.y()) + "," + round(spot.z()));
            }
            config.set(base + "points", points);
        }
        try {
            config.save(file);
        } catch (IOException ex) {
            logger.severe("[ACRPJobs] Could not save spots.yml: " + ex.getMessage());
        }
    }

    public SpotPool get(String id) {
        return id == null ? null : pools.get(id.toLowerCase());
    }

    public boolean exists(String id) {
        return get(id) != null;
    }

    public Collection<SpotPool> all() {
        return pools.values();
    }

    public int size() {
        return pools.size();
    }

    public void put(SpotPool pool) {
        pools.put(pool.id(), pool);
        save();
    }

    public boolean remove(String id) {
        if (id == null || pools.remove(id.toLowerCase()) == null) {
            return false;
        }
        save();
        return true;
    }

    private static String round(double value) {
        return String.valueOf(Math.round(value * 100.0D) / 100.0D);
    }

    private void close(Reader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {
                // nothing useful to do
            }
        }
    }
}
