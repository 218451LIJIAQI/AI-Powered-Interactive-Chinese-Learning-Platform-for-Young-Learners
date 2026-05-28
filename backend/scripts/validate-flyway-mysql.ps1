<#
.SYNOPSIS
    Spin up a throwaway MySQL container and validate that the
    single consolidated Flyway V1 baseline applies cleanly.

.DESCRIPTION
    Validates the consolidated V1 database contract end-to-end before accepting a
    schema change. Catches the kind of bugs that a unit test of
    Flyway would NOT catch: checksum drift, dialect-specific syntax,
    accidental destructive ALTERs, ordering issues.

    Workflow:
      1. Wait for Docker Desktop / Docker Engine to be reachable
         (auto-launches Docker Desktop on Windows if installed).
      2. Run an ephemeral MySQL container (random container name,
         random root + app passwords, random fresh + compatibility
         schema names so parallel runs do not stomp each other).
      3. Start Spring Boot against a fresh schema and let Flyway apply
         the single V1 baseline.
      4. Assert migration history and key production constraints/indexes.
      5. Execute compatibility checks for SQL fragments folded into V1
         to prove their SQL remains valid after consolidation.
      6. Verify repair safety by intentionally corrupting the V1 checksum
         in the throwaway schema and repairing it under explicit opt-in.
      7. Tear the container down (unless -KeepContainer is set
         for post-mortem inspection).

.PARAMETER Image
    Docker image tag for the throwaway MySQL. Defaults to
    `mysql:8.4` - pin matches the production version.

.PARAMETER StartupTimeoutSeconds
    How long to wait for MySQL to become reachable inside the
    container before giving up. Defaults to 180 seconds, generous
    enough for cold image pulls on slow CI workers.

.PARAMETER KeepContainer
    Switch - do not remove the container at the end. Useful for
    drilling into a failure with `docker exec -it`.
#>
[CmdletBinding()]
param(
    [string]$Image = "mysql:8.4",
    [int]$StartupTimeoutSeconds = 180,
    [switch]$KeepContainer
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir = (Resolve-Path (Join-Path $ScriptDir "..")).Path
$Suffix = [Guid]::NewGuid().ToString("N").Substring(0, 8)
$ContainerName = "ai-chinese-flyway-check-$Suffix"
$FreshDb = "aichinese_flyway_fresh_$Suffix"
$LegacyDb = "aichinese_flyway_legacy_$Suffix"
$RootPassword = "Pw$([Guid]::NewGuid().ToString("N"))"
$AppUsername = "aichinese_app"
$AppPassword = "PwApp$([Guid]::NewGuid().ToString("N"))"
$JwtSecret = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([Guid]::NewGuid().ToString("N") + [Guid]::NewGuid().ToString("N")))
$ConsolidatedMigrationPath = Join-Path $BackendDir "src\main\resources\db\migration\V1__consolidated_schema.sql"
$ConsolidatedMigrationContent = Get-Content -LiteralPath $ConsolidatedMigrationPath -Raw

function Write-Section([string]$Title) {
    Write-Host ""
    Write-Host "=== $Title ===" -ForegroundColor Cyan
}

function Get-CommandPath([string]$Name) {
    $Command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($Command) {
        return $Command.Source
    }
    return $null
}

function Invoke-Docker([string[]]$Arguments) {
    return Invoke-DockerWithEnvironment @{} $Arguments
}

function Invoke-DockerWithEnvironment([hashtable]$Environment, [string[]]$Arguments) {
    return Invoke-WithEnvironment $Environment {
        $Output = & docker @Arguments 2>&1
        $ExitCode = $LASTEXITCODE
        if ($ExitCode -ne 0) {
            throw "docker $(Format-SafeCommandArguments $Arguments) failed with exit code $ExitCode.`n$(Format-SafeOutput $Output)"
        }
        return @($Output)
    }
}

function Format-SafeCommandArguments([string[]]$Arguments) {
    $Sanitized = foreach ($Argument in $Arguments) {
        if ($Argument -match '^(MYSQL_(ROOT_)?PASSWORD|MYSQL_PWD|DB_PASSWORD|JWT_SECRET)=') {
            $Argument -replace '=.*$', '=[hidden]'
        } else {
            $Argument
        }
    }
    return ($Sanitized -join ' ')
}

