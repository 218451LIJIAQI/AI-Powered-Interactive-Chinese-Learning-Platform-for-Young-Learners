<#
.SYNOPSIS
    Boot the backend locally with a real SMTP outbound mail
    provider configured.

.DESCRIPTION
    Used during local QA of password-reset emails, teacher
    verification notifications, and any other transactional email
    that should hit a real inbox during development.

    Reads `.env.smtp.local` (creates it from
    `.env.smtp.local.example` on first run), then INFERS sane
    defaults for SMTP_HOST / SMTP_PORT / STARTTLS / SSL settings
    by looking at the domain portion of SMTP_USERNAME.

.PROVIDER AUTO-DETECTION
    Known domains are mapped directly:
        gmail.com         -> smtp.gmail.com:587 STARTTLS
        outlook.com /
        hotmail.com /
        live.com          -> smtp-mail.outlook.com:587 STARTTLS
        qq.com            -> smtp.qq.com:587 STARTTLS
        163.com / 126.com -> smtp.163.com|smtp.126.com:465 SSL
        icloud.com        -> smtp.mail.me.com:587 STARTTLS
        yahoo.com         -> smtp.mail.yahoo.com:587 STARTTLS
        sina.com          -> smtp.sina.com:465 SSL

    Unknown domains fall back to MX-record lookup via
    `Resolve-DnsName`; if the MX points at Google Workspace or
    Microsoft 365, the corresponding SMTP host is configured.
    Otherwise the script asks the user to fill SMTP_HOST manually
    rather than guessing.

.VALIDATIONS ENFORCED
    - SMTP_USERNAME, SMTP_PASSWORD, SMTP_HOST, SMTP_PORT must all
      be filled with non-placeholder values (same placeholder
      heuristics as `start-local-with-ai.ps1`).
    - SMTP_FROM defaults to SMTP_USERNAME if not explicitly set.

.PARAMETER EnvFile
    Path to the SMTP env file. Defaults to
    `backend/.env.smtp.local`.

.PARAMETER ValidateOnly
    Switch — only validate; do not start Spring Boot.
#>
[CmdletBinding()]
param(
    [string]$EnvFile = "",
    [switch]$ValidateOnly
)

$ErrorActionPreference = "Stop"
$backendRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $backendRoot ".env.smtp.local"
}
$exampleEnvFile = Join-Path $backendRoot ".env.smtp.local.example"

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
    if (-not (Test-Path $Path)) {
        if (Test-Path $exampleEnvFile) {
            Copy-Item -Path $exampleEnvFile -Destination $Path -Force
            throw "Created $Path from .env.smtp.local.example. Fill SMTP_USERNAME and SMTP_PASSWORD, then run this script again."
        }
        throw "SMTP env file not found: $Path."
    }

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

