'use strict';

/**
 * Dependency-free test runner. Everything here runs in mock mode, so the whole
 * pipeline is exercised without an API key and without spending anything.
 */

const assert = require('assert');
const http = require('http');

const { validate, buildSchema } = require('../src/intents');
const { ReplyCache, normalize } = require('../src/cache');
const { Limiter } = require('../src/limits');
const { DialogueHandler } = require('../src/handler');
const { createServer } = require('../src/server');
const { estimateCost } = require('../src/config');

const tests = [];
function test(name, fn) {
    tests.push({ name, fn });
}

function baseConfig(overrides) {
    return Object.assign({
        host: '127.0.0.1',
        port: 0,
        token: 'test-token',
        model: 'claude-opus-5',
        effort: 'low',
        maxTokens: 4096,
        maxReplyChars: 400,
        cacheTtlSeconds: 900,
        cacheMaxEntries: 100,
        playerMessagesPerMinute: 5,
        dailyBudgetUsd: 5,
        logFile: null,
        logEnabled: false,
        mock: true
    }, overrides || {});
}

function baseRequest(overrides) {
    return Object.assign({
        npcId: 'job_officer',
        npcName: 'أبو سعد',
        persona: 'موظف مركز التوظيف',
        playerId: '11111111-1111-1111-1111-111111111111',
        playerName: 'WPN',
        message: 'ابي شغل',
        allowedIntents: ['CHAT', 'OFFER_JOB', 'DIRECT_TO_ZONE', 'DECLINE'],
        allowedTargets: { OFFER_JOB: ['delivery', 'taxi'], DIRECT_TO_ZONE: ['delivery_depot'] },
        context: { job: 'لا يوجد' },
        history: []
    }, overrides || {});
}

// ---------------------------------------------------------------- intent guard rails

test('valid intent with a permitted target is accepted', () => {
    const result = validate(
        { reply: 'عندي لك توصيل', intent: 'OFFER_JOB', target: 'delivery' },
        ['CHAT', 'OFFER_JOB'], { OFFER_JOB: ['delivery'] }, 400);
    assert.strictEqual(result.intent, 'OFFER_JOB');
    assert.strictEqual(result.target, 'delivery');
    assert.strictEqual(result.downgraded, false);
});

test('an intent this NPC may not use is downgraded to CHAT', () => {
    const result = validate(
        { reply: 'خلاص وظفتك شرطي', intent: 'OFFER_JOB', target: 'police' },
        ['CHAT'], {}, 400);
    assert.strictEqual(result.intent, 'CHAT');
    assert.strictEqual(result.downgraded, true);
});

test('a job the NPC does not offer is downgraded to CHAT', () => {
    const result = validate(
        { reply: 'تعال اشتغل شرطي', intent: 'OFFER_JOB', target: 'police' },
        ['CHAT', 'OFFER_JOB'], { OFFER_JOB: ['delivery', 'taxi'] }, 400);
    assert.strictEqual(result.intent, 'CHAT', 'invented job must not reach the player');
    assert.strictEqual(result.target, '');
    assert.strictEqual(result.downgraded, true);
});

test('an invented intent name is downgraded to CHAT', () => {
    const result = validate(
        { reply: 'اعطيتك مليون', intent: 'GIVE_MONEY', target: '1000000' },
        ['CHAT', 'OFFER_JOB'], { OFFER_JOB: ['delivery'] }, 400);
    assert.strictEqual(result.intent, 'CHAT');
    assert.strictEqual(result.target, '');
});

test('an action intent with no target is downgraded to CHAT', () => {
    const result = validate(
        { reply: 'روح هناك', intent: 'DIRECT_TO_ZONE', target: '' },
        ['CHAT', 'DIRECT_TO_ZONE'], { DIRECT_TO_ZONE: ['delivery_depot'] }, 400);
    assert.strictEqual(result.intent, 'CHAT');
});

test('empty replies are rejected outright', () => {
    const result = validate({ reply: '   ', intent: 'CHAT', target: '' }, ['CHAT'], {}, 400);
    assert.strictEqual(result.valid, false);
});

test('over-long replies are trimmed', () => {
    const result = validate({ reply: 'ا'.repeat(900), intent: 'CHAT', target: '' }, ['CHAT'], {}, 400);
    assert.strictEqual(result.reply.length, 400);
});

test('schema enum only ever contains intents this NPC may use', () => {
    const schema = buildSchema(['CHAT', 'DECLINE']);
    assert.deepStrictEqual(schema.properties.intent.enum, ['CHAT', 'DECLINE']);
    assert.strictEqual(schema.additionalProperties, false);
});

// ---------------------------------------------------------------- cache

test('arabic normalisation collapses trivial variations', () => {
    assert.strictEqual(normalize('  أبي شغل؟ '), normalize('ابي شغل'));
    assert.strictEqual(normalize('وين المستودع'), normalize('وين المستودع  '));
});

