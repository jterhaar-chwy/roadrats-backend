param(
    [string]$ServerName = 'FLL2FHJIS02TST',
    [string]$BackendRepoRoot = 'E:\Repos\roadrats-backend',
    [string]$FrontendRepoRoot = 'E:\Repos\roadrats-frontend',
    [string]$RemoteDeployRoot = "\\FLL2FHJIS02TST\E$\Program Files\Koerber\WebService\WMS360",
    [string]$LocalStageRoot = 'E:\Temp\WMS360\staging',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

if ($PSBoundParameters.ContainsKey('ServerName') -and -not $PSBoundParameters.ContainsKey('RemoteDeployRoot')) {
    $RemoteDeployRoot = "\\$ServerName\E$\Program Files\Koerber\WebService\WMS360"
}

$backendStageRoot = Join-Path $LocalStageRoot 'backend'
$frontendStageRoot = Join-Path $LocalStageRoot 'frontend'
$remoteBackendRoot = Join-Path $RemoteDeployRoot 'backend'
$remoteFrontendRoot = Join-Path $RemoteDeployRoot 'frontend'
$serviceFilesRoot = Join-Path $BackendRepoRoot 'deploy\windows-services'

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

function Ensure-Directory {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
}

function Invoke-Step {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message,
        [Parameter(Mandatory = $true)]
        [scriptblock]$Action
    )

    Write-Host $Message -ForegroundColor Cyan
    & $Action
}

function Invoke-CommandInDirectory {
    param(
        [Parameter(Mandatory = $true)]
        [string]$WorkingDirectory,
        [Parameter(Mandatory = $true)]
        [scriptblock]$Action
    )

    Push-Location $WorkingDirectory
    try {
        & $Action
    }
    finally {
        Pop-Location
    }
}

function Invoke-NativeProcess {
    param(
        [Parameter(Mandatory = $true)]
        [scriptblock]$Action
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Action
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Invoke-RobocopyMirror {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Source,
        [Parameter(Mandatory = $true)]
        [string]$Destination
    )

    Assert-PathExists -Path $Source -Description 'Mirror source'
    Ensure-Directory -Path $Destination

    Write-Host "Replacing directory contents: $Source -> $Destination"
    & robocopy $Source $Destination /MIR /R:1 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null

    if ($LASTEXITCODE -ge 8) {
        throw "Failed to mirror directory contents: $Source -> $Destination (robocopy exit code $LASTEXITCODE)"
    }
}

function Copy-FileWithMessage {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Source,
        [Parameter(Mandatory = $true)]
        [string]$Destination
    )

    Assert-PathExists -Path $Source -Description 'Copy source'
    Ensure-Directory -Path (Split-Path -Parent $Destination)
    Copy-Item -LiteralPath $Source -Destination $Destination -Force
    Write-Host "Copied file: $Source -> $Destination"
}

