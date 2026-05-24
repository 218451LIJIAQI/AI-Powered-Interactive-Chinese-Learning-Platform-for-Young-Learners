<#
.SYNOPSIS
    Boot the backend locally with a real AI provider configured
    (e.g. DeepSeek or any OpenAI-compatible API).

.DESCRIPTION
    Reads `.env.ai.local` (creates it from
    `.env.ai.local.example` on first run), exports the AI_* env
    vars into the current process, runs strict validation, then
    launches `mvnw.cmd spring-boot:run`.

    Defaults to also loading `.env.smtp.local` so password reset
    emails go through a real SMTP server during local QA — pass
    `-SkipSmtp` to skip that load.

.VALIDATIONS ENFORCED
    - AI_API_KEY, AI_API_BASE_URL, AI_MODEL_NAME, AI_PROVIDER must
      all be filled (not blank, not "replace-me", not "your-...",
      not "<placeholder>"). This catches the common
      "I committed the example file without filling it in" mistake.
    - AI_PROVIDER restricted to `deepseek` or `openai-compatible`
      (the only two providers the backend's AIClient knows how to
      speak).
    - AI_API_BASE_URL must parse as an absolute URL AND must use
      HTTPS UNLESS the host is localhost / 127.0.0.1 / ::1 (so a
      developer can point at a local mock provider without
      certificate ceremony).
    - AI_ENABLE_REAL_PROVIDER must be `true` — guards against
      accidentally starting in stub mode when this script promised
      a real provider.

.PARAMETER EnvFile
    Path to the AI env file. Defaults to `backend/.env.ai.local`.

.PARAMETER SmtpEnvFile
    Path to the SMTP env file. Defaults to
    `backend/.env.smtp.local`. Ignored when `-SkipSmtp` is set.

.PARAMETER SkipSmtp
    Switch — do not attempt to also load SMTP env vars.

.PARAMETER ValidateOnly
    Switch — only run the validation checks and exit with code 0
    without starting Spring Boot. Useful from CI.
#>
[CmdletBinding()]
param(
    [string]$EnvFile = "",
    [string]$SmtpEnvFile = "",
    [switch]$SkipSmtp,
    [switch]$ValidateOnly
)

$ErrorActionPreference = "Stop"
$backendRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $backendRoot ".env.ai.local"
}
if ([string]::IsNullOrWhiteSpace($SmtpEnvFile)) {
    $SmtpEnvFile = Join-Path $backendRoot ".env.smtp.local"
}
$exampleEnvFile = Join-Path $backendRoot ".env.ai.local.example"

function Get-ProcessEnv {
    param([string]$Name)
    return [Environment]::GetEnvironmentVariable($Name, "Process")
}

function Set-ProcessEnvDefault {
    param(
        [string]$Name,
        [string]$Value
    )
    if ([string]::IsNullOrWhiteSpace((Get-ProcessEnv $Name))) {
        [Environment]::SetEnvironmentVariable($Name, $Value, "Process")
    }
}

