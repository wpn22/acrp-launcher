package com.adventurecity.jobs.storage;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** In-memory state of one player. Loaded on join, flushed asynchronously, saved on quit. */
public final class PlayerData {

    private final UUID uuid;
    private String name;
    private long balance;
    private String currentJob;
    private boolean onDuty;
    private final Map<String, JobProgress> jobs = new HashMap<String, JobProgress>();

    /** Earnings counted against the daily cap, plus the day they belong to (epoch day, UTC). */
    private long earnedToday;
    private long earnedDay;

    private boolean dirty;

    /** Runtime only - minutes accumulated toward the next payroll tick. Never persisted. */
    private int dutyMinutes;

    public PlayerData(UUID uuid, String name, long balance, String currentJob, boolean onDuty) {
        this.uuid = uuid;
        this.name = name;
        this.balance = balance;
        this.currentJob = currentJob;
        this.onDuty = onDuty;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        if (!name.equals(this.name)) {
            this.name = name;
            this.dirty = true;
        }
    }

    public long balance() {
        return balance;
    }

    public void balance(long balance) {
        this.balance = Math.max(0L, balance);
        this.dirty = true;
    }

    public String currentJob() {
        return currentJob;
    }

    public void currentJob(String currentJob) {
        this.currentJob = currentJob;
        this.dirty = true;
    }

    public boolean onDuty() {
        return onDuty;
    }

    public void onDuty(boolean onDuty) {
        this.onDuty = onDuty;
        this.dutyMinutes = 0;
        this.dirty = true;
    }

    public JobProgress job(String jobId) {
        return jobId == null ? null : jobs.get(jobId);
    }

    public void addJob(JobProgress progress) {
        jobs.put(progress.jobId(), progress);
        this.dirty = true;
    }

    public void removeJob(String jobId) {
        if (jobs.remove(jobId) != null) {
            this.dirty = true;
        }
    }

    public Collection<JobProgress> jobs() {
        return jobs.values();
    }

    public int jobCount() {
        return jobs.size();
    }

    public boolean hasJob(String jobId) {
        return jobs.containsKey(jobId);
    }

    /** Current progress in the job the player is actively working, or null. */
    public JobProgress currentProgress() {
        return job(currentJob);
    }

    public long earnedToday(long today) {
        return earnedDay == today ? earnedToday : 0L;
    }

    public void addEarnedToday(long today, long amount) {
        if (earnedDay != today) {
            earnedDay = today;
            earnedToday = 0L;
        }
        earnedToday += amount;
    }

    public void initEarnedToday(long today, long amount) {
        this.earnedDay = today;
        this.earnedToday = amount;
    }

    public int dutyMinutes() {
        return dutyMinutes;
    }

    public void dutyMinutes(int dutyMinutes) {
        this.dutyMinutes = dutyMinutes;
    }

    public boolean dirty() {
        return dirty;
    }

    public void dirty(boolean dirty) {
        this.dirty = dirty;
    }
}
