package com.adventurecity.jobs.storage;

/**
 * Every movement of AC is written to the audit log under one of these types. Keeping them in one
 * place is what makes "where did this money come from?" answerable months later.
 */
public final class TxType {

    /** Contract payout (counts toward the daily cap). */
    public static final String CONTRACT = "contract";
    /** Duty salary (counts toward the daily cap). */
    public static final String SALARY = "salary";
    /** Taxi fare received by the driver (counts toward the daily cap). */
    public static final String FARE = "fare";

    /** Taxi fare paid by the passenger. */
    public static final String FARE_PAID = "fare_paid";
    public static final String TRANSFER_IN = "transfer_in";
    public static final String TRANSFER_OUT = "transfer_out";
    public static final String ADMIN_GIVE = "admin_give";
    public static final String ADMIN_TAKE = "admin_take";
    public static final String ADMIN_SET = "admin_set";
    public static final String FIRST_JOIN = "first_join";

    /** Types that count against the daily earning cap. */
    public static final String[] EARNING_TYPES = { CONTRACT, SALARY, FARE };

    private TxType() {
    }
}