function Stage-FilesLocally {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BackendRepoRoot,
        [Parameter(Mandatory = $true)]
        [string]$FrontendRepoRoot,
        [Parameter(Mandatory = $true)]
        [string]$LocalStageRoot,
        [Parameter(Mandatory = $true)]
        [string]$ServiceFilesRoot
    )

    $backendJar = Join-Path $BackendRepoRoot 'build\libs\demo-0.0.1-SNAPSHOT.jar'
    $backendNativeLibs = Join-Path $BackendRepoRoot 'build\native-libs'
    $backendServiceXml = Join-Path $ServiceFilesRoot 'WMS360Backend.xml'
    $backendStartScript = Join-Path $ServiceFilesRoot 'start-backend.cmd'

    $frontendBuild = Join-Path $FrontendRepoRoot '.next'
    $frontendNodeModules = Join-Path $FrontendRepoRoot 'node_modules'
    $frontendPackageJson = Join-Path $FrontendRepoRoot 'package.json'
    $frontendNextConfig = Join-Path $FrontendRepoRoot 'next.config.js'
    $frontendServiceXml = Join-Path $ServiceFilesRoot 'WMS360Frontend.xml'
    $frontendStartScript = Join-Path $ServiceFilesRoot 'start-frontend.cmd'

    $installScript = Join-Path $ServiceFilesRoot 'install-services.ps1'
    $uninstallScript = Join-Path $ServiceFilesRoot 'uninstall-services.ps1'
    $deployStepsDoc = Join-Path $ServiceFilesRoot 'SERVER-DEPLOY-STEPS.md'

    Assert-PathExists -Path $backendJar -Description 'Backend jar'
    Assert-PathExists -Path $backendNativeLibs -Description 'Backend native-libs folder'
    Assert-PathExists -Path $backendServiceXml -Description 'Backend service XML'
    Assert-PathExists -Path $backendStartScript -Description 'Backend start script'
    Assert-PathExists -Path $frontendBuild -Description 'Frontend build output'
    Assert-PathExists -Path $frontendNodeModules -Description 'Frontend node_modules folder'
    Assert-PathExists -Path $frontendPackageJson -Description 'Frontend package.json'
    Assert-PathExists -Path $frontendNextConfig -Description 'Frontend next.config.js'
    Assert-PathExists -Path $frontendServiceXml -Description 'Frontend service XML'
    Assert-PathExists -Path $frontendStartScript -Description 'Frontend start script'
    Assert-PathExists -Path $installScript -Description 'Install services script'
    Assert-PathExists -Path $uninstallScript -Description 'Uninstall services script'
    Assert-PathExists -Path $deployStepsDoc -Description 'Deployment steps document'

    Ensure-Directory -Path $LocalStageRoot
    Ensure-Directory -Path $backendStageRoot
    Ensure-Directory -Path $frontendStageRoot

    Copy-FileWithMessage -Source $backendJar -Destination (Join-Path $backendStageRoot 'demo-0.0.1-SNAPSHOT.jar')
    Invoke-RobocopyMirror -Source $backendNativeLibs -Destination (Join-Path $backendStageRoot 'native-libs')
    Copy-FileWithMessage -Source $backendServiceXml -Destination (Join-Path $backendStageRoot 'WMS360Backend.xml')
    Copy-FileWithMessage -Source $backendStartScript -Destination (Join-Path $backendStageRoot 'start-backend.cmd')

    Invoke-RobocopyMirror -Source $frontendBuild -Destination (Join-Path $frontendStageRoot '.next')
    Invoke-RobocopyMirror -Source $frontendNodeModules -Destination (Join-Path $frontendStageRoot 'node_modules')
    Copy-FileWithMessage -Source $frontendPackageJson -Destination (Join-Path $frontendStageRoot 'package.json')
    Copy-FileWithMessage -Source $frontendNextConfig -Destination (Join-Path $frontendStageRoot 'next.config.js')
    Copy-FileWithMessage -Source $frontendServiceXml -Destination (Join-Path $frontendStageRoot 'WMS360Frontend.xml')
    Copy-FileWithMessage -Source $frontendStartScript -Destination (Join-Path $frontendStageRoot 'start-frontend.cmd')

    Copy-FileWithMessage -Source $installScript -Destination (Join-Path $LocalStageRoot 'install-services.ps1')
    Copy-FileWithMessage -Source $uninstallScript -Destination (Join-Path $LocalStageRoot 'uninstall-services.ps1')
    Copy-FileWithMessage -Source $deployStepsDoc -Destination (Join-Path $LocalStageRoot 'SERVER-DEPLOY-STEPS.md')
}

Assert-PathExists -Path $BackendRepoRoot -Description 'Backend repo root'
Assert-PathExists -Path $FrontendRepoRoot -Description 'Frontend repo root'

Write-Host "Deploy target: $RemoteDeployRoot" -ForegroundColor Green

if (-not $SkipBuild) {
    Invoke-Step -Message 'Building backend jar...' -Action {
        Invoke-CommandInDirectory -WorkingDirectory $BackendRepoRoot -Action {
            Invoke-NativeProcess -Action {
                & .\gradlew.bat bootJar
            }
            if ($LASTEXITCODE -ne 0) {
                throw 'Backend build failed'
            }
        }
    }

    Invoke-Step -Message 'Building frontend...' -Action {
        Invoke-CommandInDirectory -WorkingDirectory $FrontendRepoRoot -Action {
            $previousNextTelemetryDisabled = $env:NEXT_TELEMETRY_DISABLED
            try {
                $env:NEXT_TELEMETRY_DISABLED = '1'
                Invoke-NativeProcess -Action {
                    & yarn build
                }
                if ($LASTEXITCODE -ne 0) {
                    throw 'Frontend build failed'
                }
            }
            finally {
                if ($null -eq $previousNextTelemetryDisabled) {
                    Remove-Item Env:NEXT_TELEMETRY_DISABLED -ErrorAction SilentlyContinue
                }
                else {
                    $env:NEXT_TELEMETRY_DISABLED = $previousNextTelemetryDisabled
                }
            }
        }
    }
}

