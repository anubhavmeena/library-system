#Requires -Version 5.1
<#
.SYNOPSIS
    Install and run Target Zone Library on a Windows Server.
.DESCRIPTION
    Checks Docker prerequisites, generates .env with credentials,
    starts infrastructure, seeds the database, then builds and starts
    all microservices and the frontend in the correct order.
.NOTES
    Requires Docker Desktop to be installed first.
    Download: https://docs.docker.com/desktop/install/windows-install/
#>

$ErrorActionPreference = "Stop"

# ---- Helpers -----------------------------------------------------------------

function Write-Step { Write-Host "`n=====  $args  =====" -ForegroundColor Cyan }
function Write-Ok   { Write-Host "  [+] $args" -ForegroundColor Green }
function Write-Info { Write-Host "  [.] $args" -ForegroundColor Cyan }
function Write-Warn { Write-Host "  [!] $args" -ForegroundColor Yellow }
function Write-Err  { Write-Host "  [x] $args" -ForegroundColor Red; exit 1 }

function Read-Prompt {
    param([string]$Label, [string]$Default = "")
    if ($Default) { $display = "$Label [$Default]" } else { $display = $Label }
    $val = Read-Host "  [?] $display"
    if ([string]::IsNullOrWhiteSpace($val)) { return $Default }
    return $val
}

function New-HexSecret {
    param([int]$Bytes = 32)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $buf = New-Object byte[] $Bytes
    $rng.GetBytes($buf)
    return ($buf | ForEach-Object { $_.ToString("x2") }) -join ""
}

function Wait-Healthy {
    param([string]$ContainerName, [int]$MaxSeconds = 120)
    Write-Info "Waiting for $ContainerName..."
    $elapsed = 0
    while ($elapsed -lt $MaxSeconds) {
        try {
            $status = docker inspect --format "{{.State.Health.Status}}" $ContainerName 2>$null
            if ($status -eq "healthy") {
                Write-Host ""
                Write-Ok "$ContainerName is healthy."
                return
            }
        } catch {}
        Write-Host -NoNewline "."
        Start-Sleep -Seconds 5
        $elapsed += 5
    }
    Write-Host ""
    Write-Err "$ContainerName did not become healthy after ${MaxSeconds}s. Check: docker logs $ContainerName"
}

# ---- Step 1: Prerequisites ---------------------------------------------------
Write-Step "Step 1 - Prerequisites"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Err "Docker not found. Install Docker Desktop: https://docs.docker.com/desktop/install/windows-install/"
}
Write-Ok "Docker: $(docker --version)"

$compose = $null
try { docker compose version | Out-Null; $compose = "docker compose" } catch {}
if (-not $compose) {
    if (Get-Command docker-compose -ErrorAction SilentlyContinue) {
        $compose = "docker-compose"
        Write-Warn "Using docker-compose v1. Upgrading to v2 is recommended."
    } else {
        Write-Err "Docker Compose not found. Update Docker Desktop to a recent version."
    }
}
Write-Ok "Compose: $(Invoke-Expression "$compose version" | Select-Object -First 1)"

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Err "Git not found. Install from https://git-scm.com/download/win"
}
Write-Ok "Git: $(git --version)"

# ---- Step 2: Repository ------------------------------------------------------
Write-Step "Step 2 - Repository"

if (-not (Test-Path "docker-compose.infra.yml")) {
    Write-Warn "Not in the repo root (docker-compose.infra.yml not found)."
    $cloneUrl = Read-Prompt "Git clone URL (blank to abort)"
    if ([string]::IsNullOrWhiteSpace($cloneUrl)) {
        Write-Err "Aborted. Re-run from the repository root."
    }
    $cloneDir = Read-Prompt "Clone into directory" "library-system"
    git clone $cloneUrl $cloneDir
    Set-Location $cloneDir
}
Write-Ok "Repo root: $(Get-Location)"

# ---- Step 3: Environment (.env) ----------------------------------------------
Write-Step "Step 3 - Environment configuration"

$adminMobileNew = ""
$adminEmailNew  = ""

