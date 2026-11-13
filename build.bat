@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo === TubeLite: build APK ===

rem --- Dung Java 17/21 (Gradle khong chay duoc voi Java 25) ---
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
for /d %%J in ("%USERPROFILE%\.jdks\*") do if exist "%%J\bin\java.exe" set "JAVA_HOME=%%~fJ"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Java: %JAVA_HOME%

rem --- Tim Android SDK ---
if exist local.properties goto sdk_ok
if not exist "%LOCALAPPDATA%\Android\Sdk" goto no_sdk
set "SDK=%LOCALAPPDATA%\Android\Sdk"
set "SDK=%SDK:\=\\%"
>local.properties echo sdk.dir=%SDK%
:sdk_ok

rem --- Lay ban loi moi nhat ---
powershell -NoProfile -ExecutionPolicy Bypass -File tools\update_core.ps1
set "EXT="
if exist extractor.txt set /p EXT=<extractor.txt
if "%EXT%"=="" for /f "tokens=2 delims==" %%V in ('findstr /b "extractorVersion=" gradle.properties') do set "EXT=%%V"
echo Loi: %EXT%

rem --- Tang so build moi lan ---
set BN=1
if exist build_number.txt set /p BN=<build_number.txt
set /a BN=BN+1
>build_number.txt echo %BN%
echo Build so: %BN%

call gradlew.bat --no-daemon assembleRelease -PextractorVersion=%EXT% -PappVersionCode=%BN%
if not errorlevel 1 goto done

echo.
echo Build loi voi ban moi nhat, thu lai bang ban co san...
if exist YoutubeHttpDataSource.bak copy /y YoutubeHttpDataSource.bak app\src\main\java\com\linkinlt\tubelite\YoutubeHttpDataSource.java >nul
for /f "tokens=2 delims==" %%V in ('findstr /b "extractorVersion=" gradle.properties') do set "EXT=%%V"
call gradlew.bat --no-daemon assembleRelease -PextractorVersion=%EXT% -PappVersionCode=%BN%
if errorlevel 1 goto fail

:done
copy /y app\build\outputs\apk\release\app-release.apk "%~dp0TubeLite.apk" >nul
echo.
echo ===== XONG =====
echo APK: %~dp0TubeLite.apk
pause
exit /b 0

:no_sdk
echo Khong tim thay Android SDK o %LOCALAPPDATA%\Android\Sdk
pause
exit /b 1

:fail
echo.
echo BUILD LOI. Chup man hinh gui Claude.
pause
exit /b 1
