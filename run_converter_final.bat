@echo off
echo ========================================
echo Final Preset Conversion - All Files Fixed
echo ========================================
echo.

cd /d "%~dp0"

echo Step 1: Verifying all preset JSON files...
python validate_presets.py
echo.

echo Step 2: Running preset converter...
echo.
java -cp "build/classes/java/main;lib/*" com.nodecraft.nodesystem.preset.PresetConverterTool

echo.
echo Step 3: Checking results...
echo.
if exist src\main\resources\nodecraft\graph_presets_updated.json (
    echo ✅ graph_presets_updated.json generated successfully!
    for %%A in (src\main\resources\nodecraft\graph_presets_updated.json) do echo    File size: %%~zA bytes
) else (
    echo ❌ Failed to generate graph_presets_updated.json
    pause
    exit /b 1
)

echo.
echo ========================================
echo Conversion Complete!
echo ========================================
echo.
echo All 21 presets should now be converted.
echo.
echo Next steps:
echo 1. Review the file: src\main\resources\nodecraft\graph_presets_updated.json
echo 2. Replace the original:
echo    copy src\main\resources\nodecraft\graph_presets_updated.json src\main\resources\nodecraft\graph_presets.json
echo 3. Restart NodeCraft
echo.
pause
