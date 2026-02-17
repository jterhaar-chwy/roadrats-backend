# Road Rats Backend - Environment Setup
# Uses Windows Integrated Authentication (no username/password needed)

$env:CLS_DB_URL="jdbc:sqlserver://wmssql-cls-staging:1433;databaseName=DMSServer_320;encrypt=true;trustServerCertificate=true;integratedSecurity=true"
$env:IO_DB_URL="jdbc:sqlserver://wmssql-io-test:1433;databaseName=AAD_IMPORT_ORDER;encrypt=true;trustServerCertificate=true;integratedSecurity=true"

Write-Host "Environment variables set:" -ForegroundColor Green
Write-Host "  CLS_DB_URL = $env:CLS_DB_URL"
Write-Host "  IO_DB_URL  = $env:IO_DB_URL"
Write-Host ""
Write-Host "Using Windows Integrated Authentication (integratedSecurity=true)" -ForegroundColor Cyan
Write-Host "Make sure sqljdbc_auth.dll is in build/native-libs/" -ForegroundColor Yellow
