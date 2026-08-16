'use strict';

/**
 * The complete set of things an NPC is allowed to *do*. The model never invents an
 * action - it picks one of these, and the plugin decides whether it is legal.
 *
 * This is the guard rail that stops an NPC promising a job, a payout, or a mission
 * that does not exist in the jobs engine.
 */
const INTENTS = {
    /** Small talk. Always allowed, and the fallback for anything invalid. */
    CHAT: 'CHAT',
    /** Offer employment in a specific job id. */
    OFFER_JOB: 'OFFER_JOB',
    /** Offer a specific contract id from the player's current job. */
    OFFER_CONTRACT: 'OFFER_CONTRACT',
    /** Point the player at a named zone. */
    DIRECT_TO_ZONE: 'DIRECT_TO_ZONE',
    /** Politely refuse - abuse, off-topic, or something the NPC cannot help with. */
    DECLINE: 'DECLINE'
};

const ALL_INTENTS = Object.keys(INTENTS);

/** JSON schema handed to the model; `enum` alone removes most of the failure surface. */
function buildSchema(allowedIntents) {
    const intents = (allowedIntents && allowedIntents.length) ? allowedIntents : ['CHAT'];
    return {
        type: 'object',
        properties: {
            reply: {
                type: 'string',
                description: 'ما يقوله الشخصية للاعب. جملتان كحد أقصى، بلهجة سعودية طبيعية.'
            },
            intent: {
                type: 'string',
                enum: intents,
                description: 'الإجراء المطلوب من النظام. اختر CHAT إذا ما فيه إجراء.'
            },
            target: {
                type: 'string',
                description: 'معرّف الوظيفة أو المهمة أو المنطقة المرتبط بالإجراء. اتركه فارغاً مع CHAT.'
            }
        },
        required: ['reply', 'intent', 'target'],
        additionalProperties: false
    };
}

/**
 * Second line of defence: whatever comes back is checked against what this NPC was
 * actually allowed to offer. An out-of-scope answer degrades to CHAT rather than
 * reaching the player as a promise the server cannot keep.
 *
 * @param raw       parsed model output
 * @param allowed   intent names this NPC may use
 * @param targets   map of intent -> array of legal target ids
 */
function validate(raw, allowed, targets, maxReplyChars) {
    const allowedIntents = (allowed && allowed.length) ? allowed : ['CHAT'];
    const result = {
        reply: typeof raw?.reply === 'string' ? raw.reply.trim() : '',
        intent: 'CHAT',
        target: '',
        downgraded: false
    };

    if (!result.reply) {
        return { ...result, valid: false };
    }
    if (result.reply.length > maxReplyChars) {
        result.reply = result.reply.slice(0, maxReplyChars).trim();
    }

    const intent = typeof raw?.intent === 'string' ? raw.intent.trim().toUpperCase() : 'CHAT';
    const target = typeof raw?.target === 'string' ? raw.target.trim() : '';

    if (!ALL_INTENTS.includes(intent) || !allowedIntents.includes(intent)) {
        result.downgraded = intent !== 'CHAT';
        return { ...result, valid: true };
    }

    // Intents that act on something must name a target the NPC is permitted to use.
    if (intent === 'CHAT' || intent === 'DECLINE') {
        result.intent = intent;
        return { ...result, valid: true };
    }

    const legalTargets = (targets && targets[intent]) || [];
    if (!target || !legalTargets.includes(target)) {
        result.downgraded = true;
        return { ...result, valid: true };
    }

    result.intent = intent;
    result.target = target;
    return { ...result, valid: true };
}

module.exports = { INTENTS, ALL_INTENTS, buildSchema, validate };
