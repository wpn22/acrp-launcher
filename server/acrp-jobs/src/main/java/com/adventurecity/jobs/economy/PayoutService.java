package com.adventurecity.jobs.economy;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.storage.PlayerData;
import com.adventurecity.jobs.storage.PlayerDataManager;

/**
 * Every AC that enters the economy from a job passes through here: tax first, daily cap second,
 * deposit third. Keeping it in one place is what makes the economy tunable from config.yml instead
 * of from a dozen scattered payout sites.
 */
public final class PayoutService {

    private final ACRPJobsPlugin plugin;

    public PayoutService(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Applies tax and the daily cap, deposits what is left, and reports the breakdown. */
    public Payout pay(PlayerData data, long gross, double taxPercent, String type, String reason) {
        if (gross <= 0L) {
            return new Payout(0L, 0L, 0L, false);
        }

        long tax = Math.round(gross * (taxPercent / 100.0D));
        long net = gross - tax;
        boolean capReached = false;

        long cap = plugin.settings().dailyEarningCap;
        if (cap > 0L) {
            long today = PlayerDataManager.epochDay();
            long remaining = Math.max(0L, cap - data.earnedToday(today));
            if (net >= remaining) {
                net = remaining;
                capReached = true;
            }
            if (net > 0L) {
                data.addEarnedToday(today, net);
            }
        }

        if (net > 0L) {
            plugin.economy().deposit(data.uuid(), net, type, reason);
        }
        return new Payout(gross, tax, net, capReached);
    }
}
