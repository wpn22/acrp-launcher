const crypto = require('crypto')
const fs = require('fs')
const path = require('path')

const repoRoot = path.resolve(__dirname, '..')

const PACK_NAME = 'AcRp'
const INSTANCE_DIR = process.env.ACRP_INSTANCE_DIR
const META_INSTANCE_DIR = process.env.ACRP_META_INSTANCE_DIR
const LIBRARY_ROOT = process.env.ACRP_LIBRARY_ROOT
const FILE_BASE_URL = trimTrailingSlash(process.env.ACRP_FILE_BASE_URL
    || 'http://127.0.0.1:38412/AcRp')
const CONTENT_PATH = process.env.ACRP_CONTENT_PATH
    || path.join(repoRoot, 'app', 'assets', 'config', 'acrp-content.json')

const OUT_DISTRIBUTION = process.env.ACRP_DIST_OUT
    || path.join(repoRoot, 'app', 'assets', 'distribution.json')
const OUT_DOC_DISTRIBUTION = process.env.ACRP_DOC_DIST_OUT
    || path.join(repoRoot, 'docs', 'acrp_distribution.generated.json')
const OUT_PACK_MANIFEST = process.env.ACRP_PACK_MANIFEST_OUT
    || path.join(repoRoot, 'docs', 'acrp_pack_manifest.json')
const OUT_FORGE_MANIFEST = process.env.ACRP_FORGE_MANIFEST_OUT
    || path.join(repoRoot, 'app', 'assets', 'pack', '_meta', 'acrp_forge-14.23.5.2860.version.json')
const OUT_DOC_FORGE_MANIFEST = process.env.ACRP_DOC_FORGE_MANIFEST_OUT
    || path.join(repoRoot, 'docs', 'acrp_forge-14.23.5.2860.version.json')

const EXCLUDED_TOP_LEVEL = new Set([
    'crash-reports',
    'downloads',
    'logs',
    'playerdata',
    'saves',
    'screenshots',
    'tv-cache'
])

const EXCLUDED_RELATIVE_PATTERNS = [
    /^\.curseclient$/i,
    /^hs_err_pid.+\.log$/i,
    /^replay_pid.+\.log$/i,
    /^minecraftinstance\.json(?:\.bak.*)?$/i,
    /^usercache\.json$/i,
    /^usernamecache\.json$/i,
    /^jcef\/cache\//i,
    /\.log$/i,
    /\.tmp$/i
]

function trimTrailingSlash(value) {
    return value.replace(/\/+$/, '')
}

function toForwardSlash(value) {
    return value.replace(/\\/g, '/')
}

function encodeUrlPath(relPath) {
    return toForwardSlash(relPath)
        .split('/')
        .map(part => encodeURIComponent(part))
        .join('/')
}

function readJson(file) {
    return JSON.parse(fs.readFileSync(file, 'utf8'))
}

function hashFile(file, algo) {
    const hash = crypto.createHash(algo)
    hash.update(fs.readFileSync(file))
    return hash.digest('hex')
}

function ensureParent(file) {
    fs.mkdirSync(path.dirname(file), { recursive: true })
}

function walkFiles(root) {
    const out = []
    const stack = [root]
    while (stack.length > 0) {
        const current = stack.pop()
        for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
            const full = path.join(current, entry.name)
            if (entry.isDirectory()) {
                stack.push(full)
            } else if (entry.isFile()) {
                out.push(full)
            }
        }
    }
    return out.sort((a, b) => toForwardSlash(path.relative(root, a)).localeCompare(toForwardSlash(path.relative(root, b))))
}

function shouldExclude(relPath) {
    const normalized = toForwardSlash(relPath)
    const top = normalized.split('/')[0]
    return EXCLUDED_TOP_LEVEL.has(top)
        || EXCLUDED_RELATIVE_PATTERNS.some(pattern => pattern.test(normalized))
}

function safeFileId(relPath) {
    return `acrp.file.${crypto.createHash('sha1').update(toForwardSlash(relPath)).digest('hex').slice(0, 12)}`
}

