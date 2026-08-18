package com.adventurecity.jobs.storage;

/** A player's standing in one job. */
public final class JobProgress {

    private final String jobId;
    private int gradeLevel;
    private long xp;
    private final long hiredAt;
    private int lesson;
    private boolean metTrainer;

    public JobProgress(String jobId, int gradeLevel, long xp, long hiredAt) {
        this(jobId, gradeLevel, xp, hiredAt, 0, false);
    }

    public JobProgress(String jobId, int gradeLevel, long xp, long hiredAt, int lesson,
                       boolean metTrainer) {
        this.jobId = jobId;
        this.gradeLevel = gradeLevel;
        this.xp = xp;
        this.hiredAt = hiredAt;
        this.lesson = lesson;
        this.metTrainer = metTrainer;
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

    /** Index of the training lesson this player is waiting on. Equal to the lesson count = trained. */
    public int lesson() {
        return lesson;
    }

    public void lesson(int lesson) {
        this.lesson = Math.max(0, lesson);
    }

    /** True once the newcomer has actually found the supervisor in the street and been taught. */
    public boolean metTrainer() {
        return metTrainer;
    }

    public void metTrainer(boolean metTrainer) {
        this.metTrainer = metTrainer;
    }
}
