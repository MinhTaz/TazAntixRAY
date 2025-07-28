# TazAntixRAY Folia Edition Build Script
# PowerShell script to build the plugin
# Developed by TazukiVN

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "TazAntixRAY Folia Edition Builder" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if Java is installed
Write-Host "Checking Java installation..." -ForegroundColor Yellow
try {
    $javaVersion = java -version 2>&1
    if ($javaVersion -match "21") {
        Write-Host "✅ Java 21 found" -ForegroundColor Green
    } else {
        Write-Host "⚠️  Java version: $($javaVersion[0])" -ForegroundColor Yellow
        Write-Host "   Recommended: Java 21" -ForegroundColor Yellow
    }
} catch {
    Write-Host "❌ Java not found. Please install Java 21" -ForegroundColor Red
    Write-Host "   Download from: https://adoptium.net/" -ForegroundColor Yellow
    exit 1
}

# Check if Maven is installed
Write-Host "Checking Maven installation..." -ForegroundColor Yellow
try {
    $mavenVersion = mvn -version 2>&1
    if ($mavenVersion -match "Apache Maven") {
        Write-Host "✅ Maven found" -ForegroundColor Green
    }
} catch {
    Write-Host "❌ Maven not found" -ForegroundColor Red
    Write-Host "   Please install Maven from: https://maven.apache.org/" -ForegroundColor Yellow
    Write-Host "   Or use the manual build instructions in BUILD_INSTRUCTIONS.md" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "Starting build process..." -ForegroundColor Yellow

# Clean previous builds
Write-Host "Cleaning previous builds..." -ForegroundColor Cyan
try {
    mvn clean
    Write-Host "✅ Clean completed" -ForegroundColor Green
} catch {
    Write-Host "❌ Clean failed" -ForegroundColor Red
    exit 1
}

# Build the project
Write-Host "Building project..." -ForegroundColor Cyan
try {
    mvn package -DskipTests
    Write-Host "✅ Build completed successfully!" -ForegroundColor Green
} catch {
    Write-Host "❌ Build failed" -ForegroundColor Red
    Write-Host "   Check the error messages above" -ForegroundColor Yellow
    exit 1
}

# Check if JAR was created
$jarFile = "target\TazAntixRAY-1.0.1.jar"
if (Test-Path $jarFile) {
    $jarSize = (Get-Item $jarFile).Length
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "BUILD SUCCESSFUL!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "JAR Location: $jarFile" -ForegroundColor White
    Write-Host "JAR Size: $([math]::Round($jarSize/1KB, 2)) KB" -ForegroundColor White
    Write-Host ""
    Write-Host "Installation Instructions:" -ForegroundColor Yellow
    Write-Host "1. Copy the JAR to your Folia server's plugins folder" -ForegroundColor White
    Write-Host "2. Ensure PacketEvents is installed" -ForegroundColor White
    Write-Host "3. Restart your server" -ForegroundColor White
    Write-Host "4. Configure worlds in config.yml" -ForegroundColor White
    Write-Host ""
    Write-Host "Features in TazAntixRAY Folia edition:" -ForegroundColor Cyan
    Write-Host "✅ Full Folia compatibility" -ForegroundColor Green
    Write-Host "✅ Multi-language support" -ForegroundColor Green
    Write-Host "✅ Advanced configuration" -ForegroundColor Green
    Write-Host "✅ Region-aware processing" -ForegroundColor Green
    Write-Host "✅ Performance optimizations" -ForegroundColor Green
    Write-Host "✅ Modern command system" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "❌ JAR file not found after build" -ForegroundColor Red
    Write-Host "   Check build logs for errors" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
