#Requires -Version 5.1
<#
.SYNOPSIS
  Quick stop for the AI-Powered Chinese Learning Platform (local dev).

.DESCRIPTION
  Terminates the Spring Boot backend and the Vite frontend by port, then
  optionally stops the MySQL Docker container. Reads .run-state.json when
  available; otherwise probes the default ports.

.PARAMETER StopMySql
  Also run "docker compose down" to stop the MySQL container. Off by default
  because MySQL is often left running across work sessions.

.EXAMPLE
  .\stop.ps1
  .\stop.ps1 -StopMySql
#>

[CmdletBinding()]
param(
    [switch]$StopMySql
)

$ErrorActionPreference = "Continue"
$ProjectRoot = $PSScriptRoot
$StateFile   = Join-Path $ProjectRoot ".run-state.json"

function Write-Section([string]$t) {
    Write-Host ""
    Write-Host "=== $t ===" -ForegroundColor Cyan
}

function Test-PortBusy([int]$p) {
    return [bool](Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue)
}

function Stop-ByPort([int]$port, [string]$label) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if (-not $conns) {
        Write-Host "$label :$port - not running."
        return
    }
    $ownerPids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($ownerPid in $ownerPids) {
        try {
            $proc = Get-Process -Id $ownerPid -ErrorAction Stop
            # Kill children first (mvnw -> java, npm -> node, etc.)
            Get-CimInstance Win32_Process -Filter "ParentProcessId = $ownerPid" -ErrorAction SilentlyContinue | ForEach-Object {
                try {
                    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
                    Write-Host "  killed child PID $($_.ProcessId) ($($_.Name))"
                } catch {}
            }
            Stop-Process -Id $ownerPid -Force -ErrorAction Stop
            Write-Host "$label :$port - killed PID $ownerPid ($($proc.ProcessName))" -ForegroundColor Green
        } catch {
            Write-Host "$label :$port - failed to kill PID $ownerPid : $_" -ForegroundColor Red
        }
    }
}

# Figure out which ports to target.
$backendPort   = 8080
$frontendPorts = @(5173, 5174, 5175, 5176, 5177, 5178, 5179, 5180)

if (Test-Path $StateFile) {
    try {
        $s = Get-Content $StateFile -Raw | ConvertFrom-Json
        if ($s.backendPort)  { $backendPort   = [int]$s.backendPort }
        if ($s.frontendPort) { $frontendPorts = @([int]$s.frontendPort) + $frontendPorts }
    } catch { }
}

Write-Section "Stopping Backend"
Stop-ByPort $backendPort "Backend"

Write-Section "Stopping Frontend"
foreach ($p in ($frontendPorts | Select-Object -Unique)) {
    Stop-ByPort $p "Frontend"
}

# Belt-and-braces: kill any stray Java (Spring Boot) or Node (Vite) from this project.
Write-Section "Cleaning stray dev processes"
Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -and ($_.CommandLine -match 'spring-boot' -or $_.CommandLine -match 'aichinese')
} | ForEach-Object {
    try {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        Write-Host "  stopped java PID $($_.ProcessId)" -ForegroundColor Yellow
    } catch {}
}
Get-CimInstance Win32_Process -Filter "Name = 'node.exe'" -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -and $_.CommandLine -match 'vite'
} | ForEach-Object {
    try {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        Write-Host "  stopped node PID $($_.ProcessId)" -ForegroundColor Yellow
    } catch {}
}

# MySQL (opt-in)
if ($StopMySql) {
    Write-Section "Stopping MySQL"
    Push-Location $ProjectRoot
    try { docker compose down | Out-Host } finally { Pop-Location }
    if ($LASTEXITCODE -eq 0) {
        Write-Host "MySQL container stopped." -ForegroundColor Green
    } else {
        Write-Host "docker compose down failed (exit $LASTEXITCODE)." -ForegroundColor Red
    }
} else {
    Write-Section "MySQL (left running)"
    Write-Host "Pass -StopMySql to also shut down the MySQL Docker container."
}

# Clean the state file so the next start fresh-detects.
if (Test-Path $StateFile) {
    Remove-Item $StateFile -ErrorAction SilentlyContinue
}

Write-Section "Final check"
$backendState = if (Test-PortBusy $backendPort) { "STILL UP" } else { "stopped" }
Write-Host ("  Backend :$backendPort  -> $backendState")
foreach ($p in ($frontendPorts | Select-Object -Unique)) {
    $feState = if (Test-PortBusy $p) { "STILL UP" } else { "stopped" }
    Write-Host ("  Frontend:$p  -> $feState")
}
