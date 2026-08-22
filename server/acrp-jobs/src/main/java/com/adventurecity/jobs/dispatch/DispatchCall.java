package com.adventurecity.jobs.dispatch;

import org.bukkit.Location;

import java.util.UUID;

/** An open request from a player to whoever is on duty for a channel ("taxi" now, "911" later). */
public final class DispatchCall {

    private final int id;
    private final UUID caller;
    private final String callerName;
    private final String channel;
    private final Location location;
    private final long createdAt = System.currentTimeMillis();

    private UUID driver;

    public DispatchCall(int id, UUID caller, String callerName, String channel, Location location) {
        this.id = id;
        this.caller = caller;
        this.callerName = callerName;
        this.channel = channel;
        this.location = location.clone();
    }

    public int id() {
        return id;
    }

    public UUID caller() {
        return caller;
    }

    public String callerName() {
        return callerName;
    }

    public String channel() {
        return channel;
    }

    public Location location() {
        return location;
    }

    public long createdAt() {
        return createdAt;
    }

    public UUID driver() {
        return driver;
    }

    public void driver(UUID driver) {
        this.driver = driver;
    }

    public boolean taken() {
        return driver != null;
    }

    public int ageSeconds() {
        return (int) ((System.currentTimeMillis() - createdAt) / 1000L);
    }
}