function Import-EnvFile {
    param([string]$Path)
    foreach ($rawLine in Get-Content -Path $Path) {
        $line = $rawLine.Trim()
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) {
            continue
        }

        $separatorIndex = $line.IndexOf("=")
        if ($separatorIndex -le 0) {
            continue
        }

        $name = $line.Substring(0, $separatorIndex).Trim()
        $value = $line.Substring($separatorIndex + 1).Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

function Test-PlaceholderValue {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $true
    }

    $normalized = $Value.Trim().ToLowerInvariant()
    return $normalized -eq "replace-me" `
        -or $normalized.StartsWith("replace-with-") `
        -or $normalized.StartsWith("your-") `
        -or ($normalized.StartsWith("<") -and $normalized.EndsWith(">"))
}

function Assert-RequiredAiValue {
    param([string]$Name)
    $value = Get-ProcessEnv $Name
    if (Test-PlaceholderValue $value) {
        throw "$Name is required for AI startup. Edit .env.ai.local and fill it with your real AI value."
    }
}

function Assert-SupportedAiProvider {
    $provider = (Get-ProcessEnv "AI_PROVIDER").Trim().ToLowerInvariant()
    if ($provider -ne "deepseek" -and $provider -ne "openai-compatible" -and $provider -ne "openai_compatible") {
        throw "AI_PROVIDER must be deepseek or openai-compatible. Current value: $provider"
    }
}

function Assert-ValidAiBaseUrl {
    $rawValue = Get-ProcessEnv "AI_API_BASE_URL"
    $uri = $null
    if (-not [Uri]::TryCreate($rawValue, [UriKind]::Absolute, [ref]$uri)) {
        throw "AI_API_BASE_URL must be an absolute URL."
    }

    $scheme = $uri.Scheme.ToLowerInvariant()
    if ($scheme -eq "https") {
        return
    }

    $uriHost = $uri.Host.ToLowerInvariant()
    $isLoopback = $scheme -eq "http" -and ($uriHost -eq "localhost" -or $uriHost -eq "127.0.0.1" -or $uriHost -eq "::1")
    if (-not $isLoopback) {
        throw "AI_API_BASE_URL must use HTTPS unless it points to localhost/127.0.0.1 for local provider testing."
    }
}

if (-not (Test-Path $EnvFile)) {
    if (Test-Path $exampleEnvFile) {
        Copy-Item -Path $exampleEnvFile -Destination $EnvFile -Force
        throw "Created $EnvFile from .env.ai.local.example. Fill AI_API_KEY, then run this script again."
    }
    throw "AI env file not found: $EnvFile."
}

Import-EnvFile $EnvFile
if (-not $SkipSmtp -and (Test-Path $SmtpEnvFile)) {
    Import-EnvFile $SmtpEnvFile
    Write-Host "SMTP local environment loaded from $SmtpEnvFile."
}

Set-ProcessEnvDefault "AI_ENABLE_REAL_PROVIDER" "true"
Set-ProcessEnvDefault "AI_PROVIDER" "deepseek"
Set-ProcessEnvDefault "AI_API_BASE_URL" "https://api.deepseek.com"
Set-ProcessEnvDefault "AI_MODEL_NAME" "deepseek-chat"
Set-ProcessEnvDefault "AI_TIMEOUT_SECONDS" "30"
Set-ProcessEnvDefault "AI_MAX_OUTPUT_TOKENS" "1200"

if ((Get-ProcessEnv "AI_ENABLE_REAL_PROVIDER").Trim().ToLowerInvariant() -ne "true") {
    throw "AI_ENABLE_REAL_PROVIDER must be true for real AI startup."
}

Assert-RequiredAiValue "AI_PROVIDER"
Assert-RequiredAiValue "AI_API_BASE_URL"
Assert-RequiredAiValue "AI_MODEL_NAME"
Assert-RequiredAiValue "AI_API_KEY"
Assert-SupportedAiProvider
Assert-ValidAiBaseUrl

Write-Host "AI enabled for backend startup."
Write-Host "AI provider: $(Get-ProcessEnv "AI_PROVIDER")"
Write-Host "AI API base URL: $(Get-ProcessEnv "AI_API_BASE_URL")"
Write-Host "AI model: $(Get-ProcessEnv "AI_MODEL_NAME")"
Write-Host "AI timeout seconds: $(Get-ProcessEnv "AI_TIMEOUT_SECONDS")"
Write-Host "AI max output tokens: $(Get-ProcessEnv "AI_MAX_OUTPUT_TOKENS")"
Write-Host "AI API key: [hidden]"

if ($ValidateOnly) {
    Write-Host "AI environment validation completed."
    exit 0
}

Push-Location $backendRoot
try {
    & .\mvnw.cmd spring-boot:run
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