Invoke-Step -Message "Staging files locally to $LocalStageRoot..." -Action {
    Stage-FilesLocally -BackendRepoRoot $BackendRepoRoot -FrontendRepoRoot $FrontendRepoRoot -LocalStageRoot $LocalStageRoot -ServiceFilesRoot $serviceFilesRoot
}

Assert-PathExists -Path $backendStageRoot -Description 'Local backend stage root'
Assert-PathExists -Path $frontendStageRoot -Description 'Local frontend stage root'

Invoke-Step -Message "Preparing remote deploy root $RemoteDeployRoot..." -Action {
    Ensure-Directory -Path $RemoteDeployRoot
    Ensure-Directory -Path $remoteBackendRoot
    Ensure-Directory -Path $remoteFrontendRoot
}

Invoke-Step -Message 'Copying backend files to remote server...' -Action {
    Copy-FileWithMessage -Source (Join-Path $backendStageRoot 'demo-0.0.1-SNAPSHOT.jar') -Destination (Join-Path $remoteBackendRoot 'demo-0.0.1-SNAPSHOT.jar')
    Copy-FileWithMessage -Source (Join-Path $backendStageRoot 'WMS360Backend.xml') -Destination (Join-Path $remoteBackendRoot 'WMS360Backend.xml')
    Copy-FileWithMessage -Source (Join-Path $backendStageRoot 'start-backend.cmd') -Destination (Join-Path $remoteBackendRoot 'start-backend.cmd')
    Invoke-RobocopyMirror -Source (Join-Path $backendStageRoot 'native-libs') -Destination (Join-Path $remoteBackendRoot 'native-libs')
}

Invoke-Step -Message 'Copying frontend files to remote server...' -Action {
    Copy-FileWithMessage -Source (Join-Path $frontendStageRoot 'package.json') -Destination (Join-Path $remoteFrontendRoot 'package.json')
    Copy-FileWithMessage -Source (Join-Path $frontendStageRoot 'next.config.js') -Destination (Join-Path $remoteFrontendRoot 'next.config.js')
    Copy-FileWithMessage -Source (Join-Path $frontendStageRoot 'WMS360Frontend.xml') -Destination (Join-Path $remoteFrontendRoot 'WMS360Frontend.xml')
    Copy-FileWithMessage -Source (Join-Path $frontendStageRoot 'start-frontend.cmd') -Destination (Join-Path $remoteFrontendRoot 'start-frontend.cmd')
    Invoke-RobocopyMirror -Source (Join-Path $frontendStageRoot '.next') -Destination (Join-Path $remoteFrontendRoot '.next')
    Invoke-RobocopyMirror -Source (Join-Path $frontendStageRoot 'node_modules') -Destination (Join-Path $remoteFrontendRoot 'node_modules')
}

Invoke-Step -Message 'Copying service scripts to remote server...' -Action {
    Copy-FileWithMessage -Source (Join-Path $LocalStageRoot 'install-services.ps1') -Destination (Join-Path $RemoteDeployRoot 'install-services.ps1')
    Copy-FileWithMessage -Source (Join-Path $LocalStageRoot 'uninstall-services.ps1') -Destination (Join-Path $RemoteDeployRoot 'uninstall-services.ps1')
    Copy-FileWithMessage -Source (Join-Path $LocalStageRoot 'SERVER-DEPLOY-STEPS.md') -Destination (Join-Path $RemoteDeployRoot 'SERVER-DEPLOY-STEPS.md')
}

$backendWinSwPath = Join-Path $remoteBackendRoot 'WMS360Backend.exe'
$frontendWinSwPath = Join-Path $remoteFrontendRoot 'WMS360Frontend.exe'

Write-Host ''
Write-Host 'Build, stage, and remote copy complete.' -ForegroundColor Green
Write-Host "Remote backend root: $remoteBackendRoot" -ForegroundColor Green
Write-Host "Remote frontend root: $remoteFrontendRoot" -ForegroundColor Green

if (-not (Test-Path -LiteralPath $backendWinSwPath)) {
    Write-Warning "WinSW backend executable is missing on the server: $backendWinSwPath"
}

if (-not (Test-Path -LiteralPath $frontendWinSwPath)) {
    Write-Warning "WinSW frontend executable is missing on the server: $frontendWinSwPath"
}

Write-Host ''
Write-Host ("Next step on {0}: run install-services.ps1 as Administrator if the services are not installed yet." -f $ServerName)
