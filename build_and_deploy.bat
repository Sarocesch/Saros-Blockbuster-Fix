@echo off
REM Build & deploy Blockbuster Fix with all new/modified files.
REM Compiles the new DynamX vehicle recording classes + modifications.
REM Requires DynamX jar on classpath for DynamXCompatHandler.

setlocal enabledelayedexpansion
set "FORGE_RECOMP=C:\Users\saroc\.gradle\caches\forge_gradle\minecraft_user_repo\net\minecraftforge\forge\1.12.2-14.23.5.2859_mapped_snapshot_20171003-1.12\forge-1.12.2-14.23.5.2859_mapped_snapshot_20171003-1.12-recomp.jar"
set "LWJGL_JAR=C:\Users\saroc\AppData\Roaming\Minewache-Launcher\Die_Minewache\libraries\org\lwjgl\lwjgl\lwjgl\2.9.4-nightly-20150209\lwjgl-2.9.4-nightly-20150209.jar"
set "SPECIAL_SOURCE=C:\Users\saroc\.gradle\caches\forge_gradle\maven_downloader\net\md-5\SpecialSource\1.8.3\SpecialSource-1.8.3-shaded.jar"
set "MCP_SRG=C:\Users\saroc\.gradle\caches\minecraft\de\oceanlabs\mcp\mcp_snapshot\20171003\1.12.2\srgs\mcp-srg.srg"
set "JAVA=C:\Users\saroc\.jdks\corretto-1.8.0_462\bin"
set "MODS=C:\Users\saroc\AppData\Roaming\Minewache-Launcher\Die_Minewache\mods"
REM Resolve mod jars by pattern - the launcher updates these files and the
REM version inside the name changes, so never hardcode them
for /f "delims=" %%F in ('dir /b /o-d "%MODS%\blockbuster-*.jar" 2^>nul') do if not defined BB_JAR set "BB_JAR=%MODS%\%%F"
for /f "delims=" %%F in ('dir /b /o-d "%MODS%\mclib-*.jar" 2^>nul') do if not defined MCLIB_JAR set "MCLIB_JAR=%MODS%\%%F"
for /f "delims=" %%F in ('dir /b /o-d "%MODS%\DynamX-*-all.jar" 2^>nul') do if not defined DYNAMX_JAR set "DYNAMX_JAR=%MODS%\%%F"
for /f "delims=" %%F in ('dir /b /o-d "%MODS%\modularwarfare-*.jar" 2^>nul') do if not defined MWF_JAR set "MWF_JAR=%MODS%\%%F"
for /f "delims=" %%F in ('dir /b /o-d "%MODS%\metamorph-*.jar" 2^>nul') do if not defined METAMORPH_JAR set "METAMORPH_JAR=%MODS%\%%F"
echo === mclib:     %MCLIB_JAR%
echo === DynamX:    %DYNAMX_JAR%
echo === MWF:       %MWF_JAR%
echo === Metamorph: %METAMORPH_JAR%
rem Mod-Version: bei JEDEM Release hochzaehlen, gleich wie der Dateiname (-YYYYMMDD.N).
rem Forge laesst Client und Server mit unterschiedlicher Version dann nicht zusammen.
set "BB_VERSION=2.7-20261107.19"
set "ROOT=t:\MyModsCode\Saros Blockbuster Fix"
set "ROOTFWD=t:/MyModsCode/Saros Blockbuster Fix"
set "WORK=%TEMP%\bb_compile_patch"
set "GUAVA_JAR=C:\Users\saroc\AppData\Roaming\Minewache-Launcher\Die_Minewache\libraries\com\google\guava\guava\21.0\guava-21.0.jar"
set "NETTY_JAR=C:\Users\saroc\AppData\Roaming\Minewache-Launcher\Die_Minewache\libraries\io\netty\netty-all\4.1.9.Final\netty-all-4.1.9.Final.jar"
set "VECMATH_JAR=C:\Users\saroc\AppData\Roaming\Minewache-Launcher\Die_Minewache\libraries\java3d\vecmath\1.5.2\vecmath-1.5.2.jar"
set "LOG4J_JAR=C:\Users\saroc\AppData\Roaming\Minewache-Launcher\Die_Minewache\libraries\org\apache\logging\log4j\log4j-api\2.15.0\log4j-api-2.15.0.jar"
set "AUTHLIB_JAR=C:\Users\saroc\AppData\Roaming\Minewache-Launcher\Die_Minewache\libraries\com\mojang\authlib\1.5.25\authlib-1.5.25.jar"
REM Bewusst eine eigene Kopie: der Gradle-Cache-Pfad verschwand zwischen zwei Builds.
set "JSR305_JAR=%ROOT%\buildlibs\jsr305-3.0.2.jar"
set "COMMONSIO_JAR=C:\Users\saroc\AppData\Roaming\Minewache-Launcher\Die_Minewache\libraries\commons-io\commons-io\2.5\commons-io-2.5.jar"

