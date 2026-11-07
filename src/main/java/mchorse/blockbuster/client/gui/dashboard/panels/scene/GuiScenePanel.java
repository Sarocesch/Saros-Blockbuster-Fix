package mchorse.blockbuster.client.gui.dashboard.panels.scene;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.aperture.CameraHandler;
import mchorse.blockbuster.client.gui.dashboard.GuiBlockbusterPanel;
import mchorse.blockbuster.common.BlockbusterPermissions;
import mchorse.blockbuster.common.item.ItemPlayback;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.PacketPlaybackButton;
import mchorse.blockbuster.network.common.recording.PacketUpdatePlayerData;
import mchorse.blockbuster.network.common.scene.PacketSceneCast;
import mchorse.blockbuster.network.common.scene.PacketScenePause;
import mchorse.blockbuster.network.common.scene.PacketScenePlayback;
import mchorse.blockbuster.network.common.scene.PacketSceneRecord;
import mchorse.blockbuster.network.server.recording.ServerHandlerFramesOverwrite;
import mchorse.blockbuster.network.server.recording.ServerHandlerRequestRecording;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.recording.scene.Replay;
import mchorse.blockbuster.recording.scene.Scene;
import mchorse.blockbuster.recording.scene.SceneLocation;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiCollapseSection;
import mchorse.mclib.client.gui.framework.elements.GuiDelegateElement;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiCirculateElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTextElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiStringListElement;
import mchorse.mclib.client.gui.framework.elements.modals.GuiModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiPopUpModal;
import mchorse.mclib.client.gui.framework.elements.modals.GuiPromptModal;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.framework.elements.utils.GuiLabel;
import mchorse.mclib.client.gui.mclib.GuiDashboard;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.permissions.PermissionCategory;
import mchorse.mclib.utils.ColorUtils;
import mchorse.mclib.utils.Direction;
import mchorse.mclib.utils.MathUtils;
import mchorse.mclib.utils.OpHelper;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.creative.GuiNestedEdit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GuiScenePanel extends GuiBlockbusterPanel
{
    private GuiElement subChildren;
    private GuiDelegateElement<GuiElement> mainView;
    private GuiElement replays;
    private GuiElement replayEditor;
    private GuiElement configOptions;
    private GuiReplaySelector selector;

    /* Config fields */
    public GuiTextElement title;
    public GuiTextElement startCommand;
    public GuiTextElement stopCommand;
    public GuiToggleElement loops;

    public GuiStringListElement audio;
    public GuiTrackpadElement audioShift;
    public GuiIconElement openAudioFolder;

    /* Replay fields */
    public GuiTextElement id;
    public GuiTextElement name;
    public GuiTextElement target;
    public GuiToggleElement playbackXPFood;
    public GuiToggleElement invincible;
    public GuiToggleElement invisible;
    public GuiToggleElement enableBurning;
    public GuiToggleElement enabled;
    public GuiToggleElement fake;
    public GuiToggleElement teleportBack;
    public GuiToggleElement renderLast;
    public GuiToggleElement freezeAtEnd;
    public GuiToggleElement hideOuterLayer;
    public GuiToggleElement recorderSkin;
    public GuiTextElement idleRecord;
    public GuiButtonElement npcSkin;
    public GuiStringListElement npcSkins;
    public GuiTextElement recordPlayer;
    public GuiTrackpadElement health;
    public GuiTrackpadElement foodLevel;
    public GuiTrackpadElement totalExperience;

    public GuiButtonElement record;
    public GuiButtonElement recordGroup;
    public GuiButtonElement rename;
    public GuiButtonElement attach;
    public GuiButtonElement camera;
    public GuiButtonElement teleport;
    public GuiCollapseSection eulerFilter;
    public GuiCirculateElement eulerFilterChannel;
    public GuiTrackpadElement eulerFilterFrom;
    public GuiTrackpadElement eulerFilterTo;
    public GuiButtonElement eulerFilterExecute;

    public GuiLabel recordingId;
    public GuiNestedEdit pickMorph;

    public GuiSceneManager scenes;

    private SceneLocation location = new SceneLocation();
    private Replay replay;

    /**
     * Stand der Szene beim Laden vom Server. Beim Schliessen geht nur das an den
     * Server, was sich dagegen geaendert hat - sonst ueberschrieb jeder, der das
     * Menue zumachte, die Aenderungen der anderen.
     */
    private mchorse.blockbuster.recording.scene.SceneMerge.Baseline baseline;

    /* Live-Abgleich: Eingaben seit dem letzten Senden, und eine Fassung vom
     * Server, die noch auf das Einspielen wartet. */
    private boolean touched;
    private long lastInput;
    private long lastHeartbeat;
    private boolean announced;
    private boolean pushedLive;
    private SceneLocation pendingLive;

    /* Wer die Szene ausser mir gerade offen hat (vom Server). */
    private String editorsScene = "";
    private List<String> editorNames = new java.util.ArrayList<String>();
    private List<String> editorReplays = new java.util.ArrayList<String>();

    private IKey noneAudioTrack = IKey.lang("blockbuster.gui.director.none");

    public GuiScenePanel(Minecraft mc, GuiDashboard dashboard)
    {
        super(mc, dashboard);

        this.selector = new GuiReplaySelector(mc, (replay) -> this.setReplay(replay.get(0)));
        this.selector.flex().set(0, 0, 0, 60).relative(this).w(1, -20).y(1, -60);

        GuiElement left = new GuiElement(mc);
        GuiElement right = new GuiElement(mc);

        left.flex().relative(this).w(120).y(20).hTo(this.selector.flex()).column(5).width(100).height(20).padding(10);
        right.flex().relative(this).x(1F).y(20).w(120).hTo(this.selector.flex()).anchorX(1F).column(5).flip().width(100).height(20).padding(10);

        this.subChildren = new GuiElement(mc).noCulling();
        this.subChildren.setVisible(false);
        this.replays = new GuiElement(mc).noCulling();
        this.replayEditor = new GuiElement(mc).noCulling();
        this.replayEditor.setVisible(false);
        this.replayEditor.add(left, right);
        this.configOptions = new GuiElement(mc).noCulling();
        this.mainView = new GuiDelegateElement<GuiElement>(mc, this.replays);
        this.mainView.noCulling();

        this.add(this.subChildren);
        this.subChildren.add(this.mainView);

        /* Config options */
        this.title = new GuiTextElement(mc, 80, (str) -> this.location.getScene().title = str);
        this.startCommand = new GuiTextElement(mc, 10000, (str) -> this.location.getScene().startCommand = str);
        this.stopCommand = new GuiTextElement(mc, 10000, (str) -> this.location.getScene().stopCommand = str);
        this.loops = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.loops"), false, (b) -> this.location.getScene().loops = b.isToggled());


        this.audio = new GuiStringListElement(mc, (value) -> this.location.getScene().setAudio(value.get(0).equals(this.noneAudioTrack.get()) ? "" : value.get(0)));
        this.audio.background().tooltip(IKey.lang("blockbuster.gui.director.audio_tooltip"), Direction.RIGHT);
        this.audioShift = new GuiTrackpadElement(mc, (value) -> this.location.getScene().setAudioShift(value.intValue()));
        this.audioShift.integer().tooltip(IKey.lang("blockbuster.gui.director.audio_shift_tooltip"));
        this.openAudioFolder = new GuiIconElement(mc, Icons.FOLDER, (b) -> GuiUtils.openFolder(ClientProxy.audio.folder.getAbsolutePath()));
        this.openAudioFolder.tooltip(IKey.lang("blockbuster.gui.director.open_audio_folder"));

        this.title.flex().set(120, 50, 0, 20).relative(this.area).w(1, -130);
        this.startCommand.flex().set(120, 90, 0, 20).relative(this.area).w(1, -130);
        this.stopCommand.flex().set(120, 130, 0, 20).relative(this.area).w(1, -130);

        this.audio.flex().relative(this).xy(10, 50).w(100).hTo(this.stopCommand.area, 1F);
        this.audioShift.flex().relative(this.audio).y(1F, 5).w(1F);
        this.openAudioFolder.flex().relative(this.audio).x(1F, -16).y(-16).wh(16, 16);

        GuiElement row = Elements.row(mc, 5, 0, 20, this.loops);

        row.flex().relative(this.stopCommand).y(25).w(1F);
        this.loops.flex().h(20);

        this.configOptions.add(this.title, this.startCommand, this.stopCommand, row, this.audio, this.audioShift, this.openAudioFolder);

        /* Replay options */
        this.id = new GuiTextElement(mc, 120, (str) ->
        {
            this.replay.id = str;

            this.updateLabel(true);
        }).filename();
        this.name = new GuiTextElement(mc, 80, (str) -> this.replay.name = str);
        this.name.tooltip(IKey.lang("blockbuster.gui.director.name_tooltip"), Direction.RIGHT);
        this.target = new GuiTextElement(mc, 80, (str) -> this.replay.target = str);
        this.target.tooltip(IKey.lang("blockbuster.gui.director.target_tooltip"), Direction.LEFT);
        this.playbackXPFood = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.playback_xp_food_level_enabled"), (b) -> this.replay.playBackXPFood = b.isToggled());
        this.playbackXPFood.tooltip(IKey.lang("blockbuster.gui.director.playback_xp_food_level_tooltip"), Direction.LEFT);
        this.invincible = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.invincible"), false, (b) -> this.replay.invincible = b.isToggled());
        this.invisible = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.invisible"), false, (b) -> this.replay.invisible = b.isToggled());
        this.enableBurning = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.enable_burning"), true, (b) -> this.replay.enableBurning = b.isToggled());
        this.enabled = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.enabled"), false, (b) -> this.replay.enabled = b.isToggled());
        this.fake = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.fake_player"), false, (b) -> this.replay.fake = b.isToggled());
        this.teleportBack = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.tp_back"), false, (b) -> this.replay.teleportBack = b.isToggled());
        this.teleportBack.tooltip(IKey.lang("blockbuster.gui.director.tp_back_tooltip"), Direction.RIGHT);
        this.renderLast = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.director.render_last"), false, (b) -> this.replay.renderLast = b.isToggled());
        this.renderLast.tooltip(IKey.lang("blockbuster.gui.director.render_last_tooltip"), Direction.RIGHT);
        /* Literal label: the build patches classes into the jar, not the lang assets. */
        this.freezeAtEnd = new GuiToggleElement(mc, IKey.str("Freeze at end"), false, (b) -> this.replay.freezeAtEnd = b.isToggled());
        this.freezeAtEnd.tooltip(IKey.str("This actor and the vehicle it drove stay where the scene ended. Cleared when the scene is triggered again."), Direction.RIGHT);
        this.hideOuterLayer = new GuiToggleElement(mc, IKey.str("Hide outer skin layer"), false, (b) -> this.replay.hideOuterLayer = b.isToggled());
        this.hideOuterLayer.tooltip(IKey.str("Hides the model's second skin layer (bodywear, armwear, legwear, hat) so worn clothing does not clip through it."), Direction.RIGHT);
        this.recorderSkin = new GuiToggleElement(mc, IKey.str("Skin des Aufnehmenden (/skin + CPM)"), false, (b) -> this.replay.recorderSkin = b.isToggled());
        this.recorderSkin.tooltip(IKey.str("Zeigt den Actor als echtes Spielermodell mit dem /skin des Spielers, der die Aufnahme gemacht hat - inklusive CPM-Modell und 3D-Augen. "
            + "Geht auch, wenn dieser Spieler offline ist. Braucht eine Aufnahme ab diesem Update, oder das Aussehen wurde mit 'Update player data' nachgetragen. "
            + "Ohne gespeichertes Aussehen sieht der Actor aus wie bisher. Wirkt beim naechsten Start der Szene."), Direction.RIGHT);
        this.idleRecord = new GuiTextElement(mc, 120, (str) -> this.replay.idleRecord = str).filename();
        this.idleRecord.tooltip(IKey.str("Recording this actor loops after the scene ends. Empty means it just stops."), Direction.RIGHT);
        this.npcSkins = new GuiStringListElement(mc, (value) ->
        {
            String picked = value.get(0);

            this.replay.npcSkin = picked.equals(NO_NPC_SKIN) ? "" : mchorse.blockbuster.client.render.GtwSkins.PREFIX + picked;
            this.npcSkins.setVisible(false);
            this.updateNpcSkinLabel();
        });
        this.npcSkins.background();
        this.npcSkins.setVisible(false);
        this.npcSkin = new GuiButtonElement(mc, IKey.str("NPC-Skin: keiner"), (b) -> this.toggleNpcSkins());
        this.npcSkin.tooltip(IKey.str("Skin aus GtwNpc (alle NPC-Skins). Zeigt den Actor als Spielermodell mit diesem Skin, "
            + "dann sind auch MWRP-Posen aus der Aufnahme sichtbar. Geht vor 'Skin des Aufnehmenden'. Wirkt beim naechsten Start der Szene."), Direction.RIGHT);
        this.recordPlayer = new GuiTextElement(mc, 80, (str) -> this.replay.recordPlayer = str.trim());
        this.recordPlayer.tooltip(IKey.str("Spielername, der diesen Actor bei der Gruppenaufnahme aufnimmt. Leer = spielt bei der Gruppenaufnahme einfach mit."), Direction.LEFT);
        this.health = new GuiTrackpadElement(mc, (value) -> this.replay.health = value.floatValue());
        this.health.limit(0);
        this.foodLevel = new GuiTrackpadElement(mc, (value) -> this.replay.foodLevel = value.intValue());
        this.foodLevel.limit(0).integer();
        this.totalExperience = new GuiTrackpadElement(mc, (value) -> this.replay.totalExperience = value.intValue());
        this.totalExperience.limit(0).integer();
        this.recordingId = Elements.label(IKey.lang("blockbuster.gui.director.id")).color(0xcccccc);

        left.add(this.recordingId, this.id);
        left.add(Elements.label(IKey.lang("blockbuster.gui.director.name")).color(0xcccccc), this.name);
        left.add(Elements.label(IKey.lang("blockbuster.gui.director.health")).color(0xcccccc), this.health,
                 Elements.label(IKey.lang("blockbuster.gui.director.food_level")).color(0xcccccc), this.foodLevel,
                 Elements.label(IKey.lang("blockbuster.gui.director.total_experience")).color(0xcccccc), this.totalExperience,
                 this.invincible, this.invisible, this.enableBurning, this.enabled, this.fake, this.teleportBack, this.renderLast, this.freezeAtEnd, this.hideOuterLayer, this.recorderSkin,
                 Elements.label(IKey.str("Idle loop")).color(0xcccccc), this.idleRecord, this.npcSkin);
        this.replays.add(this.selector, this.replayEditor);

        /* Toggle view button */
        GuiIconElement toggle = new GuiIconElement(mc, Icons.GEAR, (b) ->
        {
            this.mainView.setDelegate(this.mainView.delegate == this.configOptions ? this.replays : this.configOptions);
        });

        GuiIconElement toggleScenes = new GuiIconElement(mc, Icons.MORE, (b) -> this.scenes.toggleVisible());
        toggleScenes.flex().y(4).relative(this.area).x(1, -24);

        toggle.tooltip(IKey.lang("blockbuster.gui.director.config"), Direction.LEFT);
        toggle.flex().y(4).relative(this.area).x(1, -44);

        this.add(toggleScenes);
        this.subChildren.add(toggle);

        /* Add, duplicate and remove replay buttons */
        GuiIconElement add = new GuiIconElement(mc, Icons.ADD, (b) -> this.addReplay());
        GuiIconElement dupe = new GuiIconElement(mc, Icons.DUPE, (b) -> this.dupeReplay());
        GuiIconElement remove = new GuiIconElement(mc, Icons.REMOVE, (b) -> this.removeReplay());

        add.tooltip(IKey.lang("blockbuster.gui.director.add_replay"), Direction.LEFT);
        dupe.tooltip(IKey.lang("blockbuster.gui.director.dupe_replay"), Direction.LEFT);
        remove.tooltip(IKey.lang("blockbuster.gui.director.remove_replay"), Direction.LEFT);

        add.flex().set(0, 0, 20, 20).relative(this.selector.resizer()).x(1F);
        dupe.flex().set(0, 20, 20, 20).relative(this.selector.resizer()).x(1F);
        remove.flex().set(0, 40, 20, 20).relative(this.selector.resizer()).x(1F);

        this.replays.add(add, dupe, remove);

        /* Additional utility buttons */
        IKey category = IKey.lang("blockbuster.gui.director.keys.category");
        Supplier<Boolean> active = () -> this.replay != null;

        this.pickMorph = new GuiNestedEdit(mc, (editing) -> ClientProxy.panels.addMorphs(this, editing, this.replay.morph));
        this.record = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.record"), (b) -> this.sendRecordMessage());
        this.recordGroup = new GuiButtonElement(mc, IKey.str("Gruppenaufnahme"), (b) -> this.recordGroup());
        this.recordGroup.tooltip(IKey.str("Nimmt alle Actors mit eingetragenem 'Aufnahme-Spieler' gleichzeitig auf: gemeinsamer Countdown, die uebrigen Actors spielen ab. "
            + "Nochmal klicken oder die Aufnahme-Taste des Startenden beendet die Aufnahme fuer alle. Auch per /scene recordall <szene>."), Direction.LEFT);
        GuiButtonElement edit = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.director.edit_record"), (b) -> this.openRecordEditor());
        GuiButtonElement update = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.director.update_data"), (b) -> this.updatePlayerData());
        update.tooltip(IKey.str("Uebernimmt deine Spielerdaten in diese Aufnahme - jetzt auch deinen /skin und dein CPM-Modell (fuer 'Skin des Aufnehmenden')."), Direction.RIGHT);
        this.rename = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.director.rename_prefix"), (b) -> this.renamePrefix());
        this.attach = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.director.attach"), (b) -> this.attach());
        this.teleport = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.director.tp"), (b) -> this.teleport());
        this.teleport.keys().register(this.teleport.label, Keyboard.KEY_T, () -> this.teleport.clickItself(GuiBase.getCurrent())).category(category).active(active);

        this.eulerFilter = new GuiCollapseSection(mc, IKey.lang("blockbuster.gui.director.rotation_filter.title"));
        this.eulerFilter.setCollapsed(true);

        this.eulerFilterFrom = new GuiTrackpadElement(mc, (Consumer<Double>) null);
        this.eulerFilterFrom.limit(0).integer();
        this.eulerFilterFrom.tooltip(IKey.lang("blockbuster.gui.director.rotation_filter.from_tooltip"));

        this.eulerFilterTo = new GuiTrackpadElement(mc, (Consumer<Double>) null);
        this.eulerFilterTo.limit(0).integer();
        this.eulerFilterTo.tooltip(IKey.lang("blockbuster.gui.director.rotation_filter.to_tooltip"));

        this.eulerFilterChannel = new GuiCirculateElement(mc, null);
        this.eulerFilterChannel.addLabel(IKey.lang("blockbuster.gui.director.rotation_filter.head_yaw"));
        this.eulerFilterChannel.addLabel(IKey.lang("blockbuster.gui.director.rotation_filter.head_pitch"));
        this.eulerFilterChannel.addLabel(IKey.lang("blockbuster.gui.director.rotation_filter.body_yaw"));
        this.eulerFilterChannel.tooltip(IKey.lang("blockbuster.gui.director.rotation_filter.channel_tooltip"));

        this.eulerFilterExecute = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.director.rotation_filter.execute"), (b) ->
        {
            this.rotationFilter();
        });

        this.eulerFilter.addFields(this.eulerFilterChannel, this.eulerFilterFrom, this.eulerFilterTo, this.eulerFilterExecute);

        this.pickMorph.flex().relative(this.selector).x(0.5F).y(-10).w(100).anchor(0.5F, 1F);

        update.tooltip(IKey.lang("blockbuster.gui.director.update_data_tooltip"), Direction.LEFT);
        this.rename.tooltip(IKey.lang("blockbuster.gui.director.rename_prefix_tooltip"), Direction.LEFT);
        this.attach.tooltip(IKey.lang("blockbuster.gui.director.attach_tooltip"), Direction.LEFT);
        this.teleport.tooltip(IKey.lang("blockbuster.gui.director.tp_tooltip"), Direction.LEFT);

        right.add(this.attach, this.record, this.recordGroup, update, this.rename, edit);

        if (CameraHandler.isApertureLoaded())
        {
            this.camera = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.director.camera"), (b) ->
            {
                CameraHandler.location = this.location;
                CameraHandler.openCameraEditor();
            });
            this.camera.keys().register(this.camera.label, Keyboard.KEY_C, () -> this.camera.clickItself(GuiBase.getCurrent())).category(category).active(active);

            right.add(this.camera);
        }

        right.add(this.teleport, this.eulerFilter);
        right.add(Elements.label(IKey.lang("blockbuster.gui.director.target")).color(0xcccccc).marginTop(12), this.target, Elements.label(IKey.lang("blockbuster.gui.director.playback_xp_food_level")), this.playbackXPFood);
        right.add(Elements.label(IKey.str("Aufnahme-Spieler")).color(0xcccccc).marginTop(12), this.recordPlayer);
        this.replayEditor.add(this.pickMorph);

        /* Zuletzt, damit die Liste ueber allem liegt. */
        this.npcSkins.flex().relative(this).x(130).y(20).w(200).hTo(this.selector.flex());
        this.replayEditor.add(this.npcSkins);

        /* Scene manager */
        this.add(this.scenes = new GuiSceneManager(mc, this));
        this.scenes.flex().relative(toggleScenes).xy(1F, 1F).w(160).hTo(this.selector.flex()).anchorX(1F);
        this.scenes.setVisible(false);

        this.keys().register(IKey.lang("blockbuster.gui.director.keys.toggle_list"), Keyboard.KEY_N, () -> toggleScenes.clickItself(GuiBase.getCurrent())).category(category);
        this.keys().register(IKey.lang("blockbuster.gui.director.keys.toggle_options"), Keyboard.KEY_O, () -> toggle.clickItself(GuiBase.getCurrent())).category(category);
    }

    @Override
    public PermissionCategory getRequiredPermission()
    {
        return BlockbusterPermissions.openScene;
    }

    public SceneLocation getLocation()
    {
        return this.location;
    }

    public Replay getReplay()
    {
        return this.replay;
    }

    public List<Replay> getReplays()
    {
        if (this.location.isEmpty())
        {
            return null;
        }

        return this.getLocation().getScene().replays;
    }

    public GuiScenePanel openScene(SceneLocation location)
    {
        this.scenes.setVisible(false);

        return this.setScene(location);
    }

    public GuiScenePanel setScene(SceneLocation location)
    {
        this.location = location == null ? new SceneLocation() : location;
        this.takeBaseline();

        this.subChildren.setVisible(!location.isEmpty());
        this.replayEditor.setVisible(!location.isEmpty());
        this.scenes.setScene(location.getScene());

        if (location.isEmpty())
        {
            this.setReplay(null);

            return this;
        }

        this.selector.setList(location.getScene().replays);

        if (!this.location.getScene().replays.isEmpty())
        {
            int current = this.location.getScene().replays.indexOf(this.replay);

            this.setReplay(this.location.getScene().replays.get(current == -1 ? 0 : current));
        }
        else
        {
            this.setReplay(null);
        }

        this.fillData();

        return this;
    }

    public GuiScenePanel set(SceneLocation location)
    {
        this.location = location;
        this.takeBaseline();
        this.scenes.setScene(location.getScene());

        return this;
    }

    /**
     * Neuen Vergleichsstand merken - nur fuer eine neu empfangene Szene, nicht
     * wenn dieselbe nur wieder angezeigt wird (sonst gingen Aenderungen verloren).
     */
    private void takeBaseline()
    {
        Scene scene = this.location.getScene();

        if (scene == null)
        {
            this.baseline = null;
        }
        else if (this.baseline == null || this.baseline.scene != scene)
        {
            this.baseline = new mchorse.blockbuster.recording.scene.SceneMerge.Baseline(scene);
        }
    }

    protected void rotationFilter()
    {
        int fromTest = Math.min((int) this.eulerFilterFrom.value, (int) this.eulerFilterTo.value);
        int toTest = Math.max((int) this.eulerFilterFrom.value, (int) this.eulerFilterTo.value);

        if (toTest - fromTest + 1 < 2)
        {
            this.addPopUpModal(IKey.lang("blockbuster.gui.director.rotation_filter.not_enough_frames"));

            return;
        }

        /* wait for the requested recording to return from server */
        ServerHandlerRequestRecording.requestRecording(this.replay.id, (record) ->
        {
            Frame.RotationChannel channel = Frame.RotationChannel.values()[this.eulerFilterChannel.getValue()];

            if (record == null)
            {
                this.addPopUpModal(IKey.lang("blockbuster.gui.director.rotation_filter.record_not_loaded"));

                return;
            }

            int from = MathUtils.clamp(Math.min((int) this.eulerFilterFrom.value, (int) this.eulerFilterTo.value), 0, record.frames.size() - 1);
            int to = MathUtils.clamp(Math.max((int) this.eulerFilterFrom.value, (int) this.eulerFilterTo.value), 0, record.frames.size() - 1);

            List<Frame> frames = RecordUtils.discontinuityEulerFilter(record.frames, from, to, channel);

            if (frames.isEmpty())
            {
                this.addPopUpModal(IKey.lang("blockbuster.gui.director.rotation_filter.empty_filtered_frames"));

                return;
            }

            ServerHandlerFramesOverwrite.sendFramesToServer(record.filename, frames, from, to, (obj) ->
            {
                this.addPopUpModal(obj.getKey());
            });
        });
    }

    private void addPopUpModal(IKey lang)
    {
        GuiPopUpModal modal = new GuiPopUpModal(this.mc, lang);
        modal.flex().relative(this.parent).wh(200, 50);
        modal.setFadeDuration(0);
        modal.resize();

        this.add(modal);
    }

    @Override
    public void appear()
    {
        super.appear();

        ClientProxy.panels.picker(this::setMorph);

        if (!this.location.isEmpty())
        {
            this.setScene(this.location);
        }
    }

    @Override
    public void open()
    {
        ClientProxy.panels.morphs.reload();

        this.setScene(this.location);
        this.scenes.setScene(this.location.getScene());
        this.scenes.updateSceneList();

        /* Der Server ist fuehrend: beim Oeffnen immer seine aktuelle Fassung holen,
         * nicht mit der vom letzten Mal weiterarbeiten. */
        if (this.location.isScene())
        {
            Dispatcher.sendToServer(new mchorse.blockbuster.network.common.scene.PacketSceneRequestCast(new SceneLocation(this.location.getFilename())));
        }
    }

    @Override
    public void close()
    {
        this.leave();

        if (!OpHelper.isPlayerOp())
        {
            return;
        }

        if (this.location.isScene())
        {
            if (ClientProxy.panels.morphs.hasParent())
            {
                ClientProxy.panels.morphs.finish();
            }

            this.saveChanges();
        }
    }

    @Override
    public void disappear()
    {
        super.disappear();

        if (!ClientProxy.panels.morphs.hasParent())
        {
            this.saveChanges();
        }

        this.leave();
    }

    /**
     * Eigene Aenderungen an den Server schicken (nur, was sich gegenueber dem
     * letzten Stand geaendert hat). Der Server mischt sie in seine Fassung und
     * schickt das Ergebnis an alle, die die Szene offen haben.
     */
    public void saveChanges()
    {
        this.saveChanges(false);
    }

    /**
     * @param live Zwischenspeichern bei offenem Menue: der Server schreibt nur die
     *             Datei. Das naechste normale Speichern laedt eine laufende Szene
     *             dann einmal neu, auch wenn seitdem nichts mehr geaendert wurde.
     */
    private void saveChanges(boolean live)
    {
        this.touched = false;

        Scene scene = this.location.getScene();

        if (!OpHelper.isPlayerOp() || !this.location.isScene() || scene == null)
        {
            return;
        }

        PacketSceneCast packet = new PacketSceneCast(this.location);

        if (this.baseline != null && this.baseline.scene == scene)
        {
            packet.merge = this.baseline.diff(!live && this.pushedLive);

            /* Nichts geaendert: nichts schicken. Vorher speicherte jedes Schliessen
             * die ganze Szene und lud eine laufende neu. */
            if (packet.merge == null)
            {
                return;
            }

            packet.live = live;
        }

        this.pushedLive = live;

        Dispatcher.sendToServer(packet);
        this.baseline = new mchorse.blockbuster.recording.scene.SceneMerge.Baseline(scene);
    }

    /**
     * Die Szene kennt der Server noch nicht (neu angelegt): beim naechsten
     * Speichern ganz schicken statt nur Aenderungen.
     */
    public void markUnsaved()
    {
        this.baseline = null;
        this.touched = true;
        this.lastInput = System.currentTimeMillis();
    }

    private void leave()
    {
        if (this.announced && this.mc.player != null)
        {
            Dispatcher.sendToServer(new mchorse.blockbuster.network.common.scene.PacketSceneEditing("", ""));
        }

        this.announced = false;
        this.lastHeartbeat = 0;
        this.editorNames.clear();
        this.editorReplays.clear();
        this.selector.editedBy.clear();
    }

    /**
     * Jede Sekunde melden, welche Szene und welcher Actor offen ist, und nach
     * einer kurzen Eingabepause die eigenen Aenderungen live speichern.
     */
    private void liveTick()
    {
        if (!this.location.isScene() || this.mc.player == null || !OpHelper.isPlayerOp())
        {
            return;
        }

        long now = System.currentTimeMillis();

        if (now - this.lastHeartbeat > 1000)
        {
            this.lastHeartbeat = now;
            this.announced = true;
            Dispatcher.sendToServer(new mchorse.blockbuster.network.common.scene.PacketSceneEditing(this.location.getFilename(), this.replay == null ? "" : this.replay.id));
        }

        boolean morphing = ClientProxy.panels.morphs.hasParent();

        if (this.touched && !morphing && now - this.lastInput > 1500)
        {
            this.saveChanges(true);
        }

        if (this.pendingLive != null && !morphing && !this.touched)
        {
            this.applyLive();
        }
    }

    /**
     * Neue Fassung der Szene vom Server (jemand hat gespeichert, oder die
     * Antwort auf das eigene Speichern mit den Aenderungen der anderen).
     */
    public void liveUpdate(SceneLocation location)
    {
        if (!this.location.isScene() || !location.isScene() || !this.location.getFilename().equals(location.getFilename()))
        {
            return;
        }

        this.pendingLive = location;

        boolean shown = this.mc.currentScreen == this.dashboard && this.dashboard.panels.view.delegate == this;

        /* Menue zu oder anderes Panel: es kann gerade niemand etwas aendern. */
        if (!shown)
        {
            this.pendingLive = null;
            this.set(location);
        }
    }

    /**
     * Wartende Fassung einspielen. Nur ohne eigene, noch nicht gesendete
     * Eingaben (die gehen zuerst raus, die Antwort enthaelt dann beides) und
     * nicht waehrend der Morph-Editor offen ist.
     */
    private void applyLive()
    {
        SceneLocation incoming = this.pendingLive;
        Scene current = this.location.getScene();

        this.pendingLive = null;

        if (incoming == null || incoming.getScene() == null || !incoming.getFilename().equals(this.location.getFilename()))
        {
            return;
        }

        String id = this.replay == null ? null : this.replay.id;
        int index = this.replay == null || current == null ? -1 : current.replays.indexOf(this.replay);
        GuiElement view = this.mainView.delegate;

        this.setScene(incoming);

        List<Replay> replays = incoming.getScene().replays;
        Replay pick = null;

        if (index >= 0 && index < replays.size() && replays.get(index).id.equals(id))
        {
            pick = replays.get(index);
        }

        for (int i = 0; pick == null && id != null && i < replays.size(); i++)
        {
            if (replays.get(i).id.equals(id))
            {
                pick = replays.get(i);
            }
        }

        if (pick == null && index >= 0 && index < replays.size())
        {
            pick = replays.get(index);
        }

        if (pick != null)
        {
            this.setReplay(pick);
        }

        this.mainView.setDelegate(view);
    }

    public void setEditors(String scene, List<String> names, List<String> replays)
    {
        this.editorsScene = scene;
        this.editorNames = names;
        this.editorReplays = replays;
        this.selector.editedBy.clear();

        for (int i = 0; i < names.size(); i++)
        {
            String id = replays.get(i);

            if (!id.isEmpty())
            {
                String other = this.selector.editedBy.get(id);

                this.selector.editedBy.put(id, other == null ? names.get(i) : other + ", " + names.get(i));
            }
        }
    }

    private void input()
    {
        this.touched = true;
        this.lastInput = System.currentTimeMillis();
    }

    @Override
    public boolean mouseClicked(GuiContext context)
    {
        this.input();

        return super.mouseClicked(context);
    }

    @Override
    public void mouseReleased(GuiContext context)
    {
        if (this.touched)
        {
            this.input();
        }

        super.mouseReleased(context);
    }

    @Override
    public boolean mouseScrolled(GuiContext context)
    {
        this.input();

        return super.mouseScrolled(context);
    }

    @Override
    public boolean keyTyped(GuiContext context)
    {
        this.input();

        return super.keyTyped(context);
    }

    /**
     * Oben links: wer die Szene ausser mir offen hat. Ueber dem Actor eine
     * Warnung, wenn jemand anderes gerade denselben Actor ausgewaehlt hat.
     */
    private void drawEditors()
    {
        boolean current = this.location.isScene() && this.location.getFilename().equals(this.editorsScene);

        if (!current || this.editorNames.isEmpty())
        {
            return;
        }

        StringBuilder text = new StringBuilder("Auch im Editor: ");
        StringBuilder same = new StringBuilder();

        for (int i = 0; i < this.editorNames.size(); i++)
        {
            String name = this.editorNames.get(i);
            String id = this.editorReplays.get(i);

            text.append(i > 0 ? ", " : "").append(name);

            if (!id.isEmpty())
            {
                text.append(" (").append(id).append(")");
            }

            if (this.replay != null && !this.replay.id.isEmpty() && this.replay.id.equals(id))
            {
                same.append(same.length() > 0 ? ", " : "").append(name);
            }
        }

        String line = this.font.trimStringToWidth(text.toString(), Math.max(60, this.area.w / 2 - 20));

        this.font.drawStringWithShadow(line, this.area.x + 10, this.area.y + 24, 0xffcc55);

        if (same.length() > 0 && this.mainView.delegate == this.replays)
        {
            String warning = same + " bearbeitet diesen Actor gerade auch - Aenderungen werden live abgeglichen";

            warning = this.font.trimStringToWidth(warning, Math.max(60, this.area.w / 2));
            this.drawCenteredString(this.font, warning, this.area.mx(), this.area.y + 38, 0xff6655);
        }
    }

    private static final String NO_NPC_SKIN = "(keiner)";

    private void toggleNpcSkins()
    {
        if (this.npcSkins.isVisible())
        {
            this.npcSkins.setVisible(false);

            return;
        }

        this.npcSkins.clear();
        this.npcSkins.add(NO_NPC_SKIN);

        for (String location : mchorse.blockbuster.client.render.GtwSkins.all())
        {
            this.npcSkins.add(mchorse.blockbuster.client.render.GtwSkins.shortName(location));
        }

        this.npcSkins.setCurrentScroll(this.replay.npcSkin.isEmpty() ? NO_NPC_SKIN : mchorse.blockbuster.client.render.GtwSkins.shortName(this.replay.npcSkin));
        this.npcSkins.setVisible(true);
    }

    private void updateNpcSkinLabel()
    {
        String skin = this.replay == null || this.replay.npcSkin.isEmpty() ? "keiner" : mchorse.blockbuster.client.render.GtwSkins.shortName(this.replay.npcSkin);

        this.npcSkin.label = IKey.str("NPC-Skin: " + skin);
    }

    private void setReplay(Replay replay)
    {
        if (this.replay != null)
        {
            this.replay.morph = MorphUtils.copy(this.replay.morph);
        }

        this.replay = replay;
        this.replayEditor.setVisible(this.replay != null);
        this.mainView.setDelegate(this.replays);
        this.selector.setCurrent(replay);
        this.fillReplayData();
    }

    private void fillData()
    {
        this.title.setText(this.location.getScene().title);
        this.startCommand.setText(this.location.getScene().startCommand);
        this.stopCommand.setText(this.location.getScene().stopCommand);
        this.loops.toggled(this.location.getScene().loops);
        this.attach.setEnabled(false);

        this.audio.clear();
        this.audio.add(this.noneAudioTrack.get());
        this.audio.add(ClientProxy.audio.getFileNames());
        this.audio.sort();

        String audio = this.location.getScene().getAudio();

        this.audio.setCurrentScroll(audio == null || audio.isEmpty() ? this.noneAudioTrack.get() : audio);

        this.audioShift.setValue(this.location.getScene().getAudioShift());

        if (this.mc != null && this.mc.player != null)
        {
            ItemStack stack = this.mc.player.getHeldItemMainhand();

            this.attach.setEnabled(!this.location.isEmpty() && stack.getItem() instanceof ItemPlayback);
        }
    }

    private void fillReplayData()
    {
        if (this.replay == null)
        {
            return;
        }

        this.id.setText(this.replay.id);
        this.name.setText(this.replay.name);
        this.target.setText(this.replay.target);
        this.playbackXPFood.toggled(this.replay.playBackXPFood);
        this.invincible.toggled(this.replay.invincible);
        this.invisible.toggled(this.replay.invisible);
        this.enableBurning.toggled(this.replay.enableBurning);
        this.enabled.toggled(this.replay.enabled);
        this.fake.toggled(this.replay.fake);
        this.teleportBack.toggled(this.replay.teleportBack);
        this.renderLast.toggled(this.replay.renderLast);
        this.freezeAtEnd.toggled(this.replay.freezeAtEnd);
        this.hideOuterLayer.toggled(this.replay.hideOuterLayer);
        this.recorderSkin.toggled(this.replay.recorderSkin);
        this.idleRecord.setText(this.replay.idleRecord);
        this.npcSkins.setVisible(false);
        this.updateNpcSkinLabel();
        this.recordPlayer.setText(this.replay.recordPlayer);
        this.health.setValue(this.replay.health);
        this.foodLevel.setValue(this.replay.foodLevel);
        this.totalExperience.setValue(this.replay.totalExperience);
        this.pickMorph.setMorph(this.replay.morph);

        this.selector.setCurrent(this.replay);
        this.updateLabel(false);
    }

    /**
     * Add an empty replay 
     */
    private void addReplay()
    {
        Replay replay = new Replay("");

        if (this.location.isScene())
        {
            replay.id = this.location.getScene().getNextBaseSuffix(this.location.getScene().getId());
        }

        this.location.getScene().replays.add(replay);
        this.setReplay(replay);
        this.selector.update();
    }

    /**
     * Duplicate a replay 
     */
    private void dupeReplay()
    {
        if (this.selector.isDeselected())
        {
            return;
        }

        Scene scene = this.location.getScene();

        if (scene.dupe(scene.replays.indexOf(this.replay)))
        {
            this.selector.update();
            this.selector.scroll.scrollTo(this.selector.getIndex() * this.selector.scroll.scrollItemSize);
            this.setReplay(scene.replays.get(scene.replays.size() - 1));
        }
    }

    /**
     * Remove replay 
     */
    private void removeReplay()
    {
        if (this.selector.isDeselected())
        {
            return;
        }

        Scene scene = this.location.getScene();
        int index = this.selector.getIndex();

        scene.replays.remove(this.replay);

        int size = scene.replays.size();
        index = MathHelper.clamp(index, 0, size - 1);

        this.setReplay(size == 0 ? null : scene.replays.get(index));
        this.selector.update();
    }

    private void setMorph(AbstractMorph morph)
    {
        if (this.replay != null)
        {
            this.replay.morph = morph;
        }

        this.pickMorph.setMorph(morph);
    }

    /**
     * update the labels
     * @param gui true when this is called by a callback from a GuiElement
     */
    private void updateLabel(boolean gui)
    {
        boolean error = this.replay != null && this.replay.id.isEmpty();

        this.recordingId.color(error ? 0xff3355 : 0xcccccc);

        if (this.replay != null && !this.replay.id.isEmpty() && gui)
        {
            boolean isDuplicate = false;

            for (Replay element : this.scenes.parent.getLocation().getScene().replays)
            {
                if (element.id.equals(this.replay.id) && this.replay != element)
                {
                    isDuplicate = true;

                    break;
                }
            }

            if (isDuplicate)
            {
                GuiModal.addModal(this, () ->
                {
                    GuiPopUpModal modal = new GuiPopUpModal(this.mc, IKey.lang("blockbuster.gui.director.rename_replay_dupe_modal"));
                    modal.flex().relative(this.parent).wh(220, 50);

                    return modal;
                });
            }
        }
    }

    /**
     * Send record message to the player
     */
    private void sendRecordMessage()
    {
        EntityPlayer player = this.mc.player;

        if (this.replay.id.isEmpty())
        {
            Blockbuster.l10n.error(player, "recording.fill_filename");

            return;
        }

        String command = "/action record " + this.replay.id + " " + this.location.getFilename();
        ITextComponent component = new TextComponentString(I18n.format("blockbuster.info.recording.clickhere"));

        component.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        component.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(command)));
        component.getStyle().setColor(TextFormatting.GRAY).setUnderlined(true);

        Blockbuster.l10n.info(player, "recording.message", this.replay.id, component);

        /* Add the command to the history */
        List<String> messages = this.mc.ingameGUI.getChatGUI().getSentMessages();

        boolean empty = messages.isEmpty();
        boolean lastMessageIsntCommand = !empty && !messages.get(messages.size() - 1).equals(command);

        if (lastMessageIsntCommand || empty)
        {
            messages.add(command);
        }
    }

    private void attach()
    {
        if (CameraHandler.isApertureLoaded())
        {
            CameraHandler.attach(this.location, this.scenes.sceneList.getList());
        }
        else
        {
            Dispatcher.sendToServer(new PacketPlaybackButton(this.location, 0, ""));

            this.mc.displayGuiScreen(null);
        }
    }

    private void openRecordEditor()
    {
        if (this.replay != null && !this.replay.id.isEmpty())
        {
            this.dashboard.panels.setPanel(ClientProxy.panels.recordingEditorPanel);
            ClientProxy.panels.recordingEditorPanel.selectRecord(this.replay.id);
            ClientProxy.panels.recordingEditorPanel.records.setVisible(false);
        }
    }

    private void updatePlayerData()
    {
        Dispatcher.sendToServer(new PacketUpdatePlayerData(this.replay.id));
    }

    private void teleport()
    {
        if (this.replay == null)
        {
            return;
        }

        this.mc.displayGuiScreen(null);

        try
        {
            RecordUtils.applyFrameOnEntity(Minecraft.getMinecraft().player, ClientProxy.manager.get(this.replay.id), 0); //TODO requires client to request recording from server
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void renamePrefix()
    {
        GuiModal.addModal(this.replayEditor, () ->
        {
            GuiPromptModal modal = new GuiPromptModal(this.mc, IKey.lang("blockbuster.gui.director.rename_prefix_popup"), this::renamePrefix);

            modal.markIgnored().flex().relative(this.rename).y(1F).w(1F).h(120);

            return modal;
        });
    }

    private void renamePrefix(String newPrefix)
    {
        this.location.getScene().renamePrefix(newPrefix);
        this.fillReplayData();
    }

    @Override
    public void draw(GuiContext context)
    {
        this.liveTick();

        if (this.scenes.isVisible())
        {
            int x = this.scenes.area.ex() - 20;
            int y = this.scenes.area.y - 20;

            Gui.drawRect(x, y, x + 20, y + 20, ColorUtils.HALF_BLACK);
        }

        /* Draw additional stuff */
        if (this.mainView.delegate == this.replays)
        {
            Gui.drawRect(this.selector.area.x, this.selector.area.y, this.selector.area.ex() + 20, this.selector.area.ey(), ColorUtils.HALF_BLACK);
            this.drawGradientRect(this.selector.area.x, this.selector.area.y - 16, this.selector.area.ex() + 20, this.selector.area.y, 0, ColorUtils.HALF_BLACK);

            this.font.drawStringWithShadow(I18n.format("blockbuster.gui.scenes.title"), this.area.x + 10, this.area.y + 10, 0xffffff);

            if (this.replay != null)
            {
                AbstractMorph morph = this.replay.morph;

                if (morph != null)
                {
                    int x = this.area.mx();
                    int y = this.area.y(0.55F);

                    GuiDraw.scissor(this.area.x, this.area.y, this.area.w, this.area.h, context);
                    morph.renderOnScreen(this.mc.player, x, y, this.area.h / 3.5F, 1.0F);
                    GuiDraw.unscissor(context);
                }
            }
        }
        else
        {
            this.font.drawStringWithShadow(I18n.format("blockbuster.gui.director.config"), this.area.x + 10, this.area.y + 10, 0xffffff);

            this.font.drawStringWithShadow(I18n.format("blockbuster.gui.director.audio"), this.audio.area.x, this.audio.area.y - 12, 0xcccccc);
            this.font.drawStringWithShadow(I18n.format("blockbuster.gui.director.start_command"), this.startCommand.area.x, this.startCommand.area.y - 12, 0xcccccc);
            this.font.drawStringWithShadow(I18n.format("blockbuster.gui.director.stop_command"), this.stopCommand.area.x, this.stopCommand.area.y - 12, 0xcccccc);
            this.font.drawStringWithShadow(I18n.format("blockbuster.gui.director.display_title"), this.title.area.x, this.title.area.y - 12, 0xcccccc);
        }

        if (this.location.isEmpty())
        {
            String no = I18n.format("blockbuster.gui.director.not_selected");

            this.drawCenteredString(this.font, no, this.area.mx(), this.area.my() - 6, 0xffffff);
        }

        super.draw(context);

        this.drawEditors();
    }

    public void plause()
    {
        if (!OpHelper.isPlayerOp())
        {
            return;
        }

        if (this.location.isScene())
        {
            Dispatcher.sendToServer(new PacketScenePlayback(this.location));
        }
    }

    public void record()
    {
        if (!OpHelper.isPlayerOp())
        {
            return;
        }

        Replay replay = this.replay;

        if (replay != null && !replay.id.isEmpty() && this.location.isScene())
        {
            Dispatcher.sendToServer(new PacketSceneRecord(this.location, replay.id));
        }
    }

    /**
     * Gruppenaufnahme starten oder beenden. Das Menue geht dabei zu - das
     * speichert vorher die eingetragenen Aufnahme-Spieler.
     */
    private void recordGroup()
    {
        if (!OpHelper.isPlayerOp() || !this.location.isScene())
        {
            return;
        }

        this.mc.displayGuiScreen(null);
        Dispatcher.sendToServer(new PacketSceneRecord(this.location, mchorse.blockbuster.recording.scene.GroupRecording.ALL));
    }

    public void pause()
    {
        if (!OpHelper.isPlayerOp())
        {
            return;
        }

        if (this.location.isScene())
        {
            Dispatcher.sendToServer(new PacketScenePause(this.location));
        }
    }
}