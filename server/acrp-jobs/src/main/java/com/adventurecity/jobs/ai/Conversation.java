package com.adventurecity.jobs.ai;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One player's live conversation with one NPC. */
public final class Conversation {

    private final UUID playerId;
    private final NpcPersona npc;
    private final Location npcLocation;
    private final List<String[]> history = new ArrayList<String[]>();

    private long lastActivity = System.currentTimeMillis();
    private boolean waitingForReply;

    public Conversation(UUID playerId, NpcPersona npc, Location npcLocation) {
        this.playerId = playerId;
        this.npc = npc;
        this.npcLocation = npcLocation == null ? null : npcLocation.clone();
    }

    public UUID playerId() {
        return playerId;
    }

    public NpcPersona npc() {
        return npc;
    }

    /** Where the NPC stands, used for the walk-away check. Null when started by command. */
    public Location npcLocation() {
        return npcLocation;
    }

    /** Recent turns as {role, text}, oldest first. Trimmed so prompts stay small. */
    public List<String[]> history() {
        return history;
    }

    public void remember(String role, String text) {
        history.add(new String[] { role, text });
        while (history.size() > 6) {
            history.remove(0);
        }
    }

    public void touch() {
        lastActivity = System.currentTimeMillis();
    }

    public int idleSeconds() {
        return (int) ((System.currentTimeMillis() - lastActivity) / 1000L);
    }

    /** True while a bridge request is in flight - stops a player queueing up calls. */
    public boolean waitingForReply() {
        return waitingForReply;
    }

    public void waitingForReply(boolean waiting) {
        this.waitingForReply = waiting;
    }
}
