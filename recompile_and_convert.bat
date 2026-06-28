@echo off
echo ========================================
echo Recompile and Reconvert All Presets
echo ========================================
echo.

cd /d "%~dp0"

echo Step 1: Clean and recompile...
echo.
call gradlew clean compileJava
if errorlevel 1 (
    echo ❌ Compilation failed!
    pause
    exit /b 1
)

echo.
echo Step 2: Run converter again...
echo.
java -cp "build/classes/java/main;lib/*" com.nodecraft.nodesystem.preset.PresetConverterTool

echo.
echo Step 3: Check results...
echo.
if exist src\main\resources\nodecraft\graph_presets_updated.json (
    echo ✅ File generated!

    REM Count composite presets
    findstr /C:"\"kind\": \"composite\"" src\main\resources\nodecraft\graph_presets_updated.json > temp_count.txt
    for /f %%a in ('type temp_count.txt ^| find /c /v ""') do set count=%%a
    del temp_count.txt

    echo Found !count! composite presets
    if !count! LSS 20 (
        echo ⚠️  Expected at least 20, but only found !count!
    ) else (
        echo ✅ All presets converted successfully!
    )
) else (
    echo ❌ Failed to generate file
)

echo.
pause
