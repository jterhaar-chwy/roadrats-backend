$ErrorActionPreference = 'Stop'

$services = @(
    @{
        Name = 'WMS360Backend'
        Exe = 'E:\Program Files\Koerber\WebService\WMS360\backend\WMS360Backend.exe'
    },
    @{
        Name = 'WMS360Frontend'
        Exe = 'E:\Program Files\Koerber\WebService\WMS360\frontend\WMS360Frontend.exe'
    }
)

foreach ($service in $services) {
    $existing = Get-Service -Name $service.Name -ErrorAction SilentlyContinue
    if (-not $existing) {
        continue
    }

    if (Test-Path -LiteralPath $service.Exe) {
        & $service.Exe stop | Out-Null
        & $service.Exe uninstall | Out-Null
    }
    else {
        sc.exe stop $service.Name | Out-Null
        sc.exe delete $service.Name | Out-Null
    }
}

Write-Host 'WMS360 services removed.' -ForegroundColor Green