if (Test-Path ".env") {
    Write-Warn ".env already exists - skipping. Delete it to reconfigure from scratch."
} else {
    $jwtSecret = New-HexSecret -Bytes 32
    Write-Ok "JWT_SECRET generated (64 hex chars)."

    Write-Host ""
    Write-Info "--- Admin account ---"
    Write-Info "Seed default: mobile=9999999999 / email=admin@targetzone.co.in"
    Write-Info "Press Enter to keep defaults."
    $rawMobile = Read-Prompt "Admin mobile" "9999999999"
    $rawEmail  = Read-Prompt "Admin email"  "admin@targetzone.co.in"
    if ($rawMobile -ne "9999999999")             { $adminMobileNew = $rawMobile }
    if ($rawEmail  -ne "admin@targetzone.co.in") { $adminEmailNew  = $rawEmail }

    Write-Host ""
    Write-Info "--- Twilio: OTP and WhatsApp ---"
    Write-Info "Leave blank for dev mode (OTP = '123456', messages logged only)."
    $tSid   = Read-Prompt "TWILIO_ACCOUNT_SID"
    $tToken = Read-Prompt "TWILIO_AUTH_TOKEN"
    $tPhone = Read-Prompt "TWILIO_PHONE_NUMBER (e.g. +911234567890)"
    $tWa    = Read-Prompt "TWILIO_WHATSAPP_FROM" "whatsapp:+14155238886"

    Write-Host ""
    Write-Info "--- SendGrid: email notifications ---"
    Write-Info "Leave blank: emails are logged only, not delivered."
    $sgKey       = Read-Prompt "SENDGRID_API_KEY"
    $sgFromEmail = Read-Prompt "FROM_EMAIL"  "noreply@targetzone.co.in"
    $sgFromName  = Read-Prompt "FROM_NAME"   "Target Zone Library"
    $sgAdminMail = Read-Prompt "ADMIN_EMAIL for alert notifications" "admin@targetzone.co.in"
    $sgAdminWa   = Read-Prompt "ADMIN_WHATSAPP (e.g. whatsapp:+919876543210)"

    Write-Host ""
    Write-Info "--- Razorpay: payments ---"
    Write-Info "Leave blank: dev_order_* IDs used, HMAC verification skipped."
    $rpyId     = Read-Prompt "RAZORPAY_KEY_ID"
    $rpySecret = Read-Prompt "RAZORPAY_KEY_SECRET"

    $timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd HH:mm UTC")

    # Build .env as a string array to avoid here-string encoding issues
    $envLines = @(
        "# Target Zone Library - Environment Variables",
        "# Generated by deploy.ps1 on $timestamp",
        "",
        "# JWT secret - shared by api-gateway and auth-service (must match in both)",
        "JWT_SECRET=$jwtSecret",
        "",
        "# Twilio - OTP and WhatsApp (blank = dev mode: OTP is always '123456')",
        "TWILIO_ACCOUNT_SID=$tSid",
        "TWILIO_AUTH_TOKEN=$tToken",
        "TWILIO_PHONE_NUMBER=$tPhone",
        "TWILIO_WHATSAPP_FROM=$tWa",
        "",
        "# SendGrid - email notifications (blank = emails logged but not sent)",
        "SENDGRID_API_KEY=$sgKey",
        "FROM_EMAIL=$sgFromEmail",
        "FROM_NAME=$sgFromName",
        "ADMIN_EMAIL=$sgAdminMail",
        "ADMIN_WHATSAPP=$sgAdminWa",
        "",
        "# Razorpay - payments (blank = dev_order_* IDs generated, HMAC skipped)",
        "RAZORPAY_KEY_ID=$rpyId",
        "RAZORPAY_KEY_SECRET=$rpySecret"
    )

    # Write UTF-8 without BOM so Docker Compose parses the file correctly
    $utf8noBom = New-Object System.Text.UTF8Encoding $false
    $envPath   = [System.IO.Path]::GetFullPath(".env")
    [System.IO.File]::WriteAllLines($envPath, $envLines, $utf8noBom)
    Write-Ok ".env written."
}

# ---- Step 4: Start infrastructure --------------------------------------------
Write-Step "Step 4 - Starting infrastructure (PostgreSQL, Redis, Zookeeper, Kafka)"

Invoke-Expression "$compose -f docker-compose.infra.yml up -d"
Write-Ok "Infrastructure containers started."

# ---- Step 5: Wait for infra health, then seed the database -------------------
Write-Step "Step 5 - Database initialisation"

Wait-Healthy "library-postgres" 120
Wait-Healthy "library-redis"     60
Wait-Healthy "library-kafka"    180

# PostgreSQL automatically runs docker-entrypoint-initdb.d on first start:
#   01_schema.sql - creates all tables (users, memberships, seats, payments, ...)
#   02_seed.sql   - admin user, 2 membership plans, 110 seats (idempotent)
Write-Info "Schema and seed applied automatically on first PostgreSQL start."

