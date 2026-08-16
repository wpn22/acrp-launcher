package com.adventurecity.jobs.contract;

import com.adventurecity.jobs.config.ContractDefinition;
import com.adventurecity.jobs.config.JobDefinition;
import com.adventurecity.jobs.config.StepDefinition;
import com.adventurecity.jobs.config.Zone;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Runtime state of the contract a player is currently working. */
public final class ActiveContract {

    private final UUID playerId;
    private final JobDefinition job;
    private final ContractDefinition definition;
    private final Zone[] zones;
    private final long startedAt = System.currentTimeMillis();

    private int index;
    private long stepStartedAt = System.currentTimeMillis();
    private long waitStartedAt;
    private Location lastCheckpoint;
    private Location rideStart;
    private UUID dispatchPlayer;
    private ItemStack carriedItem;
    private int carriedAmount;
    private boolean suspicious;

    public ActiveContract(UUID playerId, JobDefinition job, ContractDefinition definition, Zone[] zones) {
        this.playerId = playerId;
        this.job = job;
        this.definition = definition;
        this.zones = zones;
    }

    public UUID playerId() {
        return playerId;
    }

    public JobDefinition job() {
        return job;
    }

    public ContractDefinition definition() {
        return definition;
    }

    public int index() {
        return index;
    }

    public StepDefinition step() {
        return definition.steps().get(index);
    }

    public Zone zone() {
        return zones[index];
    }

    public int stepCount() {
        return definition.steps().size();
    }

    public boolean finished() {
        return index >= definition.steps().size();
    }

    /** Advances to the next step and resets the per-step timers. */
    public void advance(Location checkpoint) {
        index++;
        stepStartedAt = System.currentTimeMillis();
        waitStartedAt = 0L;
        if (checkpoint != null) {
            lastCheckpoint = checkpoint.clone();
        }
    }

    public long startedAt() {
        return startedAt;
    }

    public int elapsedSeconds() {
        return (int) ((System.currentTimeMillis() - startedAt) / 1000L);
    }

    public long stepStartedAt() {
        return stepStartedAt;
    }

    public int stepElapsedSeconds() {
        return (int) ((System.currentTimeMillis() - stepStartedAt) / 1000L);
    }

    public long waitStartedAt() {
        return waitStartedAt;
    }

    public void waitStartedAt(long waitStartedAt) {
        this.waitStartedAt = waitStartedAt;
    }

    public Location lastCheckpoint() {
        return lastCheckpoint;
    }

    public void lastCheckpoint(Location location) {
        this.lastCheckpoint = location == null ? null : location.clone();
    }

    /** Where a distance-paid ride began (taxi pickup point). */
    public Location rideStart() {
        return rideStart;
    }

    public void rideStart(Location location) {
        this.rideStart = location == null ? null : location.clone();
    }

    /** The passenger / caller this contract was created for, or null for solo contracts. */
    public UUID dispatchPlayer() {
        return dispatchPlayer;
    }

    public void dispatchPlayer(UUID dispatchPlayer) {
        this.dispatchPlayer = dispatchPlayer;
    }

    public ItemStack carriedItem() {
        return carriedItem;
    }

    public int carriedAmount() {
        return carriedAmount;
    }

    public void carry(ItemStack item, int amount) {
        this.carriedItem = item;
        this.carriedAmount = amount;
    }

    public void consumeCarried(int amount) {
        this.carriedAmount = Math.max(0, this.carriedAmount - amount);
    }

    /** Set when a movement check found impossible speed - completion pays nothing. */
    public boolean suspicious() {
        return suspicious;
    }

    public void flagSuspicious() {
        this.suspicious = true;
    }
}
