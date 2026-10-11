@echo off
REM ==========================================================================
REM  fkTM P0 capture  (v3)  --  grab Tmall Campus doorlock logs over adb
REM  *** NO ROOT REQUIRED ***
REM
REM  Preconditions:
REM    1) Phone: Settings - Developer options - USB debugging = ON
REM    2) USB cable connected, and "adb devices" lists the phone
REM    3) adb is in PATH
REM
REM  Usage:
REM    1) run this .bat
REM    2) open "Tmall Campus" on the phone, walk to the dorm door
REM    3) tap UNLOCK once, wait until the lock opens
REM    4) press Ctrl+C here to stop  (verify runs automatically afterwards)
REM
REM  v3 changes:
REM    - explicit CRLF line endings (LF-only .bat breaks cmd: %OUT% became empty)
REM    - no caret line-continuation, no if() blocks -> maximum cmd compatibility
REM    - real device logs everything under tag VocLockHelperV2 (must be included)
REM ==========================================================================
setlocal
set "OUT=%~dp0p0_capture.txt"
cd /d "%~dp0"

echo ============================================================
echo  fkTM P0 capture  (v3)
echo ============================================================
echo.
echo [*] output file: %OUT%
echo.
echo [*] adb devices:
call adb devices
echo.
echo [*] clearing old logcat buffer...
call adb logcat -c
echo.
echo [*] capturing... keep the phone connected.
echo [*] NOW: open Tmall Campus and tap UNLOCK once.
echo [*] AFTER the door opens: press Ctrl+C in this window.
echo ------------------------------------------------------------
call adb logcat -v time VocLockHelperV2:V VocLockHelper:V VocUnlockBridge:V BluetoothHelper:V VocLockManager:V VocLockSession:V VocLockBridge:V ZMUnlockBridge:V VocStatistics:V VocLogger:V Logger:V TLog:V *:S > "%OUT%"
echo ------------------------------------------------------------
echo.
echo [*] saved: %OUT%
echo.
echo [*] quick check:
findstr /C:"activationKey" "%OUT%" >nul 2>nul
if errorlevel 1 echo     [!] no activationKey line found -- capture may be incomplete.
if not errorlevel 1 echo     [ok] activationKey line present.
echo.
echo [*] running verify...
set "PY=C:\Users\64702\.workbuddy\binaries\python\envs\default\Scripts\python.exe"
if not exist "%PY%" set "PY=python"
"%PY%" "%~dp0cli.py" verify --log "%OUT%"
echo.
pause
endlocal
