@echo off
echo ========================================
echo Manual Conversion of Missing Presets
echo ========================================
echo.

cd /d "%~dp0"

echo Running manual conversion script...
python manual_convert_missing_presets.py

if errorlevel 1 (
    echo.
    echo ❌ Conversion failed!
    pause
    exit /b 1
)

echo.
echo ========================================
echo Conversion Complete!
echo ========================================
echo.
echo Next steps:
echo 1. Review: src\main\resources\nodecraft\graph_presets_complete.json
echo 2. Backup current: copy src\main\resources\nodecraft\graph_presets.json src\main\resources\nodecraft\graph_presets_backup2.json
echo 3. Replace: copy src\main\resources\nodecraft\graph_presets_complete.json src\main\resources\nodecraft\graph_presets.json
echo 4. Restart NodeCraft
echo.
pause
