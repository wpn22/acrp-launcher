package com.adventurecity.jobs.route;

import org.bukkit.entity.LivingEntity;

import java.util.UUID;

/**
 * One city dweller on a route.
 *
 * <p>A walker exists as numbers whether or not it exists as an entity. {@link #progress} keeps
 * advancing when nobody is nearby, so when the walker is spawned again it appears where it should
 * have got to - not back at the corner it started from.</p>
 */
public final class RouteWalker {

    private final Route route;
    private final int index;

    private double progress;
    private double speedFactor = 1.0D;
    private int segment;
    private long pauseUntil;
    private String name = "";

    private LivingEntity entity;
    private UUID talkingWith;

    public RouteWalker(Route route, int index) {
        this.route = route;
        this.index = index;
    }

    public Route route() {
        return route;
    }

    /** Position of this walker in its route's population - walker 0 carries the lead persona. */
    public int index() {
        return index;
    }

    /** The npcs.yml persona this particular walker speaks as, or empty for a silent extra. */
    public String persona() {
        return route.personaFor(index);
    }

    /** Distance walked in blocks since the walker was created. Folded into the path by RoutePath. */
    public double progress() {
        return progress;
    }

    public void progress(double progress) {
        this.progress = progress;
    }

    /** A small per-walker multiplier so a group does not march in lockstep. */
    public double speedFactor() {
        return speedFactor;
    }

    public void speedFactor(double speedFactor) {
        this.speedFactor = speedFactor;
    }

    /** Leg of the polyline the walker was on last tick - a change means a corner was rounded. */
    public int segment() {
        return segment;
    }

    public void segment(int segment) {
        this.segment = segment;
    }

    public long pauseUntil() {
        return pauseUntil;
    }

    public void pauseUntil(long pauseUntil) {
        this.pauseUntil = pauseUntil;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name == null ? "" : name;
    }

    /** Null whenever the walker is out of range and exists only as numbers. */
    public LivingEntity entity() {
        return entity;
    }

    public void entity(LivingEntity entity) {
        this.entity = entity;
    }

    public boolean spawned() {
        return entity != null && entity.isValid();
    }

    /** Set while a player is in conversation with this walker, so it stands still and faces them. */
    public UUID talkingWith() {
        return talkingWith;
    }

    public void talkingWith(UUID talkingWith) {
        this.talkingWith = talkingWith;
    }

    public boolean frozen(long now) {
        return talkingWith != null || now < pauseUntil;
    }
}
