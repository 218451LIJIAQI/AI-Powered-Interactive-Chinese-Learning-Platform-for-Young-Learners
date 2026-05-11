#Requires -Version 5.1
<#
.SYNOPSIS
  Quick start for the AI-Powered Chinese Learning Platform (local dev).

.DESCRIPTION
  Starts MySQL (docker compose), the Spring Boot backend, and the Vue (Vite) frontend.
  Idempotent: skips services that are already running. Auto-fallbacks the frontend
  port if the requested one is busy. Intended for FYP demo / local development only.

.PARAMETER FrontendPort
  Preferred frontend dev server port. Default 5173. Auto-falls back to the next
  free port in range if busy.

.PARAMETER BackendPort
  Preferred backend port. Default 8080. Must match the application.yml server.port.

.PARAMETER JwtSecret
  Optional local-dev JWT signing secret. Must be at least 32 characters.
  If omitted and backend/.env does not define JWT_SECRET, the script generates
  a random process-local secret for this run.

.PARAMETER AdminEmail / AdminPassword
  Seed the default admin account on first startup.

.EXAMPLE
  .\start.ps1
  .\start.ps1 -FrontendPort 5174
  .\start.ps1 -SkipMySql
  .\start.ps1 -SkipFrontend
#>

[CmdletBinding()]
param(
    [int]$FrontendPort = 5173,
    [int]$BackendPort  = 8080,
    [string]$JwtSecret = "",
    [string]$AdminEmail = "admin@example.com",
    [string]$AdminPassword = "Admin123!",
    [switch]$SkipMySql,
    [switch]$SkipBackend,
    [switch]$SkipFrontend
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
$StateFile   = Join-Path $ProjectRoot ".run-state.json"

function Write-Section([string]$t) {
    Write-Host ""
    Write-Host "=== $t ===" -ForegroundColor Cyan
}

function Test-PortBusy([int]$p) {
    return [bool](Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue)
}

function Find-FreePort([int]$start, [int]$tries = 10) {
    for ($i = 0; $i -lt $tries; $i++) {
        $candidate = $start + $i
        if (-not (Test-PortBusy $candidate)) { return $candidate }
    }
    throw "No free port found in range $start..$($start + $tries - 1)."
}

function Wait-Http([string]$url, [int]$timeoutSec) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri $url -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
            if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { return $true }
        } catch {}
        Start-Sleep -Seconds 2
        Write-Host "." -NoNewline
    }
    Write-Host ""
    return $false
}

function New-RandomJwtSecret() {
    $bytes = New-Object byte[] 48
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return [Convert]::ToBase64String($bytes)
}

