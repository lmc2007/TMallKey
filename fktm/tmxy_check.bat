@echo off
REM ==========================================================================
REM  fkTM lock-type check -- read the LSPatch module logs from the phone
REM
REM  Use this AFTER you installed the PATCHED Tmall Campus app, logged in,
REM  and tapped UNLOCK once on your door.
REM
REM  What the output means:
REM    ZM unlock params: {...}   -> your lock is an MV lock (not supported yet)
REM    doorlock captured ...     -> VOC lock, key captured, check TMallKey
REM    (nothing)                 -> unlock once more with this window open,
REM                                 or reinstall the latest patched app
REM ==========================================================================
setlocal
set "OUT=%~dp0tmxy_check.txt"
cd /d "%~dp0"

echo ============================================================
echo  fkTM lock-type check
echo ============================================================
echo.
echo [*] adb devices:
call adb devices
echo.
echo [*] reading module logs (buffer dump, no filter loss)...
call adb logcat -d -v time > "%OUT%" 2>nul
echo [*] filtering tmxydump lines...
echo.
findstr /i "tmxydump" "%OUT%" > "%OUT%.filtered"
if errorlevel 1 (
    echo     [!] no tmxydump lines found.
    echo     [i] 1. make sure you installed the LATEST patched app
    echo     [i] 2. open it, tap UNLOCK once, then run this again
    echo     [i] 3. or send the whole file to the developer: %OUT%
) else (
    echo     [ok] module log lines:
    echo ------------------------------------------------------------
    type "%OUT%.filtered"
    echo ------------------------------------------------------------
    echo.
    echo     [i] send the lines above to the developer.
)
echo.
echo [*] full log saved: %OUT%  (check its content before sharing!)
echo.
pause
endlocal