function Set-SmtpDefaultsFromUsername {
    $username = Get-ProcessEnv "SMTP_USERNAME"
    if ((Test-PlaceholderValue $username) -or -not $username.Contains("@")) {
        return
    }

    $domain = $username.Substring($username.LastIndexOf("@") + 1).ToLowerInvariant()
    $matchedKnownProvider = $true
    switch ($domain) {
        "gmail.com" {
            Set-ProcessEnvDefault "SMTP_HOST" "smtp.gmail.com"
            Set-ProcessEnvDefault "SMTP_PORT" "587"
            Set-ProcessEnvDefault "SMTP_STARTTLS_ENABLED" "true"
            Set-ProcessEnvDefault "SMTP_SSL_ENABLED" "false"
            break
        }
        "outlook.com" {
            Set-ProcessEnvDefault "SMTP_HOST" "smtp-mail.outlook.com"
            Set-ProcessEnvDefault "SMTP_PORT" "587"
            Set-ProcessEnvDefault "SMTP_STARTTLS_ENABLED" "true"
            Set-ProcessEnvDefault "SMTP_SSL_ENABLED" "false"
            break
        }
        "hotmail.com" {
            Set-ProcessEnvDefault "SMTP_HOST" "smtp-mail.outlook.com"
            Set-ProcessEnvDefault "SMTP_PORT" "587"
            Set-ProcessEnvDefault "SMTP_STARTTLS_ENABLED" "true"
            Set-ProcessEnvDefault "SMTP_SSL_ENABLED" "false"
            break
        }
        "live.com" {
            Set-ProcessEnvDefault "SMTP_HOST" "smtp-mail.outlook.com"
            Set-ProcessEnvDefault "SMTP_PORT" "587"
            Set-ProcessEnvDefault "SMTP_STARTTLS_ENABLED" "true"
            Set-ProcessEnvDefault "SMTP_SSL_ENABLED" "false"
            break
        }
        "qq.com" {
            Set-ProcessEnvDefault "SMTP_HOST" "smtp.qq.com"
            Set-ProcessEnvDefault "SMTP_PORT" "587"
            Set-ProcessEnvDefault "SMTP_STARTTLS_ENABLED" "true"
            Set-ProcessEnvDefault "SMTP_SSL_ENABLED" "false"
            break
        }
        "163.com" {
            Set-ProcessEnvDefault "SMTP_HOST" "smtp.163.com"
            Set-ProcessEnvDefault "SMTP_PORT" "465"
            Set-ProcessEnvDefault "SMTP_STARTTLS_ENABLED" "false"
            Set-ProcessEnvDefault "SMTP_SSL_ENABLED" "true"
            break
        }
        "126.com" {
            Set-ProcessEnvDefault "SMTP_HOST" "smtp.126.com"
            Set-ProcessEnvDefault "SMTP_PORT" "465"
            Set-ProcessEnvDefault "SMTP_STARTTLS_ENABLED" "false"
            Set-ProcessEnvDefault "SMTP_SSL_ENABLED" "true"
            break
        }
        "icloud.com" {
            Set-ProcessEnvDefault "SMTP_HOST" "smtp.mail.me.com"
            Set-ProcessEnvDefault "SMTP_PORT" "587"
            Set-ProcessEnvDefault "SMTP_STARTTLS_ENABLED" "true"
            Set-ProcessEnvDefault "SMTP_SSL_ENABLED" "false"
            break
        }
        "yahoo.com" {
            Set-ProcessEnvDefault "SMTP_HOST" "smtp.mail.yahoo.com"
            Set-ProcessEnvDefault "SMTP_PORT" "587"
            Set-ProcessEnvDefault "SMTP_STARTTLS_ENABLED" "true"
            Set-ProcessEnvDefault "SMTP_SSL_ENABLED" "false"
            break
        }
        "sina.com" {
            Set-ProcessEnvDefault "SMTP_HOST" "smtp.sina.com"
            Set-ProcessEnvDefault "SMTP_PORT" "465"
            Set-ProcessEnvDefault "SMTP_STARTTLS_ENABLED" "false"
            Set-ProcessEnvDefault "SMTP_SSL_ENABLED" "true"
            break
        }
        default {
            $matchedKnownProvider = $false
            break
        }
    }

    if ($matchedKnownProvider -or -not [string]::IsNullOrWhiteSpace((Get-ProcessEnv "SMTP_HOST"))) {
        return
    }

    try {
        $mxHosts = @(Resolve-DnsName -Type MX $domain -ErrorAction Stop |
            ForEach-Object { $_.NameExchange.ToLowerInvariant() })
        if ($mxHosts | Where-Object { $_ -like "*google.com" -or $_ -like "*googlemail.com" }) {
            Set-ProcessEnvDefault "SMTP_HOST" "smtp.gmail.com"
            Set-ProcessEnvDefault "SMTP_PORT" "587"
            Set-ProcessEnvDefault "SMTP_STARTTLS_ENABLED" "true"
            Set-ProcessEnvDefault "SMTP_SSL_ENABLED" "false"
            return
        }
        if ($mxHosts | Where-Object { $_ -like "*.protection.outlook.com" -or $_ -like "*outlook.com" }) {
            Set-ProcessEnvDefault "SMTP_HOST" "smtp.office365.com"
            Set-ProcessEnvDefault "SMTP_PORT" "587"
            Set-ProcessEnvDefault "SMTP_STARTTLS_ENABLED" "true"
            Set-ProcessEnvDefault "SMTP_SSL_ENABLED" "false"
            return
        }
    }
    catch {
        Write-Host "Could not infer SMTP provider from MX records for $domain. Fill SMTP_HOST and SMTP_PORT manually."
    }
}

function Assert-RequiredSmtpValue {
    param([string]$Name)
    $value = Get-ProcessEnv $Name
    if (Test-PlaceholderValue $value) {
        throw "$Name is required for SMTP startup. Edit .env.smtp.local and fill it with your real SMTP value."
    }
}

Import-EnvFile $EnvFile
Set-ProcessEnvDefault "SMTP_ENABLED" "true"
Set-ProcessEnvDefault "SMTP_AUTH_ENABLED" "true"
Set-ProcessEnvDefault "SMTP_CONNECTION_TIMEOUT_MS" "5000"
Set-ProcessEnvDefault "SMTP_TIMEOUT_MS" "5000"
Set-ProcessEnvDefault "SMTP_WRITE_TIMEOUT_MS" "5000"
Set-SmtpDefaultsFromUsername

if ([string]::IsNullOrWhiteSpace((Get-ProcessEnv "SMTP_FROM"))) {
    [Environment]::SetEnvironmentVariable("SMTP_FROM", (Get-ProcessEnv "SMTP_USERNAME"), "Process")
}

Assert-RequiredSmtpValue "SMTP_USERNAME"
Assert-RequiredSmtpValue "SMTP_PASSWORD"
Assert-RequiredSmtpValue "SMTP_HOST"
Assert-RequiredSmtpValue "SMTP_PORT"

Write-Host "SMTP enabled for backend startup."
Write-Host "SMTP host: $(Get-ProcessEnv "SMTP_HOST")"
Write-Host "SMTP port: $(Get-ProcessEnv "SMTP_PORT")"
Write-Host "SMTP username: $(Get-ProcessEnv "SMTP_USERNAME")"
Write-Host "SMTP from: $(Get-ProcessEnv "SMTP_FROM")"
Write-Host "SMTP password: [hidden]"

if ($ValidateOnly) {
    Write-Host "SMTP environment validation completed."
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
