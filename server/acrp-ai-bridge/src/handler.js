'use strict';

const { ReplyCache } = require('./cache');
const { Limiter } = require('./limits');
const { DialogueLog } = require('./log');
const { ClaudeClient } = require('./claude');
const { validate } = require('./intents');
const { estimateCost } = require('./config');

/**
 * The whole request pipeline, kept free of HTTP so it can be tested directly.
 *
 * Every failure path ends the same way: `ok: false`, and the plugin falls back to the
 * NPC's written line. The game never breaks because the model is slow, refused,
 * out of budget, or unreachable.
 */
class DialogueHandler {

    constructor(config) {
        this.config = config;
        this.cache = new ReplyCache(config.cacheTtlSeconds, config.cacheMaxEntries);
        this.limiter = new Limiter(config.playerMessagesPerMinute, config.dailyBudgetUsd);
        this.log = new DialogueLog(config.logFile, config.logEnabled);
        this.claude = new ClaudeClient(config);
        this.served = { model: 0, cache: 0, mock: 0, fallback: 0 };
    }

    async handle(request) {
        const problem = validateRequest(request);
        if (problem) {
            return { ok: false, reason: problem, source: 'invalid' };
        }

        if (!this.limiter.allowPlayer(request.playerId)) {
            return this.fallback(request, 'rate_limited');
        }

        const signature = contextSignature(request);
        const cacheKey = ReplyCache.key(request.npcId, request.message, signature);
        const cached = this.cache.get(cacheKey);
        if (cached) {
            this.served.cache++;
            this.log.write({
                npc: request.npcId, player: request.playerName,
                message: request.message, reply: cached.reply,
                intent: cached.intent, source: 'cache'
            });
            return Object.assign({ ok: true, source: 'cache' }, cached);
        }

        if (!this.limiter.allowSpend()) {
            return this.fallback(request, 'daily_budget_reached');
        }

        let answer;
        try {
            answer = await this.claude.ask(request);
        } catch (err) {
            console.error(`[acrp-ai] Model call failed: ${err.message}`);
            return this.fallback(request, 'model_error');
        }

        if (answer.usage) {
            this.limiter.recordSpend(estimateCost(this.config.model, answer.usage));
        }
        if (answer.refused) {
            return this.fallback(request, 'refused');
        }
        if (!answer.raw) {
            return this.fallback(request, 'unparseable');
        }

        const checked = validate(
            answer.raw,
            request.allowedIntents,
            request.allowedTargets,
            this.config.maxReplyChars
        );
        if (!checked.valid) {
            return this.fallback(request, 'empty_reply');
        }

        const payload = { reply: checked.reply, intent: checked.intent, target: checked.target };
        this.cache.set(cacheKey, payload);

        const source = answer.mock ? 'mock' : 'model';
        this.served[source]++;
        this.log.write({
            npc: request.npcId, player: request.playerName,
            message: request.message, reply: checked.reply,
            intent: checked.intent, target: checked.target,
            downgraded: checked.downgraded, source
        });

        return Object.assign({ ok: true, source, downgraded: checked.downgraded }, payload);
    }

    fallback(request, reason) {
        this.served.fallback++;
        this.log.write({
            npc: request.npcId, player: request.playerName,
            message: request.message, source: 'fallback', reason
        });
        return { ok: false, reason, source: 'fallback' };
    }

    stats() {
        return {
            mock: this.config.mock,
            model: this.config.model,
            served: this.served,
            cache: this.cache.stats(),
            limits: this.limiter.stats()
        };
    }
}

function validateRequest(request) {
    if (!request || typeof request !== 'object') {
        return 'bad_body';
    }
    if (!request.npcId || typeof request.npcId !== 'string') {
        return 'missing_npcId';
    }
    if (!request.playerId || typeof request.playerId !== 'string') {
        return 'missing_playerId';
    }
    const message = typeof request.message === 'string' ? request.message.trim() : '';
    if (!message) {
        return 'empty_message';
    }
    if (message.length > 500) {
        return 'message_too_long';
    }
    return null;
}

/** Cache entries are scoped to the player's situation, not to the player. */
function contextSignature(request) {
    const context = request.context || {};
    return Object.keys(context).sort().map(key => `${key}=${context[key]}`).join('|');
}

module.exports = { DialogueHandler, validateRequest, contextSignature };
