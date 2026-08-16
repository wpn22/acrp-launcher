package com.adventurecity.jobs.storage;

/** A player's standing in one job. */
public final class JobProgress {

    private final String jobId;
    private int gradeLevel;
    private long xp;
    private final long hiredAt;

    public JobProgress(String jobId, int gradeLevel, long xp, long hiredAt) {
        this.jobId = jobId;
        this.gradeLevel = gradeLevel;
        this.xp = xp;
        this.hiredAt = hiredAt;
    }

    public String jobId() {
        return jobId;
    }

    public int gradeLevel() {
        return gradeLevel;
    }

    public void gradeLevel(int gradeLevel) {
        this.gradeLevel = gradeLevel;
    }

    public long xp() {
        return xp;
    }

    public void addXp(long amount) {
        this.xp += amount;
    }

    public long hiredAt() {
        return hiredAt;
    }
}
