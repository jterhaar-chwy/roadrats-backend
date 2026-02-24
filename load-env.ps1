# Road Rats Backend - Environment Setup
# Uses Windows Integrated Authentication (no username/password needed)

$env:CLS_DB_URL="jdbc:sqlserver://wmssql-cls:1433;databaseName=DMSServer;encrypt=true;trustServerCertificate=true;integratedSecurity=true"
$env:IO_DB_URL="jdbc:sqlserver://wmssql-io:1433;databaseName=AAD_IMPORT_ORDER;encrypt=true;trustServerCertificate=true;integratedSecurity=true"

# Jira / Release Manager Configuration
# Reads credentials from the existing catdog jira.ini config file
$JiraConfigPath = "E:\Repos\wms-deployments\catdog\Config\jira.ini"
If (Test-Path $JiraConfigPath) {
    $JiraConfig = Get-Content $JiraConfigPath | Where-Object { $_ -match "=" } | ConvertFrom-StringData
    $env:JIRA_USER = $JiraConfig.User
    $env:JIRA_TOKEN = $JiraConfig.Token
    Write-Host "Jira credentials loaded from jira.ini" -ForegroundColor Green
} Else {
    Write-Host "WARNING: Jira config not found at $JiraConfigPath" -ForegroundColor Yellow
    Write-Host "  Set JIRA_USER and JIRA_TOKEN manually for Release Manager features" -ForegroundColor Yellow
}

# Load OPENAI_API_KEY from .env file
$EnvFile = Join-Path $PSScriptRoot ".env.local"
If (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        If ($_ -match '^\s*OPENAI_API_KEY\s*=\s*(.+)$') {
            $env:OPENAI_API_KEY = $Matches[1].Trim().Trim('"').Trim("'")
        }
    }
    If ($env:OPENAI_API_KEY) {
        Write-Host "OPENAI_API_KEY loaded from .env" -ForegroundColor Green
    } Else {
        Write-Host "WARNING: OPENAI_API_KEY not found in .env file" -ForegroundColor Yellow
    }
} Else {
    Write-Host "WARNING: .env file not found at $EnvFile" -ForegroundColor Yellow
}

$EnvFile = Join-Path $PSScriptRoot ".env.local"
If (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        If ($_ -match '^\s*GITHUB_REPO\s*=\s*(.+)$') {
            $env:GITHUB_REPO = $Matches[1].Trim().Trim('"').Trim("'")
        }
    }
    If ($env:GITHUB_REPO) {
        Write-Host "GITHUB_REPO loaded from .env" -ForegroundColor Green
    } Else {
        Write-Host "WARNING: GITHUB_REPO not found in .env file" -ForegroundColor Yellow
    }
} Else {
    Write-Host "WARNING: .env file not found at $EnvFile" -ForegroundColor Yellow
}


Write-Host "Environment variables set:" -ForegroundColor Green
Write-Host "  CLS_DB_URL  = $env:CLS_DB_URL"
Write-Host "  IO_DB_URL   = $env:IO_DB_URL"
Write-Host "  JIRA_USER   = $env:JIRA_USER"
Write-Host "  JIRA_TOKEN  = $(If ($env:JIRA_TOKEN) { '[SET]' } Else { '[NOT SET]' })"
Write-Host "  OPENAI_KEY  = $(If ($env:OPENAI_API_KEY) { '[SET]' } Else { '[NOT SET]' })"
Write-Host "  GITHUB_REPO = $(If ($env:GITHUB_REPO) { '[SET]' } Else { '[NOT SET]' })"
Write-Host ""
Write-Host "Using Windows Integrated Authentication (integratedSecurity=true)" -ForegroundColor Cyan
Write-Host "Make sure sqljdbc_auth.dll is in build/native-libs/" -ForegroundColor Yellow