set "GSON_JAR=C:\Users\saroc\AppData\Roaming\Minewache-Launcher\Die_Minewache\libraries\com\google\code\gson\gson\2.14.0\gson-2.14.0.jar"
set "CP=%FORGE_RECOMP%;%GSON_JAR%;%BB_JAR%;%MCLIB_JAR%;%DYNAMX_JAR%;%MWF_JAR%;%METAMORPH_JAR%;%LWJGL_JAR%;%GUAVA_JAR%;%NETTY_JAR%;%VECMATH_JAR%;%LOG4J_JAR%;%AUTHLIB_JAR%;%JSR305_JAR%;%COMMONSIO_JAR%"

echo === Cleaning work dir ===
if exist "%WORK%" rmdir /s /q "%WORK%"
mkdir "%WORK%"
mkdir "%WORK%\output"
mkdir "%WORK%\srg_out"

REM Write source list to file to handle spaces in ROOT path
set "SRCLIST=%WORK%\srclist.txt"
(
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster_pack/client/render/layers/LayerActorArmor.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/render/tileentity/TileEntityModelRenderer.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/common/tileentity/TileEntityModel.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/common/tileentity/TileEntityModelSettings.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/model_block/GuiModelBlockPanel.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/render/tileentity/DetachedModelBlocks.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/RenderingHandler.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/actions/VehicleControlAction.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/actions/VehicleBasicsAction.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/actions/VehicleMountAction.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/actions/AttackAction.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/actions/InteractEntityAction.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/actions/MWFFireAction.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/actions/ActionRegistry.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/dynamx/DynamXCompat.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/dynamx/DynamXVehicleHandler.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/dynamx/HydraulicCompat.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/actions/VehicleHydraulicAction.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/scene/SceneCleaner.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/client/recording/ClientHandlerSyncTick.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/mwf/MWFCompat.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/mwf/MWFCompatHandler.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/RecordPlayer.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/common/entity/EntityActor.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/render/RenderCustomModel.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/data/Frame.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/data/Record.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/scene/ScenePlaybackChunks.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/dynamx/DynamXVehiclePin.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/RecordRecorder.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/capturing/ActionHandler.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/capturing/PlayerTracker.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/Dispatcher.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/common/mwf/PacketMWFFireReplay.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/client/mwf/ClientHandlerMWFFireReplay.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/client/recording/ClientHandlerPlayerRecording.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster_pack/client/render/RenderCustomActor.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/actions/MWFExtraSlotAction.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/common/mwf/PacketMWFExtraSlot.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/client/mwf/ClientHandlerMWFExtraSlot.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/actions/MWFAimAction.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/actions/MMStateAction.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/common/mwf/PacketMMState.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/client/mwf/ClientHandlerMMState.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/common/mwf/PacketMWFAim.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/client/mwf/ClientHandlerMWFAim.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster_pack/client/render/layers/LayerActorMWFArmor.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/model/ModelCustom.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/scene/Scene.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/scene/Replay.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/scene/SceneManager.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/scene/GuiScenePanel.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/textures/URLDownloadThread.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/common/PacketSkinRequest.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/common/PacketSkinData.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/utils/SkinMirror.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/server/ServerHandlerSkinRequest.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/server/ServerHandlerSkinData.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/client/ClientHandlerSkinRequest.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/client/ClientHandlerSkinData.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/ActorsPack.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/client/recording/actions/ClientHandlerActions.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/client/scene/ClientHandlerSceneCast.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/dynamx/SceneVehicles.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/dynamx/SceneVehicleData.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/appearance/ActorAppearance.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/appearance/AppearanceCapture.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/render/ActorPuppet.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/render/ActorPuppetRenderer.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/render/RenderActor.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/RecordManager.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/server/recording/ServerHandlerUpdatePlayerData.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/scene/SceneActorRescue.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/scene/GroupRecording.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/scene/SceneMerge.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/commands/SceneCommandCompleter.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/RecordUtils.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/server/recording/ServerHandlerFramesChunk.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/client/recording/ClientHandlerPlayback.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/common/scene/PacketSceneCast.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/server/scene/ServerHandlerSceneCast.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/server/scene/ServerHandlerSceneRequestCast.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/scene/SceneEditors.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/common/scene/PacketSceneEditing.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/common/scene/PacketSceneEditors.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/common/scene/PacketSceneLive.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/common/scene/PacketSceneBackup.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/common/scene/PacketSceneManage.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/server/scene/ServerHandlerSceneEditing.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/server/scene/ServerHandlerSceneManage.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/client/scene/ClientHandlerSceneEditors.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/client/scene/ClientHandlerSceneLive.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/client/scene/ClientHandlerSceneBackup.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/scene/GuiSceneManager.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/scene/GuiReplaySelector.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/KeyboardHandler.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/render/MwrpPoseBridge.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/render/MwrpCustomPose.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/scene/SceneAutoResume.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster_pack/client/gui/GuiNpcSkinPicker.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster_pack/client/gui/GuiCustomMorph.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster_pack/morphs/CustomMorph.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/LTHelper.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/actions/InteractBlockAction.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/common/PacketLoopSceneAction.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/common/PacketLoopSceneList.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/server/ServerHandlerLoopSceneAction.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/client/ClientHandlerLoopSceneList.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/scene/GuiLoopOverview.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/scene/GuiLoopScenesPanel.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/common/PacketLittleTilesClick.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/server/ServerHandlerLittleTilesClick.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/capturing/WorldEventListener.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/render/GtwSkins.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/recording_editor/actions/GuiMarkerActionPanel.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/recording_editor/actions/GuiMwrpPoseActionPanel.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/client/ClientHandlerActorPose.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/common/PacketActorPose.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/common/PacketRecordMarker.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/network/server/ServerHandlerRecordMarker.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/actions/MarkerAction.java"
echo "%ROOTFWD%/src/main/java/mchorse/blockbuster/recording/actions/MwrpPoseAction.java"
) > "%SRCLIST%"

