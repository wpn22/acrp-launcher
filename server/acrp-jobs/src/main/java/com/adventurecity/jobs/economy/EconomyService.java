package com.adventurecity.jobs.economy;

import com.adventurecity.jobs.ACRPJobsPlugin;
import com.adventurecity.jobs.storage.PlayerData;
import com.adventurecity.jobs.storage.PlayerDataManager;
import com.adventurecity.jobs.util.Msg;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * The single source of truth for AC.
 *
 * <p>Job payouts, player transfers and store deliveries all go through here, so the balance a
 * player sees and the balance the store tops up are always the same number - and every movement
 * lands in the audit log.</p>
 */
public final class EconomyService {

    private final ACRPJobsPlugin plugin;

    public EconomyService(ACRPJobsPlugin plugin) {
        this.plugin = plugin;
    }

    private PlayerDataManager players() {
        return plugin.players();
    }

    public long balance(UUID uuid) {
        PlayerData data = players().get(uuid);
        return data == null ? 0L : data.balance();
    }

    public boolean has(UUID uuid, long amount) {
        return balance(uuid) >= amount;
    }

    /** Adds money to a loaded player; falls back to a direct SQL update when they are not cached. */
    public void deposit(UUID uuid, long amount, String type, String reason) {
        if (amount <= 0L) {
            return;
        }
        PlayerData data = players().get(uuid);
        if (data == null) {
            adjustOffline(uuid, null, amount, false, type, reason, null);
            return;
        }
        final long balanceAfter = data.balance() + amount;
        data.balance(balanceAfter);
        final UUID target = uuid;
        final long delta = amount;
        players().submit(() -> players().storage().logTransaction(target, type, delta, balanceAfter, reason));
    }

    /** @return false when the player cannot afford it (nothing is changed in that case). */
    public boolean withdraw(UUID uuid, long amount, String type, String reason) {
        if (amount <= 0L) {
            return true;
        }
        PlayerData data = players().get(uuid);
        if (data == null || data.balance() < amount) {
            return false;
        }
        final long balanceAfter = data.balance() - amount;
        data.balance(balanceAfter);
        final UUID target = uuid;
        final long delta = amount;
        players().submit(() -> players().storage().logTransaction(target, type, -delta, balanceAfter, reason));
        return true;
    }

    /**
     * Works for offline players too - this is what the web store calls through
     * {@code /ac give <player> <amount> store}.
     *
     * @param absolute true to set the balance, false to add {@code amount} to it
     * @param callback receives the new balance on the server thread, or -1 on failure
     */
    public void adjustOffline(final UUID uuid, final String name, final long amount, final boolean absolute,
                              final String type, final String reason, final Consumer<Long> callback) {
        // Keep a loaded player's cached balance in step, otherwise their next save overwrites the change.
        PlayerData data = players().get(uuid);
        if (data != null) {
            long next = absolute ? Math.max(0L, amount) : Math.max(0L, data.balance() + amount);
            long delta = next - data.balance();
            data.balance(next);
            final long balanceAfter = next;
            players().submit(() -> players().storage().logTransaction(uuid, type, delta, balanceAfter, reason));
            if (callback != null) {
                callback.accept(Long.valueOf(next));
            }
            return;
        }
        players().query(() -> Long.valueOf(players().storage().offlineAdjust(uuid, name, amount, absolute, type, reason)),
                callback);
    }

    /** "12,500 AC" */
    public String format(long amount) {
        return Msg.number(amount) + " " + plugin.settings().currencySymbol;
    }

    public String symbol() {
        return plugin.settings().currencySymbol;
    }
}
