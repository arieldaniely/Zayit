param(
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [Parameter(Mandatory = $true)][string]$ReleaseTag,
    [string]$LibraryRepository = 'arieldaniely/SeforimLibrary',
    [string]$AppRepository = 'arieldaniely/Zayit',
    [switch]$Publish
)

$ErrorActionPreference = 'Stop'
Get-Command gh -ErrorAction Stop | Out-Null
$root = (Resolve-Path -LiteralPath $OutputDirectory).Path
$manifest = Get-Content -LiteralPath (Join-Path $root 'distributions.json') -Raw | ConvertFrom-Json
$assets = @($manifest.assets)
foreach ($prefix in @('seforim_bundle.tar.zst', 'seforim_bundle-no-pdf.tar.zst',
    'seforim_bundle-no-vectors.tar.zst', 'seforim_bundle-database-only.tar.zst',
    'talmud_bavli_latest.tar.zst', 'semantic-bundle.tar.zst', 'semantic-bundle.json')) {
    if (-not ($assets | Where-Object { $_.name -eq $prefix -or $_.name -match ('^' + [regex]::Escape($prefix) + '\.part\d+$') })) {
        throw "Missing distribution: $prefix"
    }
}
$files = @($assets | ForEach-Object {
    if ([IO.Path]::GetFileName($_.name) -ne $_.name) { throw 'Invalid manifest asset name' }
    $file = Get-Item -LiteralPath (Join-Path $root $_.name)
    if ($file.Length -ne $_.bytes -or $file.Length -ge 2GB -or
        (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant() -ne $_.sha256) {
        throw "Size or checksum mismatch: $($_.name)"
    }
    $file.FullName
})
$semanticManifest = Get-Content -LiteralPath (Join-Path $root 'semantic-bundle.json') -Raw | ConvertFrom-Json
if ($semanticManifest.databaseSha256 -ne $manifest.databaseSha256) { throw 'Database fingerprints differ' }

function Invoke-GhChecked([string[]]$Arguments) {
    & gh @Arguments
    if ($LASTEXITCODE -ne 0) { throw "GitHub command failed: $($Arguments[0..1] -join ' ')" }
}

function Ensure-Draft([string]$Repository, [string]$Tag, [string]$Title) {
    $existing = & gh release view $Tag --repo $Repository --json isDraft 2>$null
    if ($LASTEXITCODE -eq 0) {
        if (-not ($existing | ConvertFrom-Json).isDraft) {
            throw "Release already published; use a new tag: $Repository/$Tag"
        }
    } else {
        Invoke-GhChecked -Arguments @('release', 'create', $Tag, '--repo', $Repository, '--draft', '--title', $Title,
            '--notes', "Database SHA-256: $($manifest.databaseSha256)")
    }
}

$semanticTag = "semantic-round2-$ReleaseTag"
Ensure-Draft $LibraryRepository $ReleaseTag $ReleaseTag
Ensure-Draft $AppRepository $semanticTag "Round 2 semantic index ($ReleaseTag)"
foreach ($file in $files + @((Join-Path $root 'distributions.json'), (Join-Path $root 'checksums.sha256'))) {
    Invoke-GhChecked -Arguments @('release', 'upload', $ReleaseTag, $file, '--repo', $LibraryRepository, '--clobber')
}
# The current app discovers semantic supplements in Zayit releases.
foreach ($file in $files | Where-Object { [IO.Path]::GetFileName($_) -like 'semantic-bundle*' }) {
    Invoke-GhChecked -Arguments @('release', 'upload', $semanticTag, $file, '--repo', $AppRepository, '--clobber')
}
if ($Publish) {
    Invoke-GhChecked -Arguments @('release', 'edit', $semanticTag, '--repo', $AppRepository, '--draft=false', '--latest=false')
    Invoke-GhChecked -Arguments @('release', 'edit', $ReleaseTag, '--repo', $LibraryRepository, '--draft=false', '--latest')
} else {
    Write-Host 'Both releases are drafts. Rerun with -Publish to publish the verified distributions.'
}
Write-Host "Library release: https://github.com/$LibraryRepository/releases/tag/$ReleaseTag"
