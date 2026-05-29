# WMS360 Windows Services

This folder contains the files needed to run the WMS360 backend and frontend
as Windows services on a server such as `FLL2FHJIS02TST`.

## Service Model

These files assume:

- Backend deploy root: `E:\Program Files\Koerber\WebService\WMS360\backend`
- Frontend deploy root: `E:\Program Files\Koerber\WebService\WMS360\frontend`
- Backend listens on `8080`
- Frontend listens on `3000`
- Services are wrapped with `WinSW`

## Important Note About "Current Logged On Account"

Windows services do not run under your current interactive logon token. If you
want the services to run under your current user account, you must provide that
account's password when configuring the service logon.

## Files

- `WMS360Backend.xml`: WinSW config for backend service
- `WMS360Frontend.xml`: WinSW config for frontend service
- `start-backend.cmd`: launches the Spring Boot jar
- `start-frontend.cmd`: launches the Next.js frontend
- `build-stage-copy-to-server.ps1`: builds locally, stages locally, and copies to `FLL2FHJIS02TST`
- `install-services.ps1`: installs and configures both services
- `uninstall-services.ps1`: stops and removes both services

## Prerequisites On The Server

- Java 17 installed
- Node.js installed
- Frontend dependencies installed in `E:\Apps\RoadRats\frontend\node_modules`
- Backend jar copied to `E:\Apps\RoadRats\backend\demo-0.0.1-SNAPSHOT.jar`
- Backend SQL auth DLLs copied to `E:\Apps\RoadRats\backend\native-libs`
- WinSW downloaded twice and renamed:
  - `E:\Apps\RoadRats\backend\WMS360Backend.exe`
  - `E:\Apps\RoadRats\frontend\WMS360Frontend.exe`

WinSW releases:

- https://github.com/winsw/winsw/releases

Use the matching `WinSW-x64.exe`, then rename each copy to match the service
XML base name.

## Deployment Layout

Backend:

- `E:\Program Files\Koerber\WebService\WMS360\backend\demo-0.0.1-SNAPSHOT.jar`
- `E:\Program Files\Koerber\WebService\WMS360\backend\native-libs\...`
- `E:\Program Files\Koerber\WebService\WMS360\backend\WMS360Backend.exe`
- `E:\Program Files\Koerber\WebService\WMS360\backend\WMS360Backend.xml`
- `E:\Program Files\Koerber\WebService\WMS360\backend\start-backend.cmd`

Frontend:

- `E:\Program Files\Koerber\WebService\WMS360\frontend\.next\...`
- `E:\Program Files\Koerber\WebService\WMS360\frontend\node_modules\...`
- `E:\Program Files\Koerber\WebService\WMS360\frontend\package.json`
- `E:\Program Files\Koerber\WebService\WMS360\frontend\next.config.js`
- `E:\Program Files\Koerber\WebService\WMS360\frontend\WMS360Frontend.exe`
- `E:\Program Files\Koerber\WebService\WMS360\frontend\WMS360Frontend.xml`
- `E:\Program Files\Koerber\WebService\WMS360\frontend\start-frontend.cmd`

## Recommended Flow

1. Run the consolidated deployment script from this computer:

```powershell
& 'E:\Repos\roadrats-backend\deploy\windows-services\build-stage-copy-to-server.ps1'
```

2. Download `WinSW-x64.exe` to the server if not already present, and rename:
   - `E:\Program Files\Koerber\WebService\WMS360\backend\WMS360Backend.exe`
   - `E:\Program Files\Koerber\WebService\WMS360\frontend\WMS360Frontend.exe`
3. Open an elevated PowerShell session on `FLL2FHJIS02TST`.
4. Run:

```powershell
& 'E:\Program Files\Koerber\WebService\WMS360\install-services.ps1'
```

The install script prompts for the Windows account and password to use as the
service logon.

## Verify

```powershell
Get-Service WMS360Backend, WMS360Frontend
Invoke-WebRequest http://localhost:8080/api/health
Invoke-WebRequest http://localhost:3000
```

## Remove

```powershell
E:\Program Files\Koerber\WebService\WMS360\uninstall-services.ps1
```
