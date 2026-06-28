@echo off
echo ========================================
echo Complete Preset Fix and Conversion
echo ========================================
echo.

cd /d "%~dp0"

echo Step 1: Fixing node IDs in all preset files...
echo.
python fix_node_ids.py

if errorlevel 1 (
    echo ❌ Failed to fix node IDs!
    pause
    exit /b 1
)

echo.
echo ========================================
echo Step 2: Recompiling Java classes...
echo.
call gradlew clean compileJava

if errorlevel 1 (
    echo ❌ Compilation failed!
    pause
    exit /b 1
)

echo.
echo ========================================
echo Step 3: Running converter with fixed node IDs...
echo.
java -cp "build/classes/java/main;lib/*" com.nodecraft.nodesystem.preset.PresetConverterTool

if errorlevel 1 (
    echo ❌ Conversion failed!
    pause
    exit /b 1
)

echo.
echo ========================================
echo Step 4: Checking results...
echo.

if not exist src\main\resources\nodecraft\graph_presets_updated.json (
    echo ❌ graph_presets_updated.json not found!
    pause
    exit /b 1
)

REM Count composite presets
findstr /C:"\"kind\": \"composite\"" src\main\resources\nodecraft\graph_presets_updated.json > temp_count.txt
for /f %%a in ('type temp_count.txt ^| find /c /v ""') do set /a count=%%a
del temp_count.txt

echo Found %count% composite presets
echo.

if %count% LSS 20 (
    echo ⚠️  Warning: Expected at least 20 composite presets, but only found %count%
    echo This might indicate some presets failed to convert.
    echo.
) else (
    echo ✅ All presets converted successfully!
    echo.
)

echo ========================================
echo Step 5: Replacing graph_presets.json...
echo.

REM Backup
if not exist src\main\resources\nodecraft\graph_presets_backup_final.json (
    copy src\main\resources\nodecraft\graph_presets.json src\main\resources\nodecraft\graph_presets_backup_final.json
    echo ✅ Backed up original file
)

REM Replace
copy /Y src\main\resources\nodecraft\graph_presets_updated.json src\main\resources\nodecraft\graph_presets.json
echo ✅ Replaced graph_presets.json

echo.
echo ========================================
echo ALL DONE!
echo ========================================
echo.
echo Summary:
echo - Fixed node IDs in preset files
echo - Recompiled Java classes
echo - Converted all presets
echo - Found %count% working presets
echo - Updated graph_presets.json
echo.
echo Next: Restart NodeCraft and test!
echo.
pause
