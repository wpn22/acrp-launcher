package com.adventurecity.jobs.spot;

/**
 * What a work spot is, and therefore how it is shown and how it is cleared.
 *
 * <p>Only {@link #TRASH} costs an entity. The rest are particles, which is why a city can be
 * covered in work without costing the server anything.</p>
 */
public enum SpotType {

    /** A rubbish bag lying in the street - a dropped item the worker right-clicks. */
    TRASH(true, false),
    /** A stain on the ground - particles only, washed off with the water pump. */
    DIRT(false, true),
    /** A wilting plant - particles only, watered with the same pump. */
    PLANT(false, true),
    /** A dead street lamp - particles, repaired by right-clicking it. */
    LAMP(false, false);

    private final boolean entity;
    private final boolean pump;

    SpotType(boolean entity, boolean pump) {
        this.entity = entity;
        this.pump = pump;
    }

    /** True when this type needs a real entity in the world (and so counts against the cap). */
    public boolean needsEntity() {
        return entity;
    }

    /** True when the water pump is what clears it, rather than a right-click. */
    public boolean usesPump() {
        return pump;
    }

    public static SpotType fromString(String raw) {
        if (raw == null) {
            return null;
        }
        for (SpotType type : values()) {
            if (type.name().equalsIgnoreCase(raw.trim())) {
                return type;
            }
        }
        return null;
    }
}
