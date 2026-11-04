[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet("eureka-server", "api-gateway", "booking-service", "hotel-service")]
    [string]$Service,

    [switch]$ValidateOnly
)

$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
$envFile = Join-Path $projectRoot ".env.local"

if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw "Missing .env.local. Copy .env.example to .env.local and replace the placeholders."
}

foreach ($line in Get-Content -LiteralPath $envFile) {
    $trimmed = $line.Trim()
    if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) {
        continue
    }

    $parts = $trimmed.Split("=", 2)
    if ($parts.Count -ne 2 -or $parts[0].Trim() -notmatch "^[A-Za-z_][A-Za-z0-9_]*$") {
        throw "Invalid entry in .env.local. Expected NAME=value."
    }

    $name = $parts[0].Trim()
    $value = $parts[1].Trim()
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
        ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }

    [Environment]::SetEnvironmentVariable($name, $value, "Process")
}

if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    throw "JAVA_HOME is missing in .env.local."
}
if (-not (Test-Path -LiteralPath $env:JAVA_HOME -PathType Container)) {
    throw "JAVA_HOME from .env.local does not point to an existing directory."
}

if ($Service -in @("booking-service", "hotel-service")) {
    $jwtBytes = [Text.Encoding]::UTF8.GetByteCount($env:JWT_SECRET)
    if ($jwtBytes -lt 32) {
        throw "JWT_SECRET in .env.local must contain at least 32 bytes."
    }
}

if ($Service -eq "booking-service" -and $env:ADMIN_BOOTSTRAP_ENABLED -eq "true") {
    if ([string]::IsNullOrWhiteSpace($env:ADMIN_USERNAME)) {
        throw "ADMIN_USERNAME is required when ADMIN_BOOTSTRAP_ENABLED=true."
    }
    if ([string]::IsNullOrWhiteSpace($env:ADMIN_PASSWORD)) {
        throw "ADMIN_PASSWORD is required when ADMIN_BOOTSTRAP_ENABLED=true."
    }

    $adminPasswordBytes = [Text.Encoding]::UTF8.GetByteCount($env:ADMIN_PASSWORD)
    if ($adminPasswordBytes -lt 12 -or $adminPasswordBytes -gt 72) {
        throw "ADMIN_PASSWORD must contain between 12 and 72 UTF-8 bytes."
    }
}

if ($ValidateOnly) {
    Write-Host "Environment configuration is valid for $Service."
    exit 0
}

$mavenWrapper = Join-Path $projectRoot "mvnw.cmd"
& $mavenWrapper -pl $Service spring-boot:run
exit $LASTEXITCODE