function fileModule(instanceRoot, file) {
    const rel = toForwardSlash(path.relative(instanceRoot, file))
    const stats = fs.statSync(file)
    return {
        id: safeFileId(rel),
        name: rel,
        type: 'File',
        artifact: {
            size: stats.size,
            MD5: hashFile(file, 'md5'),
            path: rel,
            url: `${FILE_BASE_URL}/${encodeUrlPath(rel)}`
        }
    }
}

function libraryModuleFromArtifact(lib, type = 'Library') {
    const artifact = lib.downloads && lib.downloads.artifact
    if (artifact == null || artifact.path == null) {
        return null
    }

    const local = path.join(LIBRARY_ROOT, artifact.path)
    const artifactOut = {
        size: fs.existsSync(local) ? fs.statSync(local).size : artifact.size,
        url: artifact.url
    }

    if (fs.existsSync(local)) {
        artifactOut.MD5 = hashFile(local, 'md5')
    }

    return {
        id: lib.name,
        name: lib.name,
        type,
        artifact: artifactOut
    }
}

function buildForgeModule(metaInstance) {
    const instanceJsonPath = path.join(metaInstance, 'minecraftinstance.json')
    const instanceJson = readJson(instanceJsonPath)
    const loader = instanceJson.baseModLoader
    const forgeVersionJson = JSON.parse(loader.versionJson)
    const forgeLibName = `net.minecraftforge:forge:${loader.minecraftVersion}-${loader.forgeVersion}`
    const forgeLibrary = forgeVersionJson.libraries.find(lib => lib.name === forgeLibName)
    const forgeModule = libraryModuleFromArtifact(forgeLibrary, 'ForgeHosted')
    forgeModule.name = `Minecraft Forge ${loader.forgeVersion}`

    const versionManifestBody = JSON.stringify(forgeVersionJson, null, 4)
    ensureParent(OUT_FORGE_MANIFEST)
    fs.writeFileSync(OUT_FORGE_MANIFEST, `${versionManifestBody}\n`, 'utf8')
    ensureParent(OUT_DOC_FORGE_MANIFEST)
    fs.writeFileSync(OUT_DOC_FORGE_MANIFEST, `${versionManifestBody}\n`, 'utf8')
    const versionManifestRel = `_meta/${path.basename(OUT_FORGE_MANIFEST)}`
    const versionManifestStats = fs.statSync(OUT_FORGE_MANIFEST)

    const versionManifestModule = {
        id: forgeVersionJson.id,
        name: `Forge Version Manifest ${loader.forgeVersion}`,
        type: 'VersionManifest',
        artifact: {
            size: versionManifestStats.size,
            MD5: hashFile(OUT_FORGE_MANIFEST, 'md5'),
            url: `${FILE_BASE_URL}/${versionManifestRel}`
        }
    }

    forgeModule.subModules = [
        versionManifestModule,
        ...forgeVersionJson.libraries
            .filter(lib => lib.name !== forgeLibName)
            .map(lib => libraryModuleFromArtifact(lib))
            .filter(Boolean)
    ]

    return {
        module: forgeModule,
        instanceJson,
        forgeVersionJson
    }
}