function Format-SafeOutput($Output) {
    $Text = ($Output -join "`n")
    $Text = $Text -replace [regex]::Escape($RootPassword), "[hidden-root-password]"
    $Text = $Text -replace [regex]::Escape($AppPassword), "[hidden-app-password]"
    $Text = $Text -replace [regex]::Escape($JwtSecret), "[hidden-jwt-secret]"
    return $Text
}

function Test-DockerDaemonReady {
    if (-not (Get-CommandPath "docker")) {
        return $false
    }
    $PreviousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        & docker info *> $null
        return ($LASTEXITCODE -eq 0)
    } catch {
        return $false
    } finally {
        $ErrorActionPreference = $PreviousErrorActionPreference
    }
}

function Get-DockerDesktopPath {
    $Candidates = @(
        (Join-Path ${env:ProgramFiles} "Docker\Docker\Docker Desktop.exe"),
        (Join-Path ${env:ProgramW6432} "Docker\Docker\Docker Desktop.exe"),
        (Join-Path ${env:LocalAppData} "Programs\Docker\Docker\Docker Desktop.exe")
    ) | Where-Object { $_ -and $_.Trim().Length -gt 0 }

    foreach ($Candidate in ($Candidates | Select-Object -Unique)) {
        if (Test-Path -LiteralPath $Candidate) {
            return $Candidate
        }
    }
    return $null
}

function Initialize-DockerDaemon {
    if (Test-DockerDaemonReady) {
        return
    }

    $DockerDesktop = Get-DockerDesktopPath
    if (-not $DockerDesktop) {
        throw "Docker daemon is not ready and Docker Desktop.exe was not found."
    }

    Write-Host "Docker daemon is not ready. Starting Docker Desktop..."
    $ExistingDockerDesktop = Get-CimInstance Win32_Process -Filter "Name = 'Docker Desktop.exe'" -ErrorAction SilentlyContinue
    if (-not $ExistingDockerDesktop) {
        Start-Process -FilePath $DockerDesktop -WindowStyle Hidden | Out-Null
    }

    Write-Host "Waiting for Docker daemon" -NoNewline
    $Deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    while ((Get-Date) -lt $Deadline) {
        if (Test-DockerDaemonReady) {
            Write-Host ""
            return
        }
        Start-Sleep -Seconds 4
        Write-Host "." -NoNewline
    }
    Write-Host ""
    throw "Docker Desktop did not become ready within $StartupTimeoutSeconds seconds."
}

function Wait-MySqlReady {
    Write-Host "Waiting for temporary MySQL" -NoNewline
    $Deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    while ((Get-Date) -lt $Deadline) {
        Invoke-WithEnvironment @{ MYSQL_PWD = $RootPassword } {
            & docker exec -e "MYSQL_PWD" $ContainerName mysqladmin ping -uroot --silent *> $null
            if ($LASTEXITCODE -eq 0) {
                Write-Host ""
                return
            }
        }
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Start-Sleep -Seconds 2
        Write-Host "." -NoNewline
    }
    Write-Host ""
    throw "MySQL did not become ready within $StartupTimeoutSeconds seconds."
}

function Get-HostMySqlPort {
    $PortLine = (Invoke-Docker @("port", $ContainerName, "3306/tcp") | Select-Object -First 1).Trim()
    if ($PortLine -notmatch ':(\d+)$') {
        throw "Could not parse Docker MySQL port from '$PortLine'."
    }
    return $Matches[1]
}

function Invoke-MySql([string]$Database, [string]$Sql) {
    $Arguments = @("exec", "-i", "-e", "MYSQL_PWD", $ContainerName, "mysql", "-uroot", "--batch", "--raw", "--skip-column-names")
    if ($Database) {
        $Arguments += $Database
    }
    $Arguments += @("-e", $Sql)
    return Invoke-DockerWithEnvironment @{ MYSQL_PWD = $RootPassword } $Arguments
}

function Invoke-MySqlFile([string]$Database, [string]$Path) {
    $Result = Invoke-WithEnvironment @{ MYSQL_PWD = $RootPassword } {
        $Output = Get-Content -LiteralPath $Path -Raw | & docker exec -i -e "MYSQL_PWD" $ContainerName mysql -uroot $Database 2>&1
        [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = @($Output) }
    }
    $Output = $Result.Output
    $ExitCode = $Result.ExitCode
    if ($ExitCode -ne 0) {
        throw "mysql file execution failed for $Path with exit code $ExitCode.`n$(Format-SafeOutput $Output)"
    }
}

