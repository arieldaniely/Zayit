param(
    [Parameter(Mandatory = $true)][string]$Database,
    [Parameter(Mandatory = $true)][string]$ModelDirectory,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [int]$ShardCount = 8,
    [int]$Workers = 8,
    [int]$OnnxThreads = 1
)

$ErrorActionPreference = 'Stop'
$library = (Resolve-Path (Join-Path $PSScriptRoot '..\SeforimLibrary')).Path
$databasePath = (Resolve-Path -LiteralPath $Database).Path
$modelPath = (Resolve-Path -LiteralPath $ModelDirectory).Path
$outputPath = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null

for ($shard = 0; $shard -lt $ShardCount; $shard++) {
    $manifest = Join-Path $outputPath ('shard-{0:D2}\semantic.properties' -f $shard)
    if (Test-Path -LiteralPath $manifest) {
        $properties = Get-Content -LiteralPath $manifest -Raw
        if ($properties -match ('(?m)^shardIndex=' + $shard + '\r?$') -and
            $properties -match ('(?m)^shardCount=' + $ShardCount + '\r?$') -and
            $properties -match '(?m)^indexed=[1-9][0-9]*\r?$') {
            Write-Host "Skipping completed shard $shard/$ShardCount"
            continue
        }
        throw "Invalid existing shard manifest: $manifest"
    }

    Write-Host "Starting shard $shard/$ShardCount at $(Get-Date -Format o)"
    Push-Location $library
    try {
        $arguments = @(
            ':search:buildSemanticIndex',
            "-PseforimDb=$databasePath",
            "-PsemanticModelDir=$modelPath",
            "-PsemanticIndexDir=$outputPath",
            "-PshardIndex=$shard",
            "-PshardCount=$ShardCount",
            "-PsemanticWorkers=$Workers",
            "-PsemanticThreads=$OnnxThreads",
            '--offline', '--no-daemon', '--no-build-cache', '--no-configuration-cache',
            '--project-cache-dir', '.codex-local-project-cache',
            '--init-script', '..\.codex-local-build-init.gradle'
        )
        & .\gradlew.bat @arguments
        if ($LASTEXITCODE -ne 0) { throw "Shard $shard failed with Gradle exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

Write-Host "All $ShardCount shards completed at $(Get-Date -Format o)"
