@echo off
REM ============================================================
REM   اعداد سيرفر تجربة ACRPJobs - ويندوز
REM     setup.bat            Paper 1.12.2
REM     setup.bat mohist     Mohist 1.12.2
REM   يحتاج: Java 8
REM ============================================================
setlocal enabledelayedexpansion
set "HERE=%~dp0"
set "RUN=%HERE%run"
set "PLUGINDIR=%HERE%..\acrp-jobs"
set "FLAVOUR=paper"
if /i "%~1"=="mohist" set "FLAVOUR=mohist"

echo.
echo ==^> اتحقق من Java
where java >nul 2>&1 || (echo خطأ: ما لقيت Java. ركب Java 8 من https://adoptium.net/temurin/releases/?version=8 & exit /b 1)
echo     تنبيه: ماين كرافت 1.12.2 يحتاج Java 8 بالذات.

echo.
echo ==^> ادور على جار البلق ان
set "JAR="
for /f "delims=" %%f in ('dir /b /o-d "%PLUGINDIR%\target\ACRPJobs-*.jar" 2^>nul') do (
    if not defined JAR set "JAR=%PLUGINDIR%\target\%%f"
)
if not defined JAR (
    echo     ما لقيت جار مبني.
    echo     حمله من GitHub Actions ^> Build Jobs Plugin ^> Artifacts
    echo     وحطه في: %PLUGINDIR%\target\
    exit /b 1
)
echo     !JAR!

if not exist "%RUN%\plugins" mkdir "%RUN%\plugins"

if not exist "%RUN%\server.jar" (
    echo.
    echo ==^> انزل سيرفر %FLAVOUR% 1.12.2
    if "%FLAVOUR%"=="mohist" (
        set "URL=https://mohistmc.com/api/1.12.2/latest/download"
    ) else (
        set "URL=https://api.papermc.io/v2/projects/paper/versions/1.12.2/builds/1620/downloads/paper-1.12.2-1620.jar"
    )
    powershell -NoProfile -Command "try { Invoke-WebRequest -Uri '!URL!' -OutFile '%RUN%\server.jar' } catch { exit 1 }" || (
        echo خطأ: فشل التنزيل. نزل السيرفر يدويا وسمه: %RUN%\server.jar
        exit /b 1
    )
) else (
    echo.
    echo ==^> السيرفر منزل من قبل - اتخطى التنزيل
)

echo.
echo ==^> اركب البلق ان والاعدادات الجاهزة
copy /y "!JAR!" "%RUN%\plugins\" >nul
copy /y "%HERE%preset\eula.txt" "%RUN%\eula.txt" >nul
if not exist "%RUN%\server.properties" copy /y "%HERE%preset\server.properties" "%RUN%\server.properties" >nul
if not exist "%RUN%\plugins\ACRPJobs" mkdir "%RUN%\plugins\ACRPJobs"
for %%f in (zones.yml routes.yml spots.yml) do (
    if exist "%RUN%\plugins\ACRPJobs\%%f" (
        echo     موجود من قبل، ما لمسته: %%f
    ) else (
        copy /y "%HERE%preset\plugins\ACRPJobs\%%f" "%RUN%\plugins\ACRPJobs\%%f" >nul
        echo     نسخت: %%f
    )
)

> "%RUN%\start.bat" echo @echo off
>>"%RUN%\start.bat" echo cd /d "%%~dp0"
>>"%RUN%\start.bat" echo java -Xms1G -Xmx2G -jar server.jar nogui
>>"%RUN%\start.bat" echo pause

echo.
echo ==^> تم!
echo.
echo    شغل السيرفر:   %RUN%\start.bat
echo    ادخل عليه:     localhost
echo    في الكونسول:   op اسمك_في_ماين_كرافت
echo.
echo    خطوات التجربة: %HERE%README.md
echo.
endlocal
