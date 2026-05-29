# WMS360 Server Deployment Steps

These instructions deploy the backend and frontend as Windows services on the server.

Target folders on the server:

- `E:\Program Files\Koerber\WebService\WMS360\backend`
- `E:\Program Files\Koerber\WebService\WMS360\frontend`

## Deployment

From this computer, run:

```powershell
& 'E:\Repos\roadrats-backend\deploy\windows-services\build-stage-copy-to-server.ps1'
```

That script:

- builds the backend jar
- builds the frontend
- stages the files locally
- copies everything to `\\FLL2FHJIS02TST\E$\Program Files\Koerber\WebService\WMS360`

## 1. Download WinSW

Download `WinSW-x64.exe` from:

- https://github.com/winsw/winsw/releases

## 2. Put WinSW in the backend folder

Copy `WinSW-x64.exe` to:

- `E:\Program Files\Koerber\WebService\WMS360\backend`

Rename it to:

- `WMS360Backend.exe`

Final path:

- `E:\Program Files\Koerber\WebService\WMS360\backend\WMS360Backend.exe`

## 3. Put WinSW in the frontend folder

Copy `WinSW-x64.exe` to:

- `E:\Program Files\Koerber\WebService\WMS360\frontend`

Rename it to:

- `WMS360Frontend.exe`

Final path:

- `E:\Program Files\Koerber\WebService\WMS360\frontend\WMS360Frontend.exe`

## 4. Make sure Java 17 is installed

Run:

```powershell
java -version
```

## 5. Make sure Node.js is installed

Run:

```powershell
node -v
```

## 6. Open PowerShell as Administrator

Open an elevated PowerShell window on the server.

## 7. Run the install script

Run:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
& 'E:\Program Files\Koerber\WebService\WMS360\install-services.ps1'
```

By default, the install script runs both services under the gMSA `CHEWY\g-hjservicedev$`
and does not prompt for a password:

```powershell
& 'E:\Program Files\Koerber\WebService\WMS360\install-services.ps1'
```

## 8. Enter your Windows account

When prompted, enter your current Windows username and password. This prompt is skipped for the
default gMSA install.

Use either format:

- `CHEWY\your.username`
- `your.username@domain`

## 9. Wait for installation to finish

The script will:

- install both services
- set them to automatic startup
- configure restart on failure
- start both services

## 10. Confirm the services exist

Run:

```powershell
Get-Service WMS360Backend, WMS360Frontend
```

## 11. Test the backend

Run:

```powershell
Invoke-WebRequest http://localhost:8080/api/health
```

## 12. Test the frontend

Run:

```powershell
Invoke-WebRequest http://localhost:3000
```

## Troubleshooting

Check service status:

```powershell
Get-Service WMS360Backend, WMS360Frontend
```

Check logs:

- WinSW logs in the backend and frontend folders
- backend app logs under the backend `logs` folder

If service installation fails while setting the logon account, rerun `install-services.ps1` and use the
`DOMAIN\username` form for the service account. Common `sc.exe` errors are:

- `1326`: bad username or password
- `1385`: the account is missing the `Log on as a service` right on the server
- `1057`: Windows cannot resolve the account name, or the account is not valid for service logon

If service start fails with `1069`, Windows could not log on with the configured service account.
Re-enter the service account credentials, make sure the account is not locked or expired, and confirm
the account has the `Log on as a service` right on the server.

If Windows reports that a service "has been marked for deletion", close Services, Computer Management,
Event Viewer, and any PowerShell windows that queried the service. If it still remains, reboot the
server, then rerun `install-services.ps1`.

For a gMSA, use single quotes around the account name so PowerShell keeps the trailing `$`.
Before installing the services, an administrator can confirm the server is allowed to use the gMSA:

```powershell
Import-Module ActiveDirectory
Test-ADServiceAccount g-hjservicedev
```

If that test fails, the gMSA must be allowed for this server and installed or refreshed on the server:

```powershell
Install-ADServiceAccount g-hjservicedev
Test-ADServiceAccount g-hjservicedev
```

## Remove the services

Run:

```powershell
& 'E:\Program Files\Koerber\WebService\WMS360\uninstall-services.ps1'
```