if ($adminMobileNew -or $adminEmailNew) {
    Write-Info "Updating admin credentials in the database..."
    $setParts = [System.Collections.Generic.List[string]]::new()
    if ($adminMobileNew) { $setParts.Add("mobile = '$adminMobileNew'") }
    if ($adminEmailNew)  { $setParts.Add("email = '$adminEmailNew'") }
    $setParts.Add("updated_at = NOW()")
    $setClause = $setParts -join ", "
    $sql = "UPDATE users SET $setClause WHERE id = 'a0000000-0000-0000-0000-000000000001';"
    docker exec library-postgres psql -U library_user -d library_db -c $sql
    Write-Ok "Admin credentials updated."
} else {
    Write-Info "Keeping default admin: mobile=9999999999, email=admin@targetzone.co.in"
}

# ---- Step 6: Build and start application services ----------------------------
Write-Step "Step 6 - Building and starting application services"
Write-Info "First run compiles 7 Spring Boot services - allow 5 to 10 minutes."

Invoke-Expression "$compose -f docker-compose.services.yml up -d --build"
Write-Ok "All application containers started."

# ---- Step 7: Wait for application health -------------------------------------
Write-Step "Step 7 - Waiting for application services"

$appServices = @(
    @{ Name = "library-api-gateway";          Timeout = 300 }
    @{ Name = "library-auth-service";         Timeout = 300 }
    @{ Name = "library-user-service";         Timeout = 300 }
    @{ Name = "library-membership-service";   Timeout = 300 }
    @{ Name = "library-seat-service";         Timeout = 300 }
    @{ Name = "library-notification-service"; Timeout = 300 }
    @{ Name = "library-admin-service";        Timeout = 300 }
    @{ Name = "library-frontend";             Timeout = 120 }
)

foreach ($svc in $appServices) {
    Write-Info "Waiting for $($svc.Name)..."
    $elapsed = 0
    $healthy = $false
    while ($elapsed -lt $svc.Timeout) {
        try {
            $status = docker inspect --format "{{.State.Health.Status}}" $svc.Name 2>$null
            if ($status -eq "healthy") {
                Write-Host ""
                Write-Ok "$($svc.Name) healthy."
                $healthy = $true
                break
            }
            if ($status -eq "unhealthy") {
                Write-Host ""
                Write-Warn "$($svc.Name) is unhealthy. Check: docker logs $($svc.Name)"
                break
            }
        } catch {}
        Write-Host -NoNewline "."
        Start-Sleep -Seconds 10
        $elapsed += 10
    }
    if (-not $healthy -and $elapsed -ge $svc.Timeout) {
        Write-Host ""
        Write-Warn "$($svc.Name) not yet healthy after $($svc.Timeout)s - may still be starting."
        Write-Warn "Check: docker logs $($svc.Name)"
    }
}

# ---- Step 8: Summary ---------------------------------------------------------
$serverIp = (
    Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.InterfaceAlias -notmatch "Loopback|WSL|vEthernet" } |
    Select-Object -First 1
).IPAddress
if (-not $serverIp) { $serverIp = "localhost" }

$dispMob   = if ($adminMobileNew) { $adminMobileNew } else { "9999999999" }
$dispEmail = if ($adminEmailNew)  { $adminEmailNew  } else { "admin@targetzone.co.in" }

Write-Host ""
Write-Host "======================================================" -ForegroundColor Green
Write-Host "  Target Zone Library - Deployment Complete!" -ForegroundColor Green
Write-Host "======================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Access points" -ForegroundColor White
Write-Host "  Frontend     http://${serverIp}:3000"
Write-Host "  API Gateway  http://${serverIp}:8080"
Write-Host "  Kafka UI     http://${serverIp}:8090"
Write-Host ""
Write-Host "  Admin login" -ForegroundColor White
Write-Host "  Mobile : $dispMob"
Write-Host "  Email  : $dispEmail"
Write-Host "  OTP    : 123456 (dev mode) | real SMS if Twilio is configured"
Write-Host ""
Write-Host "  Useful commands" -ForegroundColor White
Write-Host "  Tail logs    : $compose -f docker-compose.services.yml logs -f"
Write-Host "  Stop app     : $compose -f docker-compose.services.yml down"
Write-Host "  Stop + infra : $compose -f docker-compose.services.yml down; $compose -f docker-compose.infra.yml down"
Write-Host "  Wipe data    : $compose -f docker-compose.infra.yml down -v   (deletes all volumes)"
Write-Host "  Rebuild      : $compose -f docker-compose.services.yml up -d --build"
Write-Host ""