test('cache returns a stored reply and counts the hit', () => {
    const cache = new ReplyCache(900, 10);
    const key = ReplyCache.key('npc', 'ابي شغل', 'job=none');
    assert.strictEqual(cache.get(key), null);
    cache.set(key, { reply: 'اهلا', intent: 'CHAT', target: '' });
    assert.strictEqual(cache.get(key).reply, 'اهلا');
    assert.strictEqual(cache.stats().hits, 1);
});

test('cache key ignores the player but respects their situation', () => {
    const a = ReplyCache.key('npc', 'ابي شغل', 'job=none');
    const b = ReplyCache.key('npc', 'أبي شغل', 'job=none');
    const c = ReplyCache.key('npc', 'ابي شغل', 'job=delivery');
    assert.strictEqual(a, b);
    assert.notStrictEqual(a, c);
});

test('expired entries are dropped', () => {
    const cache = new ReplyCache(0.001, 10);
    const key = ReplyCache.key('npc', 'x', '');
    cache.set(key, { reply: 'a' });
    return new Promise(resolve => setTimeout(() => {
        assert.strictEqual(cache.get(key), null);
        resolve();
    }, 20));
});

test('cache evicts the oldest entry when full', () => {
    const cache = new ReplyCache(900, 2);
    cache.set('a', { reply: '1' });
    cache.set('b', { reply: '2' });
    cache.set('c', { reply: '3' });
    assert.strictEqual(cache.stats().size, 2);
    assert.strictEqual(cache.get('a'), null);
});

// ---------------------------------------------------------------- limits

test('per-player rate limit blocks the sixth message in a minute', () => {
    const limiter = new Limiter(5, 5);
    for (let i = 0; i < 5; i++) {
        assert.strictEqual(limiter.allowPlayer('p1'), true, `message ${i + 1} should pass`);
    }
    assert.strictEqual(limiter.allowPlayer('p1'), false);
    assert.strictEqual(limiter.allowPlayer('p2'), true, 'other players are unaffected');
});

test('daily budget stops spending once reached', () => {
    const limiter = new Limiter(100, 1.0);
    assert.strictEqual(limiter.allowSpend(), true);
    limiter.recordSpend(0.99);
    assert.strictEqual(limiter.allowSpend(), true);
    limiter.recordSpend(0.02);
    assert.strictEqual(limiter.allowSpend(), false);
    assert.strictEqual(limiter.stats().budgetRemainingUsd, 0);
});

test('a zero budget means unlimited', () => {
    const limiter = new Limiter(100, 0);
    limiter.recordSpend(1000);
    assert.strictEqual(limiter.allowSpend(), true);
});

test('cost estimate matches published per-token pricing', () => {
    // 1M input + 1M output on Opus 5 = $5 + $25.
    const cost = estimateCost('claude-opus-5', { input_tokens: 1000000, output_tokens: 1000000 });
    assert.strictEqual(Math.round(cost), 30);
    // Cache reads bill at a tenth of the input rate.
    const cached = estimateCost('claude-opus-5', { input_tokens: 0, cache_read_input_tokens: 1000000, output_tokens: 0 });
    assert.strictEqual(Math.round(cached * 100) / 100, 0.5);
});

// ---------------------------------------------------------------- handler pipeline

test('handler answers, then serves the same question from cache', async () => {
    const handler = new DialogueHandler(baseConfig());
    const first = await handler.handle(baseRequest());
    assert.strictEqual(first.ok, true);
    assert.strictEqual(first.source, 'mock');
    assert.strictEqual(first.intent, 'OFFER_JOB');
    assert.strictEqual(first.target, 'delivery');

    const second = await handler.handle(baseRequest());
    assert.strictEqual(second.source, 'cache');
    assert.strictEqual(second.reply, first.reply);
});

test('handler rejects malformed requests without calling the model', async () => {
    const handler = new DialogueHandler(baseConfig());
    assert.strictEqual((await handler.handle({})).reason, 'missing_npcId');
    assert.strictEqual((await handler.handle(baseRequest({ message: '  ' }))).reason, 'empty_message');
    assert.strictEqual((await handler.handle(baseRequest({ message: 'x'.repeat(501) }))).reason, 'message_too_long');
});

test('handler falls back once the player is rate limited', async () => {
    const handler = new DialogueHandler(baseConfig({ playerMessagesPerMinute: 2, cacheTtlSeconds: 0 }));
    await handler.handle(baseRequest({ message: 'وحدة' }));
    await handler.handle(baseRequest({ message: 'ثنتين' }));
    const blocked = await handler.handle(baseRequest({ message: 'ثلاث' }));
    assert.strictEqual(blocked.ok, false);
    assert.strictEqual(blocked.reason, 'rate_limited');
    assert.strictEqual(blocked.source, 'fallback');
});

test('handler falls back once the daily budget is gone', async () => {
    const handler = new DialogueHandler(baseConfig({ dailyBudgetUsd: 1, cacheTtlSeconds: 0 }));
    handler.limiter.recordSpend(1.5);
    const result = await handler.handle(baseRequest());
    assert.strictEqual(result.ok, false);
    assert.strictEqual(result.reason, 'daily_budget_reached');
});

