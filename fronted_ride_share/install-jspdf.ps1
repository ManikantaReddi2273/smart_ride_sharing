# PowerShell script to install jsPDF
Write-Host "Installing jsPDF..." -ForegroundColor Yellow

cd "c:\Users\ManishReddy\OneDrive\Desktop\RIDE_SHARING\fronted_ride_share"

# Check if jspdf already exists
if (Test-Path "node_modules\jspdf\package.json") {
    Write-Host "✅ jsPDF is already installed" -ForegroundColor Green
    exit 0
}

Write-Host "jsPDF not found. Installing..." -ForegroundColor Yellow

# Try multiple installation methods
Write-Host "Method 1: Standard install..." -ForegroundColor Cyan
npm install jspdf@2.5.1 --save

if (Test-Path "node_modules\jspdf\package.json") {
    Write-Host "✅ jsPDF installed successfully!" -ForegroundColor Green
    exit 0
}

Write-Host "Method 2: Install with legacy peer deps..." -ForegroundColor Cyan
npm install jspdf@2.5.1 --save --legacy-peer-deps

if (Test-Path "node_modules\jspdf\package.json") {
    Write-Host "✅ jsPDF installed successfully!" -ForegroundColor Green
    exit 0
}

Write-Host "Method 3: Clean install..." -ForegroundColor Cyan
Remove-Item -Recurse -Force node_modules\jspdf -ErrorAction SilentlyContinue
npm cache clean --force
npm install jspdf@2.5.1 --save --no-audit --no-fund

if (Test-Path "node_modules\jspdf\package.json") {
    Write-Host "✅ jsPDF installed successfully!" -ForegroundColor Green
    exit 0
}

Write-Host "❌ Failed to install jsPDF. Please run manually:" -ForegroundColor Red
Write-Host "   npm install jspdf@2.5.1 --save" -ForegroundColor Yellow
exit 1
