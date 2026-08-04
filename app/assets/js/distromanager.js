const { DistributionAPI } = require('helios-core/common')
const fs = require('fs-extra')
const path = require('path')

const ConfigManager = require('./configmanager')

// In production, set the ACRP_DISTRO_URL env var to your hosted distribution.json URL
// Example: https://raw.githubusercontent.com/AdventureCity/acrp-distro/main/distribution.json
exports.REMOTE_DISTRO_URL = process.env.ACRP_DISTRO_URL || 'http://127.0.0.1:38412/distribution.json'

const BUNDLED_DISTRO_PATH = path.join(__dirname, '..', 'distribution.json')

function seedBundledDistribution(name) {
    const target = path.join(ConfigManager.getLauncherDirectory(), name)
    if(!fs.existsSync(BUNDLED_DISTRO_PATH)) {
        return
    }

    let shouldSeed = !fs.existsSync(target)
    if(!shouldSeed) {
        try {
            const bundled = fs.readJsonSync(BUNDLED_DISTRO_PATH)
            const existing = fs.readJsonSync(target)
            shouldSeed = existing.servers?.[0]?.id !== bundled.servers?.[0]?.id
                || (existing.servers?.[0]?.modules?.length ?? 0) < (bundled.servers?.[0]?.modules?.length ?? 0)
        } catch (_err) {
            shouldSeed = true
        }
    }

    if(shouldSeed) {
        fs.copySync(BUNDLED_DISTRO_PATH, target)
    }
}

seedBundledDistribution('distribution.json')
seedBundledDistribution('distribution_dev.json')

const api = new DistributionAPI(
    ConfigManager.getLauncherDirectory(),
    null, // Injected forcefully by the preloader.
    null, // Injected forcefully by the preloader.
    exports.REMOTE_DISTRO_URL,
    false
)

exports.DistroAPI = api