function Invoke-MySqlSql([string]$Database, [string]$Sql) {
    $Result = Invoke-WithEnvironment @{ MYSQL_PWD = $RootPassword } {
        $Output = $Sql | & docker exec -i -e "MYSQL_PWD" $ContainerName mysql -uroot $Database 2>&1
        [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = @($Output) }
    }
    $Output = $Result.Output
    $ExitCode = $Result.ExitCode
    if ($ExitCode -ne 0) {
        throw "mysql SQL execution failed with exit code $ExitCode.`n$(Format-SafeOutput $Output)"
    }
}

function Get-ConsolidatedMigrationSection([string]$SectionName) {
    $Pattern = "(?ms)^-- ===== Section: " + [regex]::Escape($SectionName) + " =====\r?\n(.*?)(?=\r?\n-- ===== Section: |\z)"
    $Match = [regex]::Match($ConsolidatedMigrationContent, $Pattern)
    if (-not $Match.Success) {
        throw "Could not find migration section '$SectionName' in $ConsolidatedMigrationPath."
    }
    return $Match.Groups[1].Value.Trim()
}

function Get-MySqlScalar([string]$Database, [string]$Sql) {
    $Output = Invoke-MySql $Database $Sql
    return (($Output | Select-Object -First 1) -as [string]).Trim()
}

