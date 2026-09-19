@echo off
REM ============================================================
REM  auto_order one-click launcher
REM  Deps are installed in the local ./venv (no WorkBuddy global env needed)
REM  Usage: run.bat [args...]
REM    e.g.  run.bat --accounts 20 --target-orders 40 --gateway http://localhost:10001
REM ============================================================
setlocal
set "DIR=%~dp0"
"%DIR%venv\Scripts\python.exe" "%DIR%auto_order.py" %*
endlocal
