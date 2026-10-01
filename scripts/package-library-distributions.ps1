param(
    [Parameter(Mandatory = $true)][string]$Database,
    [Parameter(Mandatory = $true)][string]$PdfDirectory,
    [Parameter(Mandatory = $true)][string]$SemanticDirectory,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [ValidateRange(1, 22)][int]$CompressionLevel = 22
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $repoRoot 'SeforimLibrary/gradlew.bat'
$libraryRoot = Join-Path $repoRoot 'SeforimLibrary'
$databasePath = (Resolve-Path -LiteralPath $Database).Path
$pdfPath = (Resolve-Path -LiteralPath $PdfDirectory).Path
$semanticPath = (Resolve-Path -LiteralPath $SemanticDirectory).Path
$outputPath = [System.IO.Path]::GetFullPath($OutputDirectory)
if ((Test-Path -LiteralPath $outputPath) -and (Get-ChildItem -LiteralPath $outputPath -Force)) {
    throw 'Use an empty output directory to avoid mixing release assets'
}
foreach ($required in @(($databasePath + '.lucene'), ($databasePath + '.lookup.lucene'),
    (Join-Path (Split-Path $databasePath) 'catalog.pb'),
    (Join-Path (Split-Path $databasePath) 'lexical.db'),
    (Join-Path (Split-Path $databasePath) 'release_info.txt'))) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing base artifact: $required" }
}

$variants = @(
    @{ Name = 'seforim_bundle'; Pdf = 'true'; Vectors = 'true' },
    @{ Name = 'seforim_bundle-no-pdf'; Pdf = 'false'; Vectors = 'true' },
    @{ Name = 'seforim_bundle-no-vectors'; Pdf = 'true'; Vectors = 'false' },
    @{ Name = 'seforim_bundle-database-only'; Pdf = 'false'; Vectors = 'false' }
)

foreach ($variant in $variants) {
    $bundle = Join-Path $outputPath ($variant.Name + '.tar.zst')
    if (Test-Path -LiteralPath $bundle) { throw "Output already exists: $bundle" }
    & $gradle -p $libraryRoot ':packaging:packageArtifacts' `
        "-PseforimDb=$databasePath" "-PpdfLibraryDir=$pdfPath" "-PsemanticBundleDir=$semanticPath" `
        "-PincludePdf=$($variant.Pdf)" "-PincludeVectors=$($variant.Vectors)" "-PbundleOutput=$bundle" `
        "-PzstdLevel=$CompressionLevel" '-x' ':packaging:writeReleaseInfo' '-x' ':packaging:downloadLexicalDb'
    if ($LASTEXITCODE -ne 0) { throw "Packaging failed: $($variant.Name)" }
}

# Also publish the supplemental archive used when a user chooses PDF later.
$pdfBundle = Join-Path $outputPath 'talmud_bavli_latest.tar.zst'
if (Test-Path -LiteralPath $pdfBundle) { throw "Output already exists: $pdfBundle" }
& $gradle -p $libraryRoot ':packaging:packageArtifacts' `
    "-PseforimDb=$databasePath" "-PpdfLibraryDir=$pdfPath" `
    '-PpdfOnly=true' '-PincludePdf=true' '-PincludeVectors=false' "-PbundleOutput=$pdfBundle" `
    "-PzstdLevel=$CompressionLevel" `
    '-x' ':packaging:writeReleaseInfo' '-x' ':packaging:downloadLexicalDb'
if ($LASTEXITCODE -ne 0) { throw 'PDF packaging failed' }
& $gradle -p $libraryRoot ':packaging:packageSemanticBundle' `
    "-PseforimDb=$databasePath" "-PsemanticModelDir=$(Join-Path $semanticPath 'model')" `
    "-PsemanticIndexDir=$(Join-Path $semanticPath 'index')" `
    "-PsemanticBundleOutput=$(Join-Path $outputPath 'semantic-bundle.tar.zst')" "-PzstdLevel=$CompressionLevel"
if ($LASTEXITCODE -ne 0) { throw 'Semantic packaging failed' }

# Keep unsplit archives locally, but list only the parts when a bundle was split.
$assets = Get-ChildItem -LiteralPath $outputPath -File | Where-Object {
    $_.Name -match '\.tar\.zst(\.part\d+)?$|^semantic-bundle\.json$'
} | Where-Object {
    -not ($_.Name.EndsWith('.tar.zst') -and
        (Get-ChildItem -LiteralPath $outputPath -Filter ($_.Name + '.part*')))
} | Sort-Object Name
$entries = @($assets | ForEach-Object {
    if ($_.Length -ge 2GB) { throw "Asset exceeds GitHub limit: $($_.Name)" }
    @{ name = $_.Name; bytes = $_.Length; sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
})
$manifest = @{
    databaseSha256 = (Get-FileHash -LiteralPath $databasePath -Algorithm SHA256).Hash.ToLowerInvariant()
    defaultBundle = 'seforim_bundle'
    assets = $entries
}
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $outputPath 'distributions.json') -Encoding UTF8
$entries | ForEach-Object { "$($_.sha256)  $($_.name)" } |
    Set-Content -LiteralPath (Join-Path $outputPath 'checksums.sha256') -Encoding UTF8
Write-Host 'Six distributions ready. Publish only the assets listed in distributions.json, plus the manifest and checksums.'
