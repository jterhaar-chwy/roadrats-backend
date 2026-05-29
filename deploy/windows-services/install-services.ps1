param(
    [string]$ServiceAccount = 'CHEWY\g-hjservicedev$',
    [switch]$UseGmsa = $true
)

$ErrorActionPreference = 'Stop'

$backendRoot = 'E:\Program Files\Koerber\WebService\WMS360\backend'
$frontendRoot = 'E:\Program Files\Koerber\WebService\WMS360\frontend'
$backendServiceName = 'WMS360Backend'
$frontendServiceName = 'WMS360Frontend'

function Assert-PathExists {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "$Description not found: $Path"
    }
}

function ConvertTo-PlainText {
    param(
        [Parameter(Mandatory = $true)]
        [Security.SecureString]$SecureString
    )

    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureString)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

function Normalize-ServiceAccountName {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Username
    )

    if ($Username -notmatch '[\\@]' -and $env:USERDOMAIN) {
        return "$env:USERDOMAIN\$Username"
    }

    return $Username
}

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string[]]$ArgumentList,
        [Parameter(Mandatory = $true)]
        [string]$FailureMessage
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $FilePath @ArgumentList 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($exitCode -ne 0) {
        $details = ($output | Out-String).Trim()
        if ($details) {
            throw "$FailureMessage`n$details"
        }

        throw "$FailureMessage (exit code $exitCode)"
    }

    if ($output) {
        $output | ForEach-Object { Write-Host $_ }
    }
}

function Get-ServiceChangeResultMessage {
    param(
        [Parameter(Mandatory = $true)]
        [int]$ReturnValue
    )

    switch ($ReturnValue) {
        0 { return 'Success' }
        2 { return 'Access denied' }
        3 { return 'Dependent services running' }
        8 { return 'Unknown failure' }
        11 { return 'Service database locked' }
        15 { return 'Service logon failed' }
        16 { return 'Service marked for deletion' }
        21 { return 'Invalid parameter' }
        22 { return 'Invalid service account' }
        default { return "WMI return code $ReturnValue" }
    }
}

function Set-ServiceLogonAccount {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ServiceName,
        [Parameter(Mandatory = $true)]
        [string]$Username,
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Password,
        [switch]$UseGmsa
    )

    Write-Host "Setting service logon for $ServiceName to $Username"
    $service = Get-CimInstance -ClassName Win32_Service -Filter "Name='$ServiceName'"
    if (-not $service) {
        throw "Service not found: $ServiceName"
    }

    $arguments = @{
        StartName = $Username
    }

    if ($UseGmsa) {
        $arguments.StartPassword = $null
    }
    else {
        $arguments.StartPassword = $Password
    }

    $result = Invoke-CimMethod -InputObject $service -MethodName Change -Arguments $arguments
    if ($result.ReturnValue -ne 0) {
        $message = Get-ServiceChangeResultMessage -ReturnValue $result.ReturnValue
        throw "Failed to set logon account for $ServiceName ($message)"
    }
}

function Install-WinSwService {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ServiceExePath,
        [Parameter(Mandatory = $true)]
        [string]$ServiceName,
        [Parameter(Mandatory = $true)]
        [string]$Username,
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Password,
        [switch]$UseGmsa
    )

    $existing = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
    if ($existing) {
        Write-Host "Service $ServiceName already exists. Updating configuration..." -ForegroundColor Yellow
        if ($existing.Status -ne 'Stopped') {
            Invoke-NativeCommand `
                -FilePath 'sc.exe' `
                -ArgumentList @('stop', $ServiceName) `
                -FailureMessage "Failed to stop service $ServiceName"

            $existing.WaitForStatus('Stopped', [TimeSpan]::FromSeconds(30))
        }
    }
    else {
        Invoke-NativeCommand `
            -FilePath $ServiceExePath `
            -ArgumentList @('install') `
            -FailureMessage "Failed to install service $ServiceName"
    }

    if ($UseGmsa -and $Username -notmatch '\$$') {
        throw "gMSA account names must end with `$: $Username"
    }

    Set-ServiceLogonAccount `
        -ServiceName $ServiceName `
        -Username $Username `
        -Password $Password `
        -UseGmsa:$UseGmsa

    Invoke-NativeCommand `
        -FilePath 'sc.exe' `
        -ArgumentList @('failure', $ServiceName, 'reset=', '86400', 'actions=', 'restart/10000/restart/30000/restart/60000') `
        -FailureMessage "Failed to configure recovery actions for $ServiceName"

    Set-Service -Name $ServiceName -StartupType Automatic
}

function Start-WinSwService {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ServiceName
    )

    Write-Host "Starting service $ServiceName"
    try {
        Invoke-NativeCommand `
            -FilePath 'sc.exe' `
            -ArgumentList @('start', $ServiceName) `
            -FailureMessage "Failed to start service $ServiceName"
    }
    catch {
        if ($_.Exception.Message -match '\b1069\b') {
            $gmsaHint = if ($ServiceAccount -and $UseGmsa) {
                '- confirm this server is allowed to retrieve the gMSA password and Test-ADServiceAccount succeeds'
            }
            else {
                '- verify the password is current and the account is not locked'
            }

            throw @"
Failed to start service $ServiceName because Windows could not log on with the configured service account.

Fix the service account, then rerun this installer:
- verify the username is DOMAIN\username or user@domain
$gmsaHint
- grant the account the "Log on as a service" right on this server

$($_.Exception.Message)
"@
        }

        throw
    }
}