function buildDistribution() {
    if (!INSTANCE_DIR) throw new Error('ACRP_INSTANCE_DIR environment variable must be set')
    if (!META_INSTANCE_DIR) throw new Error('ACRP_META_INSTANCE_DIR environment variable must be set')
    if (!LIBRARY_ROOT) throw new Error('ACRP_LIBRARY_ROOT environment variable must be set')
    if (!fs.existsSync(INSTANCE_DIR)) {
        throw new Error(`ACRP instance folder not found: ${INSTANCE_DIR}`)
    }
    if (!fs.existsSync(path.join(META_INSTANCE_DIR, 'minecraftinstance.json'))) {
        throw new Error(`CurseForge metadata not found: ${META_INSTANCE_DIR}`)
    }

    const content = fs.existsSync(CONTENT_PATH) ? readJson(CONTENT_PATH) : {}
    const serverName = content.brand?.serverName || 'Adventure City'
    const serverAddress = process.env.ACRP_SERVER_ADDRESS || content.server?.address || '127.0.0.1:25565'
    const hasPublicServerAddress = Boolean(process.env.ACRP_SERVER_ADDRESS || content.server?.address)
    const discordClientId = process.env.ACRP_DISCORD_CLIENT_ID || content.discordRpc?.clientId?.trim()
    const discordSettings = discordClientId
        ? {
            clientId: discordClientId,
            smallImageText: serverName,
            smallImageKey: content.discordRpc?.smallImageKey || 'acrp'
        }
        : null
    const serverDiscordSettings = discordClientId
        ? {
            shortId: PACK_NAME,
            largeImageText: content.discordRpc?.largeImageText || `${serverName} Roleplay`,
            largeImageKey: content.discordRpc?.largeImageKey || 'acrp'
        }
        : null

    const { module: forgeModule, instanceJson } = buildForgeModule(META_INSTANCE_DIR)
    const allFiles = walkFiles(INSTANCE_DIR)
    const includedFiles = allFiles.filter(file => !shouldExclude(path.relative(INSTANCE_DIR, file)))
    const excludedFiles = allFiles.filter(file => shouldExclude(path.relative(INSTANCE_DIR, file)))

    const fileModules = includedFiles.map(file => fileModule(INSTANCE_DIR, file))
    const topLevelSummary = {}
    for (const file of includedFiles) {
        const rel = toForwardSlash(path.relative(INSTANCE_DIR, file))
        const top = rel.split('/')[0]
        topLevelSummary[top] = topLevelSummary[top] || { files: 0, bytes: 0 }
        topLevelSummary[top].files += 1
        topLevelSummary[top].bytes += fs.statSync(file).size
    }

    const distribution = {
        version: '1.0.0',
        ...(discordSettings == null ? {} : { discord: discordSettings }),
        rss: '',
        servers: [
            {
                id: PACK_NAME,
                name: serverName,
                description: `${serverName} Roleplay server pack for Minecraft 1.12.2.`,
                icon: `${FILE_BASE_URL}/AcRp.ico`,
                version: '1.0.0',
                address: serverAddress,
                minecraftVersion: instanceJson.gameVersion || '1.12.2',
                javaOptions: {
                    supported: '>=8 <9',
                    suggestedMajor: 8,
                    ram: {
                        minimum: 4096,
                        recommended: 8192
                    }
                },
                ...(serverDiscordSettings == null ? {} : { discord: serverDiscordSettings }),
                mainServer: true,
                autoconnect: hasPublicServerAddress,
                modules: [
                    forgeModule,
                    ...fileModules
                ]
            }
        ]
    }

    const manifest = {
        generatedAt: new Date().toISOString(),
        packName: PACK_NAME,
        sourceInstance: INSTANCE_DIR,
        metadataInstance: META_INSTANCE_DIR,
        fileBaseUrl: FILE_BASE_URL,
        minecraftVersion: instanceJson.gameVersion || '1.12.2',
        forgeVersion: instanceJson.baseModLoader.forgeVersion,
        serverId: PACK_NAME,
        included: {
            files: includedFiles.length,
            bytes: includedFiles.reduce((sum, file) => sum + fs.statSync(file).size, 0),
            topLevel: topLevelSummary
        },
        excluded: {
            files: excludedFiles.length,
            examples: excludedFiles.slice(0, 50).map(file => toForwardSlash(path.relative(INSTANCE_DIR, file)))
        }
    }

    return { distribution, manifest }
}

function main() {
    const { distribution, manifest } = buildDistribution()
    for (const file of [OUT_DISTRIBUTION, OUT_DOC_DISTRIBUTION, OUT_PACK_MANIFEST]) {
        ensureParent(file)
    }
    fs.writeFileSync(OUT_DISTRIBUTION, `${JSON.stringify(distribution, null, 4)}\n`, 'utf8')
    fs.writeFileSync(OUT_DOC_DISTRIBUTION, `${JSON.stringify(distribution, null, 4)}\n`, 'utf8')
    fs.writeFileSync(OUT_PACK_MANIFEST, `${JSON.stringify(manifest, null, 4)}\n`, 'utf8')

    console.log(`Generated ${OUT_DISTRIBUTION}`)
    console.log(`Generated ${OUT_DOC_DISTRIBUTION}`)
    console.log(`Generated ${OUT_PACK_MANIFEST}`)
    console.log(`Included ${manifest.included.files} files (${manifest.included.bytes} bytes), excluded ${manifest.excluded.files}`)
}

main()