echo === Compiling ===
"%JAVA%/javac" -source 1.8 -target 1.8 -proc:none -cp "%CP%" -d "%WORK%\output" @"%SRCLIST%"
if errorlevel 1 (
    echo COMPILE FAILED
    pause
    exit /b 1
)

echo === Packaging classes ===
cd /d "%WORK%\output"
"%JAVA%/jar" cf "%WORK%/mcp.jar" mchorse

echo === Reobfuscating MCP -^> SRG ===
"%JAVA%/java" -cp "%CP%;%SPECIAL_SOURCE%" net.md_5.specialsource.SpecialSource --in-jar "%WORK%/mcp.jar" --out-jar "%WORK%/srg.jar" --srg-in "%MCP_SRG%" --live
if errorlevel 1 (
    echo REOBF FAILED
    pause
    exit /b 1
)

echo === Patching JAR ===
cd /d "%WORK%\srg_out"
"%JAVA%\jar" xf "%WORK%\srg.jar"
copy /y "%BB_JAR%" "%WORK%\blockbuster-final.jar" >nul
if errorlevel 1 (
    echo COPY OF BASE JAR FAILED - is %BB_JAR% missing?
    pause
    exit /b 1
)
"%JAVA%/jar" uf "%WORK%\blockbuster-final.jar" mchorse
python "%ROOT%\set_version.py" "%WORK%\blockbuster-final.jar" "%BB_VERSION%"
if errorlevel 1 (
    echo VERSION FAILED
    exit /b 1
)

echo === Deploying to mods folder ===
copy /y "%WORK%\blockbuster-final.jar" "%BB_JAR%"
copy /y "%WORK%\blockbuster-final.jar" "%ROOT%\blockbuster-2.7-1.12.2-dynamxfix.jar"

echo.
echo === DONE! Restart Minecraft to test. ===
echo.
pause
