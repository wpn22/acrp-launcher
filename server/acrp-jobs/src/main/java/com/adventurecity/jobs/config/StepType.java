package com.adventurecity.jobs.config;

/**
 * The reusable building blocks every job is assembled from. Adding a job means combining these in a
 * YAML file - no new code. Police/EMS/mechanic in later phases add types here and reuse the rest.
 */
public enum StepType {

    /** Reach a zone (fixed id, or a random member of a group). */
    GOTO_ZONE,
    /** Reach the player who created the dispatch call (target moves, the objective follows). */
    GOTO_PLAYER,
    /** Receive the job item, optionally only inside a zone. */
    PICKUP_ITEM,
    /** Hand the job item over inside a zone. */
    DELIVER_ITEM,
    /** Stay inside the current target for N seconds (loading, treating, repairing). */
    WAIT_TIMER,
    /** Player runs /jobs confirm while close enough to the target. */
    CONFIRM,
    /** Clear a number of work spots from a pool: rubbish, stains, thirsty plants, dead lamps. */
    CLEAR_SPOTS;

    public static StepType fromString(String raw) {
        if (raw == null) {
            return null;
        }
        for (StepType type : values()) {
            if (type.name().equalsIgnoreCase(raw.trim())) {
                return type;
            }
        }
        return null;
    }
}
