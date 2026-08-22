'use strict';

const fs = require('fs');
const path = require('path');

const DEFAULTS = {
    // Bind to loopback only. The bridge must never be reachable from the internet -
    // it holds the API key and would otherwise be an open, billable endpoint.
    host: '127.0.0.1',
    port: 8787,

    // Shared secret the plugin sends as `Authorization: Bearer <token>`.
    token: '',

    // Claude Opus 5 is the default. claude-haiku-4-5 costs roughly a fifth as much
    // per reply and is the usual choice for high-volume NPC chatter - switch here.
    model: 'claude-opus-5',
    // Short, latency-sensitive replies: low effort keeps thinking (and cost) small.
    effort: 'low',
    maxTokens: 4096,
    // Replies longer than this are trimmed before they reach the player.
    maxReplyChars: 400,

    // Identical question to the same NPC reuses the previous answer for this long.
    cacheTtlSeconds: 900,
    cacheMaxEntries: 2000,

    // Per-player throttle.
    playerMessagesPerMinute: 5,
    // Hard stop for the whole server, in US dollars of estimated spend per day.
    dailyBudgetUsd: 5.0,

    // Every conversation line is written here for moderation.
    logFile: 'logs/dialogue.log',
    logEnabled: true
};

const PRICES_PER_MTOK = {
    'claude-opus-5': { input: 5.0, output: 25.0 },
    'claude-fable-5': { input: 10.0, output: 50.0 },
    'claude-sonnet-5': { input: 3.0, output: 15.0 },
    'claude-haiku-4-5': { input: 1.0, output: 5.0 }
};

function load() {
    const config = Object.assign({}, DEFAULTS);

    const file = process.env.ACRP_AI_CONFIG || path.join(__dirname, '..', 'config.json');
    if (fs.existsSync(file)) {
        try {
            Object.assign(config, JSON.parse(fs.readFileSync(file, 'utf8')));
        } catch (err) {
            console.error(`[acrp-ai] Could not read ${file}: ${err.message}`);
        }
    }

    // Environment always wins, so secrets can stay out of the config file.
    if (process.env.ACRP_AI_PORT) config.port = Number(process.env.ACRP_AI_PORT);
    if (process.env.ACRP_AI_HOST) config.host = process.env.ACRP_AI_HOST;
    if (process.env.ACRP_AI_TOKEN) config.token = process.env.ACRP_AI_TOKEN;
    if (process.env.ACRP_AI_MODEL) config.model = process.env.ACRP_AI_MODEL;
    if (process.env.ACRP_AI_DAILY_BUDGET) config.dailyBudgetUsd = Number(process.env.ACRP_AI_DAILY_BUDGET);

    // No key means mock mode: the whole pipeline runs, the model call is stubbed.
    config.mock = !process.env.ANTHROPIC_API_KEY || process.env.ACRP_AI_MOCK === '1';

    return config;
}

/** Rough spend estimate from a usage object, in USD. */
function estimateCost(model, usage) {
    const price = PRICES_PER_MTOK[model];
    if (!price || !usage) {
        return 0;
    }
    const input = usage.input_tokens || 0;
    const output = usage.output_tokens || 0;
    const cacheRead = usage.cache_read_input_tokens || 0;
    const cacheWrite = usage.cache_creation_input_tokens || 0;

    return (
        (input * price.input) +
        (cacheRead * price.input * 0.1) +
        (cacheWrite * price.input * 1.25) +
        (output * price.output)
    ) / 1000000;
}

module.exports = { load, estimateCost, PRICES_PER_MTOK, DEFAULTS };
