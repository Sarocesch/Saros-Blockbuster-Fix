@echo off
REM Build & deploy Blockbuster Fix with all new/modified files.
REM Compiles the new DynamX vehicle recording classes + modifications.
REM Requires DynamX jar on classpath for DynamXCompatHandler.

setlocal enabledelayedexpansion
set "FORGE_RECOMP=C:/Users/saroc/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.12.2-14.23.5.2859_mapped_snapshot_20171003-1.12/forge-1.12.2-14.23.5.2859_mapped_snapshot_20171003-1.12-recomp.jar"
set "BB_JAR=C:/Users/saroc/AppData/Roaming/Minewache-Launcher/Die_Minewache/mods/blockbuster-2.7-1.12.2-dynamxfix.jar"
set "MCLIB_JAR=C:/Users/saroc/AppData/Roaming/Minewache-Launcher/Die_Minewache/mods/mclib-2.4.3-1.12.2.jar"
set "DYNAMX_JAR=C:/Users/saroc/AppData/Roaming/Minewache-Launcher/Die_Minewache/mods/DynamX-4.2.0-beta-saros-temp-fix-all.jar"
set "METAMORPH_JAR=C:/Users/saroc/AppData/Roaming/Minewache-Launcher/Die_Minewache/mods/metamorph-1.5-1.12.2-sarosfix.jar"
set "LWJGL_JAR=C:/Users/saroc/AppData/Roaming/Minewache-Launcher/Die_Minewache/libraries/org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar"
set "SPECIAL_SOURCE=C:/Users/saroc/.gradle/caches/forge_gradle/maven_downloader/net/md-5/SpecialSource/1.8.3/SpecialSource-1.8.3-shaded.jar"
set "MCP_SRG=C:/Users/saroc/.gradle/caches/minecraft/de/oceanlabs/mcp/mcp_snapshot/20171003/1.12.2/srgs/mcp-srg.srg"
set "JAVA=C:/Users/saroc/.jdks/corretto-1.8.0_462/bin"
set "MODS=C:/Users/saroc/AppData/Roaming/Minewache-Launcher/Die_Minewache/mods"
set "ROOT=t:/MyModsCode/Saros Blockbuster Fix"
set "WORK=%TEMP%\bb_compile_patch"

set "CP=%FORGE_RECOMP%;%BB_JAR%;%MCLIB_JAR%;%DYNAMX_JAR%;%METAMORPH_JAR%;%LWJGL_JAR%"

set "SOURCES="
set "SOURCES=%SOURCES% %ROOT%/src/main/java/mchorse/blockbuster_pack/client/render/layers/LayerActorArmor.java"
set "SOURCES=%SOURCES% %ROOT%/src/main/java/mchorse/blockbuster/recording/actions/Action.java"
set "SOURCES=%SOURCES% %ROOT%/src/main/java/mchorse/blockbuster/recording/actions/MountingAction.java"
set "SOURCES=%SOURCES% %ROOT%/src/main/java/mchorse/blockbuster/recording/actions/VehicleControlAction.java"
set "SOURCES=%SOURCES% %ROOT%/src/main/java/mchorse/blockbuster/recording/actions/VehicleMountAction.java"
set "SOURCES=%SOURCES% %ROOT%/src/main/java/mchorse/blockbuster/recording/actions/ActionRegistry.java"
set "SOURCES=%SOURCES% %ROOT%/src/main/java/mchorse/blockbuster/recording/dynamx/DynamXCompat.java"
set "SOURCES=%SOURCES% %ROOT%/src/main/java/mchorse/blockbuster/recording/dynamx/DynamXCompatHandler.java"
set "SOURCES=%SOURCES% %ROOT%/src/main/java/mchorse/blockbuster/recording/RecordPlayer.java"
set "SOURCES=%SOURCES% %ROOT%/src/main/java/mchorse/blockbuster/recording/RecordRecorder.java"
set "SOURCES=%SOURCES% %ROOT%/src/main/java/mchorse/blockbuster/recording/capturing/ActionHandler.java"
set "SOURCES=%SOURCES% %ROOT%/src/main/java/mchorse/blockbuster/CommonProxy.java"

echo === Cleaning work dir ===
if exist "%WORK%" rmdir /s /q "%WORK%"
mkdir "%WORK%"
mkdir "%WORK%\output"
mkdir "%WORK%\srg_out"

echo === Compiling ===
"%JAVA%/javac" -source 1.8 -target 1.8 -cp "%CP%" -d "%WORK%\output" %SOURCES%
if errorlevel 1 (
    echo COMPILE FAILED
    pause
    exit /b 1
)

echo === Packaging classes ===
cd /d "%WORK%\output"
"%JAVA%/jar" cf "%WORK%/mcp.jar" mchorse

echo === Reobfuscating MCP -^> SRG ===
"%JAVA%/java" -cp "%FORGE_RECOMP%;%BB_JAR%;%MCLIB_JAR%;%DYNAMX_JAR%;%METAMORPH_JAR%;%LWJGL_JAR%;%SPECIAL_SOURCE%" net.md_5.specialsource.SpecialSource --in-jar "%WORK%/mcp.jar" --out-jar "%WORK%/srg.jar" --srg-in "%MCP_SRG%" --live
if errorlevel 1 (
    echo REOBF FAILED
    pause
    exit /b 1
)

echo === Patching JAR ===
cd /d "%WORK%\srg_out"
"%JAVA%/jar" xf "%WORK%/srg.jar"
copy /y "%BB_JAR%" "%WORK%/blockbuster-final.jar" >nul
"%JAVA%/jar" uf "%WORK%/blockbuster-final.jar" mchorse

echo === Deploying to mods folder ===
copy /y "%WORK%/blockbuster-final.jar" "%MODS%/blockbuster-2.7-1.12.2-dynamxfix.jar" >nul
copy /y "%WORK%/blockbuster-final.jar" "%ROOT%/blockbuster-2.7-1.12.2-dynamxfix.jar" >nul

echo.
echo === DONE! Restart Minecraft to test. ===
echo.
pause
