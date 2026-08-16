'use strict';

const http = require('http');
const crypto = require('crypto');

const { load } = require('./config');
const { DialogueHandler } = require('./handler');

const MAX_BODY_BYTES = 64 * 1024;

function createServer(config, handler) {
    return http.createServer((req, res) => {
        if (req.method === 'GET' && req.url === '/health') {
            return sendJson(res, 200, Object.assign({ ok: true }, handler.stats()));
        }
        if (req.method !== 'POST' || req.url !== '/dialogue') {
            return sendJson(res, 404, { ok: false, reason: 'not_found' });
        }
        if (!authorized(req, config.token)) {
            return sendJson(res, 401, { ok: false, reason: 'unauthorized' });
        }

        readBody(req, MAX_BODY_BYTES)
            .then(body => {
                let request;
                try {
                    request = JSON.parse(body);
                } catch (err) {
                    return sendJson(res, 400, { ok: false, reason: 'bad_json' });
                }
                return handler.handle(request).then(result => sendJson(res, 200, result));
            })
            .catch(err => {
                const tooLarge = err && err.message === 'body_too_large';
                sendJson(res, tooLarge ? 413 : 500, {
                    ok: false,
                    reason: tooLarge ? 'body_too_large' : 'server_error'
                });
            });
    });
}

/** Constant-time compare so the token cannot be probed byte by byte. */
function authorized(req, expected) {
    if (!expected) {
        // No token configured - only safe because the bridge binds to loopback.
        return true;
    }
    const header = req.headers['authorization'] || '';
    const provided = header.startsWith('Bearer ') ? header.slice(7) : '';
    const a = Buffer.from(provided);
    const b = Buffer.from(expected);
    if (a.length !== b.length) {
        return false;
    }
    return crypto.timingSafeEqual(a, b);
}

function readBody(req, limit) {
    return new Promise((resolve, reject) => {
        let size = 0;
        const chunks = [];
        req.on('data', chunk => {
            size += chunk.length;
            if (size > limit) {
                reject(new Error('body_too_large'));
                req.destroy();
                return;
            }
            chunks.push(chunk);
        });
        req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
        req.on('error', reject);
    });
}

function sendJson(res, status, payload) {
    const body = JSON.stringify(payload);
    res.writeHead(status, {
        'Content-Type': 'application/json; charset=utf-8',
        'Content-Length': Buffer.byteLength(body)
    });
    res.end(body);
}

function main() {
    const config = load();
    const handler = new DialogueHandler(config);
    const server = createServer(config, handler);

    server.listen(config.port, config.host, () => {
        console.log(`[acrp-ai] listening on http://${config.host}:${config.port}`);
        console.log(`[acrp-ai] model: ${config.model}${config.mock ? ' (MOCK MODE - no ANTHROPIC_API_KEY)' : ''}`);
        console.log(`[acrp-ai] daily budget: ${config.dailyBudgetUsd === 0 ? 'unlimited' : '$' + config.dailyBudgetUsd}`);
        if (!config.token) {
            console.warn('[acrp-ai] WARNING: no token set - anything on this host can call the bridge.');
        }
    });

    const shutdown = () => {
        console.log('[acrp-ai] shutting down');
        server.close(() => process.exit(0));
    };
    process.on('SIGINT', shutdown);
    process.on('SIGTERM', shutdown);
}

if (require.main === module) {
    main();
}

module.exports = { createServer, authorized, readBody };