# Parse a simple KEY=VALUE .env file and expose each entry as a process env var.
# Lines starting with '#' and blank lines are ignored. Values may be wrapped in
# single or double quotes. .env values OVERWRITE any existing process env var,
# so editing backend/.env always takes effect on the next start.ps1 run -
# even within the same PowerShell session.
function Import-DotEnv([string]$path) {
    if (-not (Test-Path $path)) { return 0 }
    $count = 0
    Get-Content $path -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { return }
        $eq = $line.IndexOf("=")
        if ($eq -lt 1) { return }
        $k = $line.Substring(0, $eq).Trim()
        $v = $line.Substring($eq + 1).Trim()
        if ($v.Length -ge 2 -and (
                ($v.StartsWith('"') -and $v.EndsWith('"')) -or
                ($v.StartsWith("'") -and $v.EndsWith("'")))) {
            $v = $v.Substring(1, $v.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($k, $v, "Process")
        $count++
    }
    return $count
}

# Load previous state (if any) so we can preserve known values across runs.
$state = @{}
if (Test-Path $StateFile) {
    try {
        $prev = Get-Content $StateFile -Raw | ConvertFrom-Json
        if ($prev.backendPid)   { $state.backendPid   = $prev.backendPid }
        if ($prev.backendPort)  { $state.backendPort  = $prev.backendPort }
        if ($prev.frontendPid)  { $state.frontendPid  = $prev.frontendPid }
        if ($prev.frontendPort) { $state.frontendPort = $prev.frontendPort }
    } catch { }
}

# -------- 1/3 MySQL --------
if (-not $SkipMySql) {
    Write-Section "1/3 MySQL (Docker)"
    $running = docker ps --format "{{.Names}}" 2>$null | Where-Object { $_ -eq "ai-chinese-learning-mysql" }
    if ($running) {
        Write-Host "Already running: ai-chinese-learning-mysql" -ForegroundColor Green
    } else {
        Write-Host "docker compose up -d ..."
        Push-Location $ProjectRoot
        try { docker compose up -d | Out-Host } finally { Pop-Location }
        if ($LASTEXITCODE -ne 0) { throw "docker compose up -d failed (exit $LASTEXITCODE)." }
    }

    Write-Host "Waiting for healthy" -NoNewline
    $deadline = (Get-Date).AddSeconds(60)
    $ok = $false
    while ((Get-Date) -lt $deadline) {
        $status = docker inspect --format "{{.State.Health.Status}}" ai-chinese-learning-mysql 2>$null
        if ($status -eq "healthy") { $ok = $true; break }
        Start-Sleep -Seconds 2
        Write-Host "." -NoNewline
    }
    Write-Host ""
    if ($ok) { Write-Host "MySQL healthy on localhost:3307" -ForegroundColor Green }
    else     { throw "MySQL did not become healthy within 60s." }
}

# -------- 2/3 Backend --------
if (-not $SkipBackend) {
    Write-Section "2/3 Backend (Spring Boot on :$BackendPort)"
    if (Test-PortBusy $BackendPort) {
        Write-Host "Port $BackendPort already in use - assuming backend is running. Skipping." -ForegroundColor Yellow
        $state.backendPort = $BackendPort
    } else {
        # Pre-compute the likely frontend port(s) so CORS matches even after fallback.
        $frontendPrimary = $FrontendPort
        $frontendFallback = $FrontendPort
        if (Test-PortBusy $frontendPrimary) {
            $frontendFallback = Find-FreePort ($FrontendPort + 1) 10
        }
        $corsOrigins = @("http://localhost:$frontendPrimary")
        if ($frontendFallback -ne $frontendPrimary) {
            $corsOrigins += "http://localhost:$frontendFallback"
        }

        $backendDir = Join-Path $ProjectRoot "backend"

        # 1. Load backend/.env first so user-supplied secrets (AI_API_KEY,
        #    AI_ENABLE_REAL_PROVIDER, JWT_SECRET, STRIPE_*, SMTP_*, etc.) take
        #    precedence over the script defaults below.
        $envFile = Join-Path $backendDir ".env"
        $loaded  = Import-DotEnv $envFile
        if ($loaded -gt 0) {
            Write-Host "Loaded $loaded variable(s) from backend/.env" -ForegroundColor Green
        }

        # 2. Apply script defaults only where .env did not already provide a value.
        if (-not $env:JWT_SECRET) {
            if ($JwtSecret) {
                $env:JWT_SECRET = $JwtSecret
            } else {
                $env:JWT_SECRET = New-RandomJwtSecret
                Write-Host "Generated an ephemeral JWT secret for this local run." -ForegroundColor Yellow
            }
        }
        if (-not $env:APP_CORS_ALLOWED_ORIGINS) { $env:APP_CORS_ALLOWED_ORIGINS = ($corsOrigins -join ",") }
        if (-not $env:DEFAULT_ADMIN_EMAIL)      { $env:DEFAULT_ADMIN_EMAIL      = $AdminEmail }
        if (-not $env:DEFAULT_ADMIN_PASSWORD)   { $env:DEFAULT_ADMIN_PASSWORD   = $AdminPassword }

        Write-Host "Starting backend from $backendDir ..."
        Write-Host "  CORS allowed: $env:APP_CORS_ALLOWED_ORIGINS"
        if ($env:AI_API_KEY) {
            $keyLen = $env:AI_API_KEY.Length
            Write-Host "  AI key: present ($keyLen chars), real-provider=$env:AI_ENABLE_REAL_PROVIDER" -ForegroundColor Green
        } else {
            Write-Host "  AI key: not set - AI features will show friendly fallback" -ForegroundColor Yellow
        }
        $proc = Start-Process -FilePath "cmd.exe" `
                              -ArgumentList "/c","mvnw.cmd spring-boot:run" `
                              -WorkingDirectory $backendDir `
                              -PassThru `
                              -WindowStyle Minimized
        $state.backendPid  = $proc.Id
        $state.backendPort = $BackendPort
        Write-Host "Backend PID $($proc.Id) launching on :$BackendPort ..."
        Write-Host "Waiting for /api/v1/health" -NoNewline
        if (Wait-Http "http://localhost:$BackendPort/api/v1/health" 120) {
            Write-Host "Backend READY on http://localhost:$BackendPort" -ForegroundColor Green
        } else {
            Write-Host "Backend did not respond within 120s. Check the minimised cmd window for logs." -ForegroundColor Red
        }
    }
}

# -------- 3/3 Frontend --------
if (-not $SkipFrontend) {
    Write-Section "3/3 Frontend (Vite)"
    $chosen = $FrontendPort
    if (Test-PortBusy $chosen) {
        $alt = Find-FreePort ($chosen + 1) 10
        Write-Host "Port $chosen busy. Using $alt instead." -ForegroundColor Yellow
        $chosen = $alt
    }

    $frontendDir = Join-Path $ProjectRoot "frontend"
    if (-not (Test-Path (Join-Path $frontendDir "node_modules"))) {
        Write-Host "node_modules missing - running npm install (1-2 min) ..."
        Push-Location $frontendDir
        try { npm install | Out-Host } finally { Pop-Location }
        if ($LASTEXITCODE -ne 0) { throw "npm install failed (exit $LASTEXITCODE)." }
    }

    Write-Host "Starting frontend from $frontendDir on :$chosen ..."
    $proc = Start-Process -FilePath "cmd.exe" `
                          -ArgumentList "/c","npm run dev -- --port $chosen --strictPort" `
                          -WorkingDirectory $frontendDir `
                          -PassThru `
                          -WindowStyle Minimized
    $state.frontendPid  = $proc.Id
    $state.frontendPort = $chosen
    Write-Host "Frontend PID $($proc.Id) launching on :$chosen ..."
    Write-Host "Waiting for HTTP" -NoNewline
    if (Wait-Http "http://localhost:$chosen" 45) {
        Write-Host "Frontend READY on http://localhost:$chosen" -ForegroundColor Green
    } else {
        Write-Host "Frontend did not respond within 45s. Check the minimised cmd window for logs." -ForegroundColor Red
    }
    $FrontendPort = $chosen
}

# -------- Persist state & summary --------
$state | ConvertTo-Json -Depth 4 | Set-Content $StateFile -Encoding UTF8

Write-Section "Done"
Write-Host "  MySQL    : localhost:3307 (root/root, db=ai_chinese_learning)" -ForegroundColor Green
Write-Host ("  Backend  : http://localhost:{0}" -f $BackendPort) -ForegroundColor Green
Write-Host ("  Frontend : http://localhost:{0}" -f $FrontendPort) -ForegroundColor Green
Write-Host ("  Admin    : {0} / {1}" -f $AdminEmail, $AdminPassword) -ForegroundColor Green
Write-Host ""
Write-Host "Stop everything       :  .\stop.ps1"
Write-Host "Stop (incl. MySQL)    :  .\stop.ps1 -StopMySql"
