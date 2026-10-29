param(
    [ValidateSet('Start', 'Stop', 'Status')]
    [string]$Action = 'Start'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$state = Join-Path $root '.local'
$backend = Join-Path $root 'backend'
$frontend = Join-Path $root 'frontend'
$backendPidFile = Join-Path $state 'backend.pid'
$frontendPidFile = Join-Path $state 'frontend.pid'
$backendJar = 'aichinese-0.0.1-SNAPSHOT.jar'
$viteMarker = 'vite.js'
$vite = Join-Path $frontend 'node_modules\vite\bin\vite.js'

function Get-OwnedProcess([string]$pidFile, [string]$marker) {
    if (-not (Test-Path -LiteralPath $pidFile)) { return $null }
    $savedPid = 0
    if (-not [int]::TryParse(("" + (Get-Content -LiteralPath $pidFile -Raw)).Trim(), [ref]$savedPid)) { return $null }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $savedPid" -ErrorAction SilentlyContinue
    if ($process -and $process.CommandLine -like "*$marker*") { return $process }
    return $null
}

function Stop-OwnedProcess([string]$pidFile, [string]$marker) {
    $process = Get-OwnedProcess $pidFile $marker
    if ($process) {
        Stop-Process -Id $process.ProcessId -Force
        Write-Host "Stopped process $($process.ProcessId)."
    }
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
}

function Stop-All {
    Stop-OwnedProcess $frontendPidFile $viteMarker
    Stop-OwnedProcess $backendPidFile $backendJar
}

function Wait-ForUrl([string]$url, [int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -eq 200) { return $true }
        } catch { }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Get-JavaHome([string]$javaExe) {
    # java.exe on PATH can be a launcher copy (e.g. Oracle's javapath), so ask the JVM for its own home.
    # The settings go to stderr, which must not stop the script.
    $ErrorActionPreference = 'Continue'
    $line = & $javaExe -XshowSettings:properties -version 2>&1 | ForEach-Object { "$_" } |
        Where-Object { $_ -match '^\s*java\.home\s*=' } | Select-Object -First 1
    if (-not $line) { throw 'Could not read java.home from java.exe.' }
    return ($line -replace '^\s*java\.home\s*=\s*', '').Trim()
}

function Assert-FreePort([int]$port) {
    $listener = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
    if ($listener) { throw "Port $port is already in use. Close that service before starting FYP." }
}

function New-DemoPassword {
    $randomBytes = New-Object byte[] 18
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($randomBytes) }
    finally { $generator.Dispose() }
    return 'Fyp!' + [Convert]::ToBase64String($randomBytes)
}

$backendProcess = Get-OwnedProcess $backendPidFile $backendJar
$frontendProcess = Get-OwnedProcess $frontendPidFile $viteMarker

if ($Action -eq 'Stop') {
    Stop-All
    exit 0
}

if ($Action -eq 'Status') {
    Write-Host "Backend: $(if ($backendProcess) { 'running' } else { 'stopped' })"
    Write-Host "Frontend: $(if ($frontendProcess) { 'running' } else { 'stopped' })"
    exit 0
}

$dataDirectory = Join-Path $state 'data'
New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null
$env:FYP_DATA_DIR = $dataDirectory.Replace('\', '/')
$env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
$java = (Get-Command java.exe -ErrorAction Stop).Source
$node = (Get-Command node.exe -ErrorAction Stop).Source
$env:JAVA_HOME = Get-JavaHome $java
# GA releases print "openjdk 21 2023-09-19", updates "openjdk 21.0.2 2024-01-16".
if ((& $java --version | Select-Object -First 1) -notmatch '^\S+\s+21(\.\d+)*\s') { throw 'Java 21 is required.' }
if ((& $node --version) -notmatch '^v22\.') { throw 'Node.js 22 is required.' }

if ($backendProcess -and $frontendProcess) {
    Write-Host 'FYP is already running at http://127.0.0.1:5174'
    Start-Process 'http://127.0.0.1:5174'
    exit 0
}
Stop-All
Assert-FreePort 8080
Assert-FreePort 5174

Write-Host 'Building backend...'
Push-Location $backend
try { & '.\mvnw.cmd' '-q' '-DskipTests' 'package'; if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' } }
finally { Pop-Location }
if (-not (Test-Path -LiteralPath $vite)) {
    Write-Host 'Installing frontend dependencies...'
    Push-Location $frontend
    try { & 'npm.cmd' 'ci' '--no-fund'; if ($LASTEXITCODE -ne 0) { throw 'Frontend install failed.' } }
    finally { Pop-Location }
}

$adminFile = Join-Path $state 'admin.env'
if (-not (Test-Path -LiteralPath $adminFile)) {
    @('DEFAULT_ADMIN_EMAIL=admin@fyp.local', "DEFAULT_ADMIN_PASSWORD=$(New-DemoPassword)") |
        Set-Content -LiteralPath $adminFile -Encoding ASCII
}
$settings = @{}
Get-Content -LiteralPath $adminFile | ForEach-Object {
    $key, $value = $_.Split('=', 2)
    $settings[$key] = $value
}
foreach ($key in 'DEMO_TEACHER_PASSWORD', 'DEMO_STUDENT_PASSWORD') {
    if (-not $settings.ContainsKey($key)) {
        $settings[$key] = New-DemoPassword
        "$key=$($settings[$key])" | Add-Content -LiteralPath $adminFile -Encoding ASCII
    }
}
foreach ($key in 'DEFAULT_ADMIN_EMAIL', 'DEFAULT_ADMIN_PASSWORD', 'DEMO_TEACHER_PASSWORD', 'DEMO_STUDENT_PASSWORD') {
    if ($settings[$key]) { Set-Item -LiteralPath "Env:$key" -Value $settings[$key] }
}

Write-Host 'Starting backend...'
$backendProcess = Start-Process -FilePath $java -ArgumentList @('-jar', "target\$backendJar", '--spring.profiles.active=demo,desktop') -WorkingDirectory $backend -RedirectStandardOutput (Join-Path $state 'backend.out.log') -RedirectStandardError (Join-Path $state 'backend.err.log') -WindowStyle Hidden -PassThru
$backendProcess.Id | Set-Content -LiteralPath $backendPidFile -Encoding ASCII
if (-not (Wait-ForUrl 'http://127.0.0.1:8080/api/v1/health' 90)) {
    Stop-All
    throw "Backend did not become healthy. Check $state\backend.err.log and backend.out.log."
}

Write-Host 'Starting frontend...'
$frontendProcess = Start-Process -FilePath $node -ArgumentList @('node_modules\vite\bin\vite.js', '--host', '127.0.0.1') -WorkingDirectory $frontend -RedirectStandardOutput (Join-Path $state 'frontend.out.log') -RedirectStandardError (Join-Path $state 'frontend.err.log') -WindowStyle Hidden -PassThru
$frontendProcess.Id | Set-Content -LiteralPath $frontendPidFile -Encoding ASCII
if (-not (Wait-ForUrl 'http://127.0.0.1:5174/' 30)) {
    Stop-All
    throw "Frontend did not become ready. Check $state\frontend.err.log and frontend.out.log."
}

Write-Host 'FYP is running at http://127.0.0.1:5174'
Write-Host "Demo login details (admin, teacher, student): $adminFile"
Start-Process 'http://127.0.0.1:5174'