Assert-PathExists -Path $backendRoot -Description 'Backend root'
Assert-PathExists -Path $frontendRoot -Description 'Frontend root'
Assert-PathExists -Path (Join-Path $backendRoot 'WMS360Backend.exe') -Description 'Backend WinSW executable'
Assert-PathExists -Path (Join-Path $backendRoot 'WMS360Backend.xml') -Description 'Backend WinSW config'
Assert-PathExists -Path (Join-Path $backendRoot 'start-backend.cmd') -Description 'Backend start script'
Assert-PathExists -Path (Join-Path $backendRoot 'demo-0.0.1-SNAPSHOT.jar') -Description 'Backend jar'
Assert-PathExists -Path (Join-Path $frontendRoot 'WMS360Frontend.exe') -Description 'Frontend WinSW executable'
Assert-PathExists -Path (Join-Path $frontendRoot 'WMS360Frontend.xml') -Description 'Frontend WinSW config'
Assert-PathExists -Path (Join-Path $frontendRoot 'start-frontend.cmd') -Description 'Frontend start script'
Assert-PathExists -Path (Join-Path $frontendRoot '.next') -Description 'Frontend build output'
Assert-PathExists -Path (Join-Path $frontendRoot 'node_modules') -Description 'Frontend node_modules'

if ($ServiceAccount) {
    $serviceUsername = Normalize-ServiceAccountName -Username $ServiceAccount
    if ($UseGmsa) {
        $plainPassword = ''
        Write-Host "Using gMSA service account $serviceUsername"
    }
    else {
        $credential = Get-Credential -UserName $serviceUsername -Message 'Enter the Windows service account password'
        $plainPassword = ConvertTo-PlainText -SecureString $credential.Password
    }
}
else {
    $defaultUser = "$env:USERDOMAIN\$env:USERNAME"
    $credential = Get-Credential -UserName $defaultUser -Message 'Enter the Windows account to run the WMS360 services'
    $serviceUsername = Normalize-ServiceAccountName -Username $credential.UserName
    $plainPassword = ConvertTo-PlainText -SecureString $credential.Password
}

try {
    Install-WinSwService `
        -ServiceExePath (Join-Path $backendRoot 'WMS360Backend.exe') `
        -ServiceName $backendServiceName `
        -Username $serviceUsername `
        -Password $plainPassword `
        -UseGmsa:$UseGmsa

    Install-WinSwService `
        -ServiceExePath (Join-Path $frontendRoot 'WMS360Frontend.exe') `
        -ServiceName $frontendServiceName `
        -Username $serviceUsername `
        -Password $plainPassword `
        -UseGmsa:$UseGmsa
}
finally {
    if ($plainPassword) {
        $plainPassword = $null
    }
}

Start-WinSwService -ServiceName $backendServiceName
Start-WinSwService -ServiceName $frontendServiceName

Write-Host 'Services installed and started:' -ForegroundColor Green
Get-Service -Name $backendServiceName, $frontendServiceName | Format-Table -AutoSize
