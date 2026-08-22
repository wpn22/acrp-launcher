'use strict';

/**
 * Two independent brakes:
 *  - a per-player message rate, so one bored player cannot burn the budget, and
 *  - a server-wide daily spend ceiling, after which every NPC quietly falls back
 *    to its written lines instead of costing money.
 */
class Limiter {

    constructor(messagesPerMinute, dailyBudgetUsd) {
        this.messagesPerMinute = Math.max(1, messagesPerMinute);
        this.dailyBudgetUsd = Math.max(0, dailyBudgetUsd);
        this.players = new Map();
        this.spentUsd = 0;
        this.day = today();
        this.blockedByRate = 0;
        this.blockedByBudget = 0;
    }

    /** @return true when the player may send another message right now */
    allowPlayer(playerId) {
        const now = Date.now();
        const windowStart = now - 60000;
        const stamps = (this.players.get(playerId) || []).filter(t => t > windowStart);

        if (stamps.length >= this.messagesPerMinute) {
            this.players.set(playerId, stamps);
            this.blockedByRate++;
            return false;
        }
        stamps.push(now);
        this.players.set(playerId, stamps);
        return true;
    }

    /** @return true while there is budget left for a paid call today */
    allowSpend() {
        this.rolloverIfNeeded();
        if (this.dailyBudgetUsd === 0) {
            return true;
        }
        if (this.spentUsd >= this.dailyBudgetUsd) {
            this.blockedByBudget++;
            return false;
        }
        return true;
    }

    recordSpend(usd) {
        this.rolloverIfNeeded();
        this.spentUsd += (usd || 0);
    }

    rolloverIfNeeded() {
        const current = today();
        if (current !== this.day) {
            this.day = current;
            this.spentUsd = 0;
            this.players.clear();
        }
    }

    stats() {
        this.rolloverIfNeeded();
        return {
            day: this.day,
            spentUsd: Number(this.spentUsd.toFixed(4)),
            dailyBudgetUsd: this.dailyBudgetUsd,
            budgetRemainingUsd: this.dailyBudgetUsd === 0
                ? null
                : Number(Math.max(0, this.dailyBudgetUsd - this.spentUsd).toFixed(4)),
            blockedByRate: this.blockedByRate,
            blockedByBudget: this.blockedByBudget
        };
    }
}

function today() {
    return new Date().toISOString().slice(0, 10);
}

module.exports = { Limiter };
