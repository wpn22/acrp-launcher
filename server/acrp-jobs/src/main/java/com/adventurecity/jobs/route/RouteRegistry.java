package com.adventurecity.jobs.route;

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
 * Loads and saves routes.yml, the file written by <code>/jobsadmin route</code>.
 *
 * <p>Mirrors {@link com.adventurecity.jobs.config.ZoneRegistry}: the admin draws in game, the file
 * is the record. It is read through an explicit UTF-8 reader because walker names are Arabic.</p>
 */
public final class RouteRegistry {

    private final File file;
    private final Logger logger;
    private final Map<String, Route> routes = new LinkedHashMap<String, Route>();

    public RouteRegistry(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void load() {
        routes.clear();
        if (!file.isFile()) {
            return;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
            ConfigurationSection root = config.getConfigurationSection("routes");
            if (root == null) {
                return;
            }
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                Route route = parse(id, section);
                if (route != null) {
                    routes.put(route.id(), route);
                }
            }
            logger.info("[ACRPJobs] Loaded " + routes.size() + " route(s).");
        } catch (IOException ex) {
            logger.severe("[ACRPJobs] Could not read routes.yml: " + ex.getMessage());
        } catch (RuntimeException ex) {
            logger.severe("[ACRPJobs] Invalid YAML in routes.yml: " + ex.getMessage());
        } finally {
            close(reader);
        }
    }

    private Route parse(String id, ConfigurationSection section) {
        Route route = new Route(id);
        route.worldName(section.getString("world", ""));
        route.mode(section.getString("mode", RoutePath.MODE_LOOP));
        route.speed(section.getDouble("speed", 3.2D));
        route.population(section.getInt("population", 3));
        route.entityType(section.getString("entity", "VILLAGER"));
        route.persona(section.getString("persona", ""));
        route.names(section.getStringList("names"));
        route.activationRange(section.getDouble("activationRange", 48.0D));
        route.pauseChance(section.getDouble("pauseChance", 0.15D));
        route.corners(readCorners(id, section.getList("corners")));

        if (route.worldName().isEmpty()) {
            logger.warning("[ACRPJobs] Route '" + id + "' has no world - skipped.");
            return null;
        }
        if (!route.path().valid()) {
            logger.warning("[ACRPJobs] Route '" + id + "' needs at least two corners apart from each"
                    + " other - it is loaded but nobody will walk it.");
        }
        return route;
    }

    /**
     * Accepts both shapes a corner can take: the compact string this class writes
     * (<code>"120.5,64.0,-88.5"</code>) and the list form (<code>[120.5, 64.0, -88.5]</code>) in case
     * the file was edited by hand.
     */
    private List<double[]> readCorners(String id, List<?> raw) {
        List<double[]> corners = new ArrayList<double[]>();
        if (raw == null) {
            return corners;
        }
        for (Object entry : raw) {
            double[] corner = null;
            if (entry instanceof String) {
                corner = parseString((String) entry);
            } else if (entry instanceof List) {
                corner = parseList((List<?>) entry);
            }
            if (corner == null) {
                logger.warning("[ACRPJobs] Route '" + id + "' has an unreadable corner - skipped: " + entry);
                continue;
            }
            corners.add(corner);
        }
        return corners;
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
        double[] corner = new double[3];
        for (int i = 0; i < 3; i++) {
            Object item = value.get(i);
            if (!(item instanceof Number)) {
                return null;
            }
            corner[i] = ((Number) item).doubleValue();
        }
        return corner;
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.options().header("مسارات سكان المدينة - تُرسم داخل اللعبة بـ /jobsadmin route new <اسم>\n"
                + "كل زاوية مكتوبة x,y,z . عدّل الأرقام هنا أو من /jobsadmin route set");
        for (Route route : routes.values()) {
            String base = "routes." + route.id() + ".";
            config.set(base + "world", route.worldName());
            config.set(base + "mode", route.mode());
            config.set(base + "speed", route.speed());
            config.set(base + "population", route.population());
            config.set(base + "entity", route.entityType());
            config.set(base + "persona", route.persona());
            config.set(base + "names", new ArrayList<String>(route.names()));
            config.set(base + "activationRange", route.activationRange());
            config.set(base + "pauseChance", route.pauseChance());

            List<String> corners = new ArrayList<String>();
            for (double[] corner : route.corners()) {
                corners.add(round(corner[0]) + "," + round(corner[1]) + "," + round(corner[2]));
            }
            config.set(base + "corners", corners);
        }
        try {
            config.save(file);
        } catch (IOException ex) {
            logger.severe("[ACRPJobs] Could not save routes.yml: " + ex.getMessage());
        }
    }

    public Route get(String id) {
        return id == null ? null : routes.get(id.toLowerCase());
    }

    public boolean exists(String id) {
        return get(id) != null;
    }

    public Collection<Route> all() {
        return routes.values();
    }

    public int size() {
        return routes.size();
    }

    public void put(Route route) {
        routes.put(route.id(), route);
        save();
    }

    public boolean remove(String id) {
        if (id == null || routes.remove(id.toLowerCase()) == null) {
            return false;
        }
        save();
        return true;
    }

    /** Two decimals is well below one pixel of movement and keeps the file readable. */
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