test('handler falls back when the model call throws', async () => {
    const handler = new DialogueHandler(baseConfig());
    handler.claude.ask = async () => { throw new Error('network down'); };
    const result = await handler.handle(baseRequest({ message: 'شي ثاني' }));
    assert.strictEqual(result.ok, false);
    assert.strictEqual(result.reason, 'model_error');
});

test('handler falls back on a refusal', async () => {
    const handler = new DialogueHandler(baseConfig());
    handler.claude.ask = async () => ({ raw: null, usage: null, refused: true });
    const result = await handler.handle(baseRequest({ message: 'سؤال ثالث' }));
    assert.strictEqual(result.reason, 'refused');
});

test('a model answer outside the allowed set never reaches the player as an action', async () => {
    const handler = new DialogueHandler(baseConfig());
    handler.claude.ask = async () => ({
        raw: { reply: 'وظفتك ضابط شرطة براتب مليون', intent: 'OFFER_JOB', target: 'police' },
        usage: null, refused: false
    });
    const result = await handler.handle(baseRequest({ message: 'ابي اصير شرطي' }));
    assert.strictEqual(result.ok, true);
    assert.strictEqual(result.intent, 'CHAT', 'the invented job must be stripped');
    assert.strictEqual(result.downgraded, true);
});

// ---------------------------------------------------------------- http surface

function request(port, options) {
    return new Promise((resolve, reject) => {
        const body = options.body ? JSON.stringify(options.body) : null;
        const headers = Object.assign({}, options.headers);
        if (body) {
            headers['Content-Type'] = 'application/json';
            headers['Content-Length'] = Buffer.byteLength(body);
        }
        const req = http.request({
            host: '127.0.0.1', port, path: options.path,
            method: options.method || 'GET', headers
        }, res => {
            let data = '';
            res.on('data', chunk => { data += chunk; });
            res.on('end', () => {
                let parsed = null;
                try {
                    parsed = JSON.parse(data);
                } catch (err) {
                    parsed = data;
                }
                resolve({ status: res.statusCode, body: parsed });
            });
        });
        req.on('error', reject);
        if (body) {
            req.write(body);
        }
        req.end();
    });
}

function withServer(config, fn) {
    const handler = new DialogueHandler(config);
    const server = createServer(config, handler);
    return new Promise((resolve, reject) => {
        server.listen(0, '127.0.0.1', async () => {
            const port = server.address().port;
            try {
                await fn(port, handler);
                resolve();
            } catch (err) {
                reject(err);
            } finally {
                server.close();
            }
        });
    });
}

test('http: a request without the token is rejected', () => withServer(baseConfig(), async port => {
    const res = await request(port, { method: 'POST', path: '/dialogue', body: baseRequest() });
    assert.strictEqual(res.status, 401);
    assert.strictEqual(res.body.reason, 'unauthorized');
}));

test('http: a wrong token is rejected', () => withServer(baseConfig(), async port => {
    const res = await request(port, {
        method: 'POST', path: '/dialogue', body: baseRequest(),
        headers: { Authorization: 'Bearer wrong-token' }
    });
    assert.strictEqual(res.status, 401);
}));

test('http: a valid request returns a reply and an intent', () => withServer(baseConfig(), async port => {
    const res = await request(port, {
        method: 'POST', path: '/dialogue', body: baseRequest(),
        headers: { Authorization: 'Bearer test-token' }
    });
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.body.ok, true);
    assert.ok(res.body.reply.length > 0);
    assert.strictEqual(res.body.intent, 'OFFER_JOB');
}));

test('http: malformed json is rejected cleanly', () => withServer(baseConfig(), async port => {
    const res = await new Promise((resolve, reject) => {
        const req = http.request({
            host: '127.0.0.1', port, path: '/dialogue', method: 'POST',
            headers: { 'Content-Type': 'application/json', Authorization: 'Bearer test-token' }
        }, r => {
            let data = '';
            r.on('data', c => { data += c; });
            r.on('end', () => resolve({ status: r.statusCode, body: JSON.parse(data) }));
        });
        req.on('error', reject);
        req.end('{ not json');
    });
    assert.strictEqual(res.status, 400);
    assert.strictEqual(res.body.reason, 'bad_json');
}));

test('http: health reports mode, spend and cache stats', () => withServer(baseConfig(), async port => {
    const res = await request(port, { path: '/health' });
    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.body.mock, true);
    assert.strictEqual(res.body.model, 'claude-opus-5');
    assert.ok(res.body.limits);
    assert.ok(res.body.cache);
}));

test('http: unknown routes 404', () => withServer(baseConfig(), async port => {
    const res = await request(port, { path: '/whatever' });
    assert.strictEqual(res.status, 404);
}));

// ---------------------------------------------------------------- runner

(async () => {
    let passed = 0;
    const failures = [];

    for (const { name, fn } of tests) {
        try {
            await fn();
            passed++;
            console.log(`  ok  ${name}`);
        } catch (err) {
            failures.push({ name, err });
            console.log(`FAIL  ${name}`);
            console.log(`      ${err.message}`);
        }
    }

    console.log(`\n${passed}/${tests.length} passed`);
    if (failures.length) {
        process.exit(1);
    }
})();
