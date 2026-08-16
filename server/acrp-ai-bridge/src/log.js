'use strict';

const fs = require('fs');
const path = require('path');

/**
 * Append-only JSONL record of every exchange. Players will try to make NPCs say
 * things they should not, so a readable transcript is not optional - it is how the
 * staff moderates the feature.
 */
class DialogueLog {

    constructor(file, enabled) {
        this.enabled = Boolean(enabled) && Boolean(file);
        this.file = file;
        this.failed = false;

        if (this.enabled) {
            try {
                fs.mkdirSync(path.dirname(file), { recursive: true });
            } catch (err) {
                console.error(`[acrp-ai] Cannot create log directory: ${err.message}`);
                this.enabled = false;
            }
        }
    }

    write(entry) {
        if (!this.enabled || this.failed) {
            return;
        }
        const line = JSON.stringify(Object.assign({ at: new Date().toISOString() }, entry));
        fs.appendFile(this.file, line + '\n', err => {
            if (err && !this.failed) {
                this.failed = true;
                console.error(`[acrp-ai] Logging disabled after write error: ${err.message}`);
            }
        });
    }
}

module.exports = { DialogueLog };
