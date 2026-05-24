<#
.SYNOPSIS
    Run Flyway repair against a target MySQL database when checksums drift.

.DESCRIPTION
    Used after a migration file's bytes change in a way that does not
    alter its applied effect (typically: re-formatting, adding header
    comments, or re-encoding line endings on V1__consolidated_schema.sql
    or V14__final_database_contract_hardening.sql). Flyway will normally
    refuse to start the application with "checksum mismatch" — this
    script invokes the Flyway Maven plugin's `repair` goal so the
    schema_history rows pick up the new checksums without re-running
    the migrations.

.SAFETY-FIRST DESIGN
    1. Runs `flyway:validate` first. If validation already passes,
       nothing is done and the script exits 0 — repair is never
       applied speculatively.
    2. Dry-run by default. The destructive `repair` step only runs
       when the caller passes BOTH `-Execute` AND
       `-ConfirmDatabaseName <exact db name>`. This makes it
       impossible to accidentally run repair against the wrong
       database via shell history replay.
    3. Re-validates after repair so the script aborts loudly if
       repair did not fix the underlying issue (rather than silently
       leaving the deployer with a corrupt history).

.PARAMETER DbHost
    Hostname or IP of the target MySQL server.

.PARAMETER DbPort
    TCP port. Defaults to 3307 (the docker-compose local override port).

.PARAMETER DbName
    Database / schema name. Required and must match
    `-ConfirmDatabaseName` for repair to run.

.PARAMETER DbUsername
    MySQL user. Defaults to `root`; production should override.

.PARAMETER DbPassword
    SecureString password. The script unwraps it just-in-time when
    invoking Maven via an environment-variable hand-off so the
    plaintext is never written to disk or PowerShell history.

.PARAMETER Execute
    Switch — required to actually run `flyway:repair`. Without it
    the script stays in dry-run mode (exit code 2).

.PARAMETER ConfirmDatabaseName
    Must be passed AND must exactly equal `-DbName` for repair to
    run. The double-name pattern is a deliberate guard against
    fat-fingered "wrong database" mistakes.

.EXAMPLE
    PS> $pw = Read-Host -AsSecureString 'MySQL root password'
    PS> .\repair-flyway-checksums.ps1 -DbHost localhost -DbName ai_chinese_learning -DbPassword $pw
    # Dry run only — exits 2 with instructions.

    PS> .\repair-flyway-checksums.ps1 `
            -DbHost localhost -DbName ai_chinese_learning `
            -DbPassword $pw -Execute -ConfirmDatabaseName ai_chinese_learning
    # Actually runs Flyway repair, then re-validates.
#>
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
            & .\mvnw.cmd -q "org.flywaydb:flyway-maven-plugin:12.6.2:$Goal"
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
