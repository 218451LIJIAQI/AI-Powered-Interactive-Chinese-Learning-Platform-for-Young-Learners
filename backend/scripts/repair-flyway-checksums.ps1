[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$DbHost,

    [int]$DbPort = 3307,

    [Parameter(Mandatory = $true)]
    [string]$DbName,

    [string]$DbUsername = "root",

    [Parameter(Mandatory = $true)]
    [securestring]$DbPassword,

    [switch]$Execute,

    [string]$ConfirmDatabaseName = ""
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir = (Resolve-Path (Join-Path $ScriptDir "..")).Path

function Write-Section([string]$Title) {
    Write-Host ""
    Write-Host "=== $Title ===" -ForegroundColor Cyan
}

function Invoke-WithEnvironment([hashtable]$Environment, [scriptblock]$Block) {
    $Previous = @{}
    foreach ($Key in $Environment.Keys) {
        $Previous[$Key] = [Environment]::GetEnvironmentVariable($Key, "Process")
        [Environment]::SetEnvironmentVariable($Key, [string]$Environment[$Key], "Process")
    }
    try {
        & $Block
    } finally {
        foreach ($Key in $Environment.Keys) {
            [Environment]::SetEnvironmentVariable($Key, $Previous[$Key], "Process")
        }
    }
}

function Invoke-MavenFlyway([string]$Goal) {
    $PlainPassword = [System.Net.NetworkCredential]::new("", $DbPassword).Password
    $FlywayUrl = "jdbc:mysql://$DbHost`:$DbPort/$DbName`?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Singapore&connectionTimeZone=Asia/Singapore&useUnicode=true&characterEncoding=utf8"
    $Environment = @{
        FLYWAY_URL = $FlywayUrl
        FLYWAY_USER = $DbUsername
        FLYWAY_PASSWORD = $PlainPassword
        FLYWAY_LOCATIONS = "classpath:db/migration"
    }
    Invoke-WithEnvironment $Environment {
        Push-Location $BackendDir
        try {
            & .\mvnw.cmd -q "org.flywaydb:flyway-maven-plugin:11.7.2:$Goal"
            if ($LASTEXITCODE -ne 0) {
                throw "Flyway $Goal failed with exit code $LASTEXITCODE."
            }
        } finally {
            Pop-Location
        }
    }
}

Write-Section "Target database"
Write-Host "Host     : $DbHost"
Write-Host "Port     : $DbPort"
Write-Host "Database : $DbName"
Write-Host "Username : $DbUsername"

Write-Section "Validation before repair"
try {
    Invoke-MavenFlyway "validate"
    Write-Host "Flyway validate already passes. Repair is not required." -ForegroundColor Green
    exit 0
} catch {
    Write-Host "Flyway validate failed before repair: $($_.Exception.Message)" -ForegroundColor Yellow
}

if (-not $Execute) {
    Write-Section "Dry run"
    Write-Host "No changes were made. Re-run with -Execute -ConfirmDatabaseName $DbName to run Flyway repair." -ForegroundColor Yellow
    exit 2
}

if ($ConfirmDatabaseName -ne $DbName) {
    throw "Refusing to run repair: -ConfirmDatabaseName must exactly match '$DbName'."
}

Write-Section "Repair"
Invoke-MavenFlyway "repair"

Write-Section "Validation after repair"
Invoke-MavenFlyway "validate"
Write-Host "Flyway repair completed and validate now passes." -ForegroundColor Green
