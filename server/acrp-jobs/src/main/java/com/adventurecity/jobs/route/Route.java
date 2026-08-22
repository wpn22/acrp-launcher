package com.adventurecity.jobs.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One route drawn in game: the corners the admin clicked plus how the walkers on it behave.
 *
 * <p>Like {@link RoutePath} this holds plain values only - the world name is a string and the
 * entity type is a string - so the whole model layer stays testable and the Bukkit lookups happen
 * in {@link RouteService} where they belong.</p>
 */
public final class Route {

    /** A route with more walkers than this is a mistake, not a design; the cap is server-wide anyway. */
    public static final int MAX_POPULATION = 20;

    private final String id;
    private String worldName = "";
    private String mode = RoutePath.MODE_LOOP;
    private double speed = 3.2D;
    private int population = 3;
    private String entityType = "VILLAGER";
    private String persona = "";
    private String leadPersona = "";
    private List<String> names = new ArrayList<String>();
    private double activationRange = 48.0D;
    private double pauseChance = 0.15D;

    private final List<double[]> corners = new ArrayList<double[]>();
    private RoutePath path = new RoutePath((double[][]) null, true);

    public Route(String id) {
        this.id = id == null ? "" : id.toLowerCase();
    }

    public String id() {
        return id;
    }

    // ---------------------------------------------------------------- corners

    public List<double[]> corners() {
        return Collections.unmodifiableList(corners);
    }

    public int cornerCount() {
        return corners.size();
    }

    public void corners(List<double[]> replacement) {
        corners.clear();
        if (replacement != null) {
            for (double[] corner : replacement) {
                if (corner != null && corner.length >= 3) {
                    corners.add(new double[] { corner[0], corner[1], corner[2] });
                }
            }
        }
        rebuild();
    }

    /** The walkable line through the corners. Rebuilt whenever the corners or the mode change. */
    public RoutePath path() {
        return path;
    }

    private void rebuild() {
        path = new RoutePath(corners, loop());
    }

    // ---------------------------------------------------------------- settings

    public String worldName() {
        return worldName;
    }

    public void worldName(String worldName) {
        this.worldName = worldName == null ? "" : worldName;
    }

    public String mode() {
        return mode;
    }

    public boolean loop() {
        return RoutePath.MODE_LOOP.equals(mode);
    }

    /** Anything that is not PINGPONG becomes LOOP - an unreadable value must not break the route. */
    public void mode(String mode) {
        String upper = mode == null ? "" : mode.trim().toUpperCase();
        this.mode = RoutePath.MODE_PINGPONG.equals(upper) ? RoutePath.MODE_PINGPONG : RoutePath.MODE_LOOP;
        rebuild();
    }

    public double speed() {
        return speed;
    }

    public void speed(double speed) {
        this.speed = clamp(speed, 0.3D, 12.0D);
    }

    public int population() {
        return population;
    }

    public void population(int population) {
        this.population = (int) clamp(population, 0.0D, MAX_POPULATION);
    }

    public String entityType() {
        return entityType;
    }

    public void entityType(String entityType) {
        this.entityType = entityType == null || entityType.trim().isEmpty()
                ? "VILLAGER" : entityType.trim().toUpperCase();
    }

    /** Empty means a silent extra; a persona id from npcs.yml makes the walkers talkable. */
    public String persona() {
        return persona;
    }

    public void persona(String persona) {
        this.persona = persona == null ? "" : persona.trim().toLowerCase();
    }

    public boolean hasPersona() {
        return !persona.isEmpty();
    }

    /**
     * Persona for the first walker on this route only. That is how one named supervisor ends up
     * walking a round among a handful of anonymous citizens instead of three identical foremen.
     */
    public String leadPersona() {
        return leadPersona;
    }

    public void leadPersona(String leadPersona) {
        this.leadPersona = leadPersona == null ? "" : leadPersona.trim().toLowerCase();
    }

    public boolean hasLeadPersona() {
        return !leadPersona.isEmpty();
    }

    /** The persona for walker number {@code index} on this route, or empty for a silent extra. */
    public String personaFor(int index) {
        if (index == 0 && !leadPersona.isEmpty()) {
            return leadPersona;
        }
        return persona;
    }

    public List<String> names() {
        return Collections.unmodifiableList(names);
    }

    public void names(List<String> names) {
        List<String> copy = new ArrayList<String>();
        if (names != null) {
            for (String name : names) {
                if (name != null && !name.trim().isEmpty()) {
                    copy.add(name.trim());
                }
            }
        }
        this.names = copy;
    }

    public double activationRange() {
        return activationRange;
    }

    public void activationRange(double activationRange) {
        this.activationRange = clamp(activationRange, 8.0D, 200.0D);
    }

    public double pauseChance() {
        return pauseChance;
    }

    public void pauseChance(double pauseChance) {
        this.pauseChance = clamp(pauseChance, 0.0D, 1.0D);
    }

    /** Seconds for one full round trip - what the admin is shown before confirming. */
    public double lapSeconds() {
        if (speed <= 0.0D) {
            return 0.0D;
        }
        return path.cycleLength() / speed;
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }
}
