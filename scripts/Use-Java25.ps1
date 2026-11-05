# Installs a verified, workspace-local JDK and selects it for this PowerShell session.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$toolsDirectory = Join-Path $repositoryRoot '.tools'
$javaDirectory = Join-Path $toolsDirectory 'java'
$jdkDirectory = Join-Path $javaDirectory 'jdk-25.0.5+8'
$archivePath = Join-Path $toolsDirectory 'temurin-25.0.5+8-windows-x64.zip'
$downloadUrl = 'https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/OpenJDK25U-jdk_x64_windows_hotspot_25.0.5_8.zip'
$expectedHash = '636bdc13dc649ddc7d5921c5c7d23cca6e44fd19d662b45cc9c02135a6c96c42'

if (-not (Test-Path -LiteralPath (Join-Path $jdkDirectory 'bin\javac.exe'))) {
    New-Item -ItemType Directory -Force -Path $toolsDirectory, $javaDirectory | Out-Null
    if (-not (Test-Path -LiteralPath $archivePath)) {
        Write-Host 'Downloading Temurin JDK 25 for Windows x64...'
        Invoke-WebRequest -UseBasicParsing -Uri $downloadUrl -OutFile $archivePath
    }
    if ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash -ne $expectedHash) {
        throw "JDK archive checksum mismatch. Remove $archivePath and retry."
    }
    Expand-Archive -LiteralPath $archivePath -DestinationPath $javaDirectory -Force
}

if (-not (Test-Path -LiteralPath (Join-Path $jdkDirectory 'bin\javac.exe'))) {
    throw "The JDK was not found at $jdkDirectory after extraction."
}
$env:JAVA_HOME = $jdkDirectory
$env:Path = (Join-Path $jdkDirectory 'bin') + [IO.Path]::PathSeparator + $env:Path
Write-Host "Using Java 25 from $jdkDirectory for this session."
