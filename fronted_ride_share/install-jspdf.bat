@echo off
echo ========================================
echo Installing jsPDF Package
echo ========================================
echo.

cd /d "%~dp0"

echo Step 1: Checking if jsPDF is already installed...
if exist "node_modules\jspdf\package.json" (
    echo [SUCCESS] jsPDF is already installed!
    echo.
    npm list jspdf
    echo.
    echo You can now restart your dev server.
    pause
    exit /b 0
)

echo [INFO] jsPDF not found. Installing...
echo.

echo Step 2: Installing jsPDF...
call npm install jspdf@2.5.1 --save

if exist "node_modules\jspdf\package.json" (
    echo.
    echo [SUCCESS] jsPDF installed successfully!
    echo.
    npm list jspdf
    echo.
    echo ========================================
    echo Installation Complete!
    echo ========================================
    echo.
    echo Please restart your Vite dev server:
    echo   npm run dev
    echo.
) else (
    echo.
    echo [ERROR] Installation failed!
    echo.
    echo Trying alternative method...
    call npm install jspdf@2.5.1 --save --legacy-peer-deps
    
    if exist "node_modules\jspdf\package.json" (
        echo [SUCCESS] jsPDF installed with legacy peer deps!
    ) else (
        echo [ERROR] Installation still failed.
        echo Please run manually: npm install jspdf@2.5.1 --save
    )
)

pause
