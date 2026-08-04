@echo off
REM ACRP Launcher - Free Code Signing via sign.necessary.nu
REM Requirements: osslsigncode (choco install osslsigncode)
REM Set SIGNING_TOKEN env var before running

if "%SIGNING_TOKEN%"=="" (
    echo ERROR: Set SIGNING_TOKEN env var first
    echo   set SIGNING_TOKEN=your_token_here
    echo   sign.bat
    exit /b 1
)

set INSTALLER=dist\ACRP Launcher-setup-1.0.0.exe
set SIGNED=dist\ACRP Launcher-setup-1.0.0-signed.exe
set TMPFILE=tosign.bin
set SIGNATURE=signed.bin

echo [1/3] Extracting data from installer...
osslsigncode extract-data -in "%INSTALLER%" -out "%TMPFILE%"
if %errorlevel% neq 0 (
    echo ERROR: Failed to extract data. Is osslsigncode installed?
    echo   choco install osslsigncode
    exit /b 1
)

echo [2/3] Sending to signing service...
curl -X POST -H "Authorization: Bearer %SIGNING_TOKEN%" --data-binary @%TMPFILE% https://sign.necessary.nu/windows/sign -o %SIGNATURE%
if %errorlevel% neq 0 (
    echo ERROR: Signing request failed
    exit /b 1
)

echo [3/3] Attaching signature...
osslsigncode attach-signature -sigin %SIGNATURE% -in "%INSTALLER%" -out "%SIGNED%"
if %errorlevel% neq 0 (
    echo ERROR: Failed to attach signature
    exit /b 1
)

echo.
echo DONE! Signed file: %SIGNED%
echo.
del %TMPFILE% %SIGNATURE% 2>nul