function Assert-Equal([string]$Expected, [string]$Actual, [string]$Label) {
    if ($Actual -ne $Expected) {
        throw "$Label expected '$Expected' but was '$Actual'."
    }
    Write-Host "OK: $Label = $Actual"
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

function Get-ChildProcessIdsRecursive([int]$ParentPid) {
    $Collected = New-Object System.Collections.Generic.List[int]
    function Add-Children([int]$CurrentPid, $Bag) {
        $Children = Get-CimInstance Win32_Process -Filter "ParentProcessId = $CurrentPid" -ErrorAction SilentlyContinue
        foreach ($Child in $Children) {
            $Bag.Add([int]$Child.ProcessId)
            Add-Children -CurrentPid ([int]$Child.ProcessId) -Bag $Bag
        }
    }
    Add-Children -CurrentPid $ParentPid -Bag $Collected
    return @($Collected.ToArray())
}

function Stop-ProcessTree([int]$ProcessId) {
    foreach ($ChildId in (Get-ChildProcessIdsRecursive $ProcessId | Sort-Object -Descending -Unique)) {
        Stop-Process -Id $ChildId -Force -ErrorAction SilentlyContinue
    }
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

function Invoke-FreshSpringBootValidation([string]$HostPort) {
    Write-Section "Fresh DB Flyway + Hibernate validation"
    $Environment = @{
        DB_HOST = "127.0.0.1"
        DB_PORT = $HostPort
        DB_NAME = $FreshDb
        DB_USERNAME = $AppUsername
        DB_PASSWORD = $AppPassword
        JWT_SECRET = $JwtSecret
        SERVER_PORT = "0"
        DEFAULT_ADMIN_EMAIL = ""
        DEFAULT_ADMIN_PASSWORD = ""
    }

    $LogPath = Join-Path ([System.IO.Path]::GetTempPath()) "aichinese-flyway-spring-$Suffix.log"
    if (Test-Path -LiteralPath $LogPath) {
        Remove-Item -LiteralPath $LogPath -Force
    }

    Invoke-WithEnvironment $Environment {
        $MavenCommand = ".\mvnw.cmd -q spring-boot:run ""-Dspring-boot.run.arguments=--server.port=0 --spring.main.banner-mode=off"" > ""$LogPath"" 2>&1"
        $Process = Start-Process -FilePath "cmd.exe" `
            -ArgumentList "/d", "/c", $MavenCommand `
            -WorkingDirectory $BackendDir `
            -PassThru `
            -WindowStyle Hidden
        try {
            Write-Host "Waiting for Spring Boot context" -NoNewline
            $Deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
            while ((Get-Date) -lt $Deadline) {
                if (Test-Path -LiteralPath $LogPath) {
                    $LogText = Get-Content -LiteralPath $LogPath -Raw -ErrorAction SilentlyContinue
                    if ($LogText -match "Started AichineseApplication") {
                        Write-Host ""
                        Write-Host "Spring Boot context started successfully."
                        return
                    }
                    if ($LogText -match "APPLICATION FAILED TO START") {
                        throw "Spring Boot application failed to start.`n$($LogText | Select-Object -Last 1)"
                    }
                }
                if ($Process.HasExited) {
                    $Tail = if (Test-Path -LiteralPath $LogPath) {
                        (Get-Content -LiteralPath $LogPath -Tail 120 -ErrorAction SilentlyContinue) -join "`n"
                    } else {
                        ""
                    }
                    throw "Spring Boot process exited before startup completed with code $($Process.ExitCode).`n$Tail"
                }
                Start-Sleep -Seconds 2
                Write-Host "." -NoNewline
            }
            Write-Host ""
            $Tail = if (Test-Path -LiteralPath $LogPath) {
                (Get-Content -LiteralPath $LogPath -Tail 120 -ErrorAction SilentlyContinue) -join "`n"
            } else {
                ""
            }
            throw "Spring Boot did not start within $StartupTimeoutSeconds seconds.`n$Tail"
        } finally {
            Stop-ProcessTree $Process.Id
        }
    }

    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(*) from information_schema.tables where table_schema = database() and table_name = 'student_quiz_progress';") "fresh student_quiz_progress table"
    Assert-Equal "0" (Get-MySqlScalar $FreshDb "select count(*) from information_schema.tables where table_schema = database() and table_name = 'student_activity_progress';") "fresh student_activity_progress table"
    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(*) from flyway_schema_history where version = '1' and success = 1;") "fresh consolidated V1 migration history"
    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'cart_items' and constraint_name = 'uk_cart_items_cart_item';") "fresh cart item uniqueness"
    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'lesson_progress' and constraint_name = 'uk_lesson_progress_student_lesson';") "fresh lesson progress uniqueness"
    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'points_transactions' and constraint_name = 'uk_points_transactions_student_source';") "fresh point-award source uniqueness"
    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(*) from information_schema.columns where table_schema = database() and table_name = 'points_transactions' and column_name = 'source_key' and extra like '%STORED GENERATED%';") "fresh points source generated column"
    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'password_reset_tokens' and constraint_name = 'uk_password_reset_tokens_active_user';") "fresh active reset token uniqueness"
    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'video_progress' and constraint_name = 'chk_video_progress_watched_within_total';") "fresh video progress consistency check"
    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(distinct index_name) from information_schema.statistics where table_schema = database() and table_name = 'audit_logs' and index_name = 'idx_audit_logs_target_created';") "fresh audit target hot-path index"
    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'ai_chat_messages' and constraint_name = 'chk_ai_chat_messages_sender_role';") "fresh AI chat sender role check"
    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'teacher_earnings' and constraint_name = 'chk_teacher_earnings_status';") "fresh teacher earning status check"
    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'learning_modules' and constraint_name = 'chk_learning_modules_access_price';") "fresh module access price check"
    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'lessons' and constraint_name = 'chk_lessons_access_price';") "fresh lesson access price check"
    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'course_packages' and constraint_name = 'chk_course_packages_access_price';") "fresh package access price check"
    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'teacher_verification_documents' and constraint_name = 'uk_teacher_verification_documents_current_type';") "fresh current teacher verification document uniqueness"
    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(*) from information_schema.columns where table_schema = database() and table_name = 'teacher_verification_documents' and column_name = 'current_document_key' and extra like '%STORED GENERATED%';") "fresh current teacher verification generated column"
    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(distinct index_name) from information_schema.statistics where table_schema = database() and table_name = 'ai_chat_messages' and index_name = 'idx_ai_chat_messages_session_role_id';") "fresh AI chat role lookup index"
    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(distinct index_name) from information_schema.statistics where table_schema = database() and table_name = 'teacher_verification_reviews' and index_name = 'idx_teacher_verification_reviews_latest';") "fresh teacher verification review latest index"
    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(distinct index_name) from information_schema.statistics where table_schema = database() and table_name = 'notifications' and index_name = 'idx_notifications_user_created_id';") "fresh notification inbox hot-path index"
    Assert-Equal "1" (Get-MySqlScalar $FreshDb "select count(distinct index_name) from information_schema.statistics where table_schema = database() and table_name = 'notifications' and index_name = 'idx_notifications_user_status_created_id';") "fresh notification unread hot-path index"
    Assert-Equal "0" (Get-MySqlScalar $FreshDb "select count(distinct index_name) from information_schema.statistics where table_schema = database() and table_name = 'notifications' and index_name = 'idx_notifications_user_status_created_at';") "fresh notification redundant unread index removed"
}

function Invoke-RepairScript([string]$HostPort, [switch]$Execute) {
    $RepairScript = Join-Path $ScriptDir "repair-flyway-checksums.ps1"
    $EscapedRepairScript = $RepairScript.Replace("'", "''")
    $Command = "`$pwd = ConvertTo-SecureString `$env:AICHINESE_FLYWAY_REPAIR_PASSWORD -AsPlainText -Force; & '$EscapedRepairScript' -DbHost 127.0.0.1 -DbPort $HostPort -DbName $FreshDb -DbUsername $AppUsername -DbPassword `$pwd"
    if ($Execute) {
        $Command += " -Execute -ConfirmDatabaseName $FreshDb"
    }
    return Invoke-WithEnvironment @{ AICHINESE_FLYWAY_REPAIR_PASSWORD = $AppPassword } {
        $Output = & powershell -NoProfile -ExecutionPolicy Bypass -Command $Command 2>&1
        $ExitCode = $LASTEXITCODE
        [pscustomobject]@{
            ExitCode = $ExitCode
            Output = @($Output)
        }
    }
}

function Show-RepairOutput($Result) {
    foreach ($Line in $Result.Output) {
        if ($null -ne $Line -and "$Line".Trim().Length -gt 0) {
            Write-Host $Line
        }
    }
}

function Invoke-ChecksumRepairValidation([string]$HostPort) {
    Write-Section "Flyway checksum repair validation"

    $BeforeChecksum = Get-MySqlScalar $FreshDb "select checksum from flyway_schema_history where version = '1';"
    Invoke-MySql $FreshDb "update flyway_schema_history set checksum = coalesce(checksum, 0) + 1 where version = '1';" | Out-Null
    $BrokenChecksum = Get-MySqlScalar $FreshDb "select checksum from flyway_schema_history where version = '1';"
    if ($BrokenChecksum -eq $BeforeChecksum) {
        throw "Failed to simulate Flyway checksum mismatch for consolidated V1."
    }
    Write-Host "Simulated consolidated V1 checksum mismatch."

    $DryRun = Invoke-RepairScript $HostPort
    Show-RepairOutput $DryRun
    if ($DryRun.ExitCode -eq 0 -or (($DryRun.Output -join "`n") -notmatch "No changes were made")) {
        throw "Repair dry-run did not stop safely before modifying the database. Exit code: $($DryRun.ExitCode)."
    }
    Assert-Equal $BrokenChecksum (Get-MySqlScalar $FreshDb "select checksum from flyway_schema_history where version = '1';") "dry-run preserved broken checksum"

    $Repair = Invoke-RepairScript $HostPort -Execute
    Show-RepairOutput $Repair
    if ($Repair.ExitCode -ne 0) {
        throw "Repair execute failed with exit code $($Repair.ExitCode)."
    }

    $AfterChecksum = Get-MySqlScalar $FreshDb "select checksum from flyway_schema_history where version = '1';"
    if ($AfterChecksum -eq $BrokenChecksum) {
        throw "Flyway repair did not update the simulated broken checksum."
    }
    Write-Host "OK: Flyway repair updated consolidated V1 checksum from $BrokenChecksum to $AfterChecksum"
}

function Invoke-FoldedCleanupCompatibilityValidation {
    Write-Section "Folded cleanup SQL compatibility validation"
    Invoke-MySql "" "create database $LegacyDb;" | Out-Null

    $LegacySetup = @"
create table student_profiles (
    id bigint not null,
    primary key (id)
) engine=InnoDB;

create table lessons (
    id bigint not null,
    primary key (id)
) engine=InnoDB;

create table package_items (
    id bigint not null auto_increment,
    item_type enum ('MODULE','LESSON','ACTIVITY') not null,
    primary key (id)
) engine=InnoDB;

create table student_activity_progress (
    attempt_count integer not null,
    completed bit not null,
    activity_source_id bigint not null,
    completed_at datetime(6),
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    last_interacted_at datetime(6),
    lesson_id bigint not null,
    started_at datetime(6),
    student_id bigint not null,
    updated_at datetime(6) not null,
    activity_type varchar(50) not null,
    completion_summary varchar(500),
    primary key (id)
) engine=InnoDB;

insert into student_profiles (id) values (10);
insert into lessons (id) values (20);
insert into package_items (item_type) values ('MODULE'), ('LESSON'), ('ACTIVITY');
insert into student_activity_progress (
    attempt_count,
    completed,
    activity_source_id,
    completed_at,
    created_at,
    last_interacted_at,
    lesson_id,
    started_at,
    student_id,
    updated_at,
    activity_type,
    completion_summary
) values
    (2, b'1', 1001, now(6), now(6), now(6), 20, now(6), 10, now(6), 'QUIZ', 'legacy quiz progress'),
    (1, b'1', 2002, now(6), now(6), now(6), 20, now(6), 10, now(6), 'FLASHCARD', 'legacy activity progress');
"@
    Invoke-MySql $LegacyDb $LegacySetup | Out-Null

    Invoke-MySqlSql $LegacyDb (Get-ConsolidatedMigrationSection "package item activity cleanup")
    Invoke-MySqlSql $LegacyDb (Get-ConsolidatedMigrationSection "student quiz progress consolidation")

    $PackageItemType = Get-MySqlScalar $LegacyDb "select column_type from information_schema.columns where table_schema = database() and table_name = 'package_items' and column_name = 'item_type';"
    if ($PackageItemType -like "*'ACTIVITY'*") {
        throw "legacy package_items.item_type still allows ACTIVITY: $PackageItemType"
    }
    Write-Host "OK: legacy package_items.item_type = $PackageItemType"

    Assert-Equal "0" (Get-MySqlScalar $LegacyDb "select count(*) from information_schema.tables where table_schema = database() and table_name = 'student_activity_progress';") "legacy student_activity_progress table"
    Assert-Equal "1" (Get-MySqlScalar $LegacyDb "select count(*) from student_quiz_progress;") "legacy migrated quiz progress rows"
    Assert-Equal "1001" (Get-MySqlScalar $LegacyDb "select quiz_id from student_quiz_progress;") "legacy migrated quiz_id"
    Assert-Equal "1" (Get-MySqlScalar $LegacyDb "select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'student_quiz_progress' and constraint_name = 'uk_student_quiz_progress_student_quiz';") "legacy student_quiz_progress unique constraint"
    Assert-Equal "2" (Get-MySqlScalar $LegacyDb "select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'student_quiz_progress' and constraint_type = 'FOREIGN KEY';") "legacy student_quiz_progress foreign keys"
}

$StartedContainer = $false
try {
    Write-Section "Docker"
    Initialize-DockerDaemon

    Write-Section "Temporary MySQL"
    Invoke-DockerWithEnvironment @{
        MYSQL_ROOT_PASSWORD = $RootPassword
        MYSQL_DATABASE = $FreshDb
        MYSQL_USER = $AppUsername
        MYSQL_PASSWORD = $AppPassword
    } @("run", "--name", $ContainerName, "-e", "MYSQL_ROOT_PASSWORD", "-e", "MYSQL_DATABASE", "-e", "MYSQL_USER", "-e", "MYSQL_PASSWORD", "-p", "127.0.0.1::3306", "-d", $Image) | Out-Null
    $StartedContainer = $true
    Wait-MySqlReady
    $HostPort = Get-HostMySqlPort
    Write-Host "Temporary MySQL is available on 127.0.0.1:$HostPort"

    Invoke-FreshSpringBootValidation $HostPort
    Invoke-ChecksumRepairValidation $HostPort
    Invoke-FoldedCleanupCompatibilityValidation

    Write-Section "Result"
    Write-Host "Flyway MySQL validation passed." -ForegroundColor Green
    exit 0
} catch {
    Write-Host ""
    Write-Host "Flyway MySQL validation failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally {
    if ($StartedContainer -and -not $KeepContainer) {
        try {
            Invoke-Docker @("rm", "-f", $ContainerName) | Out-Null
        } catch {
        }
    } elseif ($StartedContainer) {
        Write-Host "Temporary MySQL container kept: $ContainerName"
    }
}
