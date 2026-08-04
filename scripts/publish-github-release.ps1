param(
    [Parameter(Mandatory = $true)]
    [string]$Repo,

    [string]$Tag = "v1.0.0",

    [string]$Title = "ACRP Launcher 1.0.0",

    [string]$Asset = "M:\HeliosLauncher-master\HeliosLauncher-master\dist\ACRP-Launcher-1.0.0-win-unpacked.zip"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI (gh) is not installed. Install it, then run: gh auth login"
}

if (-not (Test-Path -LiteralPath $Asset)) {
    throw "Release asset was not found: $Asset"
}

$notes = @"
ACRP Launcher build for Adventure City.

- Minecraft 1.12.2
- Forge 14.23.5.2860
- Pack name: AcRp
- Adventure City themed launcher
- AC store content ready
- Discord links ready
- Resource pack auto-enable support
"@

$releaseExists = $false
try {
    gh release view $Tag --repo $Repo *> $null
    $releaseExists = $true
} catch {
    $releaseExists = $false
}

if ($releaseExists) {
    gh release upload $Tag $Asset --repo $Repo --clobber
} else {
    gh release create $Tag $Asset --repo $Repo --title $Title --notes $notes
}

Write-Host "Uploaded $Asset to https://github.com/$Repo/releases/tag/$Tag"
