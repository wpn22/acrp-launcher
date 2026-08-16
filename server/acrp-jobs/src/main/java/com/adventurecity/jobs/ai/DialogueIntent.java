package com.adventurecity.jobs.ai;

/**
 * The only actions an NPC can take. The model picks one of these; it never invents an
 * action, and {@link DialogueService} checks the choice against what this NPC is
 * actually allowed to offer before anything happens in game.
 */
public enum DialogueIntent {

    /** Talk only. The safe default for anything unrecognised. */
    CHAT,
    /** Offer employment in a job this NPC is configured to hire for. */
    OFFER_JOB,
    /** Offer a contract from the player's current job. */
    OFFER_CONTRACT,
    /** Point the player at a zone this NPC knows. */
    DIRECT_TO_ZONE,
    /** Politely refuse - abuse, off-topic, or out of scope. */
    DECLINE;

    public static DialogueIntent fromString(String raw) {
        if (raw == null) {
            return CHAT;
        }
        for (DialogueIntent intent : values()) {
            if (intent.name().equalsIgnoreCase(raw.trim())) {
                return intent;
            }
        }
        return CHAT;
    }

    /** True when the intent needs a target id to mean anything. */
    public boolean needsTarget() {
        return this == OFFER_JOB || this == OFFER_CONTRACT || this == DIRECT_TO_ZONE;
    }
}
