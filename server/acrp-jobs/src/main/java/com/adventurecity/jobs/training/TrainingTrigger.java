package com.adventurecity.jobs.training;

/**
 * What has to actually happen before a lesson counts as learned.
 *
 * <p>Training never advances on a click-through. The supervisor waits until the worker has really
 * clocked in, really taken a round, really cleared something - so the explanation is tied to the
 * work rather than being a wall of text somebody skips.</p>
 */
public enum TrainingTrigger {

    /** Talk to the supervisor again. Used for a plain "any questions?" beat. */
    TALK,
    /** The worker clocked in with /duty. */
    DUTY_START,
    /** The worker took on a contract. */
    CONTRACT_START,
    /** The worker cleared a work spot: a bag, a stain, a plant, a lamp. */
    SPOT_CLEARED,
    /** The worker finished any step of the contract. */
    STEP_DONE,
    /** The worker finished the whole contract and got paid. */
    CONTRACT_DONE;

    public static TrainingTrigger fromString(String raw) {
        if (raw == null) {
            return null;
        }
        for (TrainingTrigger trigger : values()) {
            if (trigger.name().equalsIgnoreCase(raw.trim())) {
                return trigger;
            }
        }
        return null;
    }
}
