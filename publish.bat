@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo === TubeLite: dua code len git + phat hanh ban moi ===

if not exist TubeLite.apk ( echo Chua co TubeLite.apk, hay chay build.bat truoc. & pause & exit /b 1 )
set /p BN=<build_number.txt
set "BN=%BN: =%"
echo Ban build: %BN%

set "NOTES="
set /p NOTES=Ghi chu cho ban moi (Enter de bo qua): 
if "%NOTES%"=="" set "NOTES=Ban cap nhat moi"
set "NOTES=%NOTES:"=%"

rem --- 1. Code len repo rieng tu Linkinlt/TubeLite ---
if not exist .git (
  git init -b main
  git remote add origin https://github.com/Linkinlt/TubeLite.git
)
git add -A
git commit -m "Build %BN%: %NOTES%"
git push -u origin main
if errorlevel 1 ( echo Day code len git LOI. & pause & exit /b 1 )

rem --- 2. APK len repo cong khai Linkinlt/TubeLite-update ---
set "UPD=%~dp0..\TubeLite-update"
if not exist "%UPD%\.git" git clone https://github.com/Linkinlt/TubeLite-update.git "%UPD%"
if not exist "%UPD%\.git" ( echo Khong tai duoc repo TubeLite-update. & pause & exit /b 1 )
cd /d "%UPD%"
git pull
copy /y "%~dp0TubeLite.apk" TubeLite.apk >nul
>version.json echo {"build": %BN%, "name": "1.0.%BN%", "notes": "%NOTES%"}
git add TubeLite.apk version.json
git commit -m "Build %BN%"
git push
if errorlevel 1 ( echo Day ban cai len git LOI. & pause & exit /b 1 )

echo.
echo ===== XONG =====
echo Ban build %BN% da len git. TV vao Gioi thieu ^> Kiem tra cap nhat de cai.
pause
