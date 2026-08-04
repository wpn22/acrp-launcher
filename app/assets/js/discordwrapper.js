// Work in progress
const { LoggerUtil } = require('helios-core')

const logger = LoggerUtil.getLogger('DiscordWrapper')

const { Client } = require('discord-rpc-patch')

const Lang = require('./langloader')

let client
let activity

exports.initRPC = function(genSettings, servSettings, initialDetails = Lang.queryJS('discord.waiting')){
    if(!genSettings?.clientId) {
        logger.info('Discord Rich Presence skipped, no clientId provided.')
        return false
    }
    if(client) {
        exports.shutdownRPC()
    }

    client = new Client({ transport: 'ipc' })

    activity = {
        details: initialDetails,
        state: Lang.queryJS('discord.state', {shortId: servSettings.shortId}),
        largeImageKey: servSettings.largeImageKey,
        largeImageText: servSettings.largeImageText,
        smallImageKey: genSettings.smallImageKey,
        smallImageText: genSettings.smallImageText,
        startTimestamp: new Date().getTime(),
        instance: false
    }

    client.on('ready', () => {
        logger.info('Discord RPC Connected')
        client.setActivity(activity)
    })
    
    client.login({clientId: genSettings.clientId}).catch(error => {
        if(error.message.includes('ENOENT')) {
            logger.info('Unable to initialize Discord Rich Presence, no client detected.')
        } else {
            logger.info('Unable to initialize Discord Rich Presence: ' + error.message, error)
        }
    })

    return true
}

exports.updateDetails = function(details){
    if(!client || !activity) {
        return
    }
    activity.details = details
    client.setActivity(activity).catch(error => {
        logger.info('Unable to update Discord Rich Presence: ' + error.message, error)
    })
}

exports.shutdownRPC = function(){
    if(!client) return
    try {
        client.clearActivity()
        client.destroy()
    } catch(error) {
        logger.info('Unable to shut down Discord Rich Presence cleanly: ' + error.message, error)
    }
    client = null
    activity = null
}
