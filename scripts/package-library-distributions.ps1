param(
    [Parameter(Mandatory = $true)][string]$Database,
    [Parameter(Mandatory = $true)][string]$PdfDirectory,
    [Parameter(Mandatory = $true)][string]$SemanticDirectory,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $repoRoot 'SeforimLibrary/gradlew.bat'
$libraryRoot = Join-Path $repoRoot 'SeforimLibrary'
$databasePath = (Resolve-Path -LiteralPath $Database).Path
$pdfPath = (Resolve-Path -LiteralPath $PdfDirectory).Path
$semanticPath = (Resolve-Path -LiteralPath $SemanticDirectory).Path
$outputPath = [System.IO.Path]::GetFullPath($OutputDirectory)

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
        "-PincludePdf=$($variant.Pdf)" "-PincludeVectors=$($variant.Vectors)" "-PbundleOutput=$bundle"
    if ($LASTEXITCODE -ne 0) { throw "Packaging failed: $($variant.Name)" }
}

# Also publish the supplemental archive used when a user chooses PDF later.
$pdfBundle = Join-Path $outputPath 'talmud_bavli_latest.tar.zst'
if (Test-Path -LiteralPath $pdfBundle) { throw "Output already exists: $pdfBundle" }
& $gradle -p $libraryRoot ':packaging:packageArtifacts' `
    "-PseforimDb=$databasePath" "-PpdfLibraryDir=$pdfPath" `
    '-PpdfOnly=true' '-PincludePdf=true' '-PincludeVectors=false' "-PbundleOutput=$pdfBundle" `
    '-PsplitPartBytes=9223372036854775807'
if ($LASTEXITCODE -ne 0) { throw 'PDF packaging failed' }

& $gradle -p $libraryRoot ':packaging:packageSemanticBundle' `
    "-PseforimDb=$databasePath" "-PsemanticModelDir=$(Join-Path $semanticPath 'model')" `
    "-PsemanticIndexDir=$(Join-Path $semanticPath 'index')" `
    "-PsemanticBundleOutput=$(Join-Path $outputPath 'semantic-bundle.tar.zst')"
if ($LASTEXITCODE -ne 0) { throw 'Semantic packaging failed' }
