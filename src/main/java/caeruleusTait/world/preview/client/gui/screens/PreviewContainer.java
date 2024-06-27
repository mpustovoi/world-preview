package caeruleusTait.world.preview.client.gui.screens;

import caeruleusTait.world.preview.RenderSettings;
import caeruleusTait.world.preview.WorldPreview;
import caeruleusTait.world.preview.WorldPreviewConfig;
import caeruleusTait.world.preview.backend.WorkManager;
import caeruleusTait.world.preview.backend.color.ColorMap;
import caeruleusTait.world.preview.backend.color.PreviewData;
import caeruleusTait.world.preview.backend.color.PreviewMappingData;
import caeruleusTait.world.preview.client.gui.PreviewContainerDataProvider;
import caeruleusTait.world.preview.client.gui.PreviewDisplayDataProvider;
import caeruleusTait.world.preview.client.gui.widgets.OldStyleImageButton;
import caeruleusTait.world.preview.client.gui.widgets.PreviewDisplay;
import caeruleusTait.world.preview.client.gui.widgets.ToggleButton;
import caeruleusTait.world.preview.client.gui.widgets.lists.BaseObjectSelectionList;
import caeruleusTait.world.preview.client.gui.widgets.lists.BiomesList;
import caeruleusTait.world.preview.client.gui.widgets.lists.SeedsList;
import caeruleusTait.world.preview.client.gui.widgets.lists.StructuresList;
import caeruleusTait.world.preview.mixin.client.ScreenAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.shorts.Short2LongMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static caeruleusTait.world.preview.RenderSettings.RenderMode.BIOMES;
import static caeruleusTait.world.preview.RenderSettings.RenderMode.HEIGHTMAP;
import static caeruleusTait.world.preview.RenderSettings.RenderMode.INTERSECTIONS;
import static caeruleusTait.world.preview.RenderSettings.RenderMode.NOISE_CONTINENTALNESS;
import static caeruleusTait.world.preview.RenderSettings.RenderMode.NOISE_DEPTH;
import static caeruleusTait.world.preview.RenderSettings.RenderMode.NOISE_EROSION;
import static caeruleusTait.world.preview.RenderSettings.RenderMode.NOISE_HUMIDITY;
import static caeruleusTait.world.preview.RenderSettings.RenderMode.NOISE_PEAKS_AND_VALLEYS;
import static caeruleusTait.world.preview.RenderSettings.RenderMode.NOISE_TEMPERATURE;
import static caeruleusTait.world.preview.RenderSettings.RenderMode.NOISE_WEIRDNESS;
import static caeruleusTait.world.preview.WorldPreview.LOGGER;
import static caeruleusTait.world.preview.client.WorldPreviewComponents.*;

public class PreviewContainer implements AutoCloseable, PreviewDisplayDataProvider {

    public static final TagKey<Biome> C_CAVE = TagKey.create(Registries.BIOME, new ResourceLocation("c", "caves"));
    public static final TagKey<Biome> C_IS_CAVE = TagKey.create(Registries.BIOME, new ResourceLocation("c", "is_cave"));
    public static final TagKey<Biome> FORGE_CAVE = TagKey.create(Registries.BIOME, new ResourceLocation("forge", "caves"));
    public static final TagKey<Biome> FORGE_IS_CAVE = TagKey.create(Registries.BIOME, new ResourceLocation("forge", "is_cave"));
    public static final TagKey<Structure> DISPLAY_BY_DEFAULT = TagKey.create(Registries.STRUCTURE, new ResourceLocation("c", "display_on_map_by_default"));

    public static final ResourceLocation BUTTONS_TEXTURE = new ResourceLocation("world_preview:textures/gui/buttons.png");
    public static final int BUTTONS_TEX_WIDTH = 400;
    public static final int BUTTONS_TEX_HEIGHT = 60;

    public static final int LINE_HEIGHT = 20;
    public static final int LINE_VSPACE = 4;

    private final PreviewContainerDataProvider dataProvider;
    private final Minecraft minecraft;
    private final WorldPreview worldPreview;
    private final WorldPreviewConfig cfg;
    private final WorkManager workManager;
    private final RenderSettings renderSettings;
    private final PreviewMappingData previewMappingData;
    private PreviewData previewData;

    private List<ResourceLocation> levelStemKeys;
    private Registry<LevelStem> levelStemRegistry;

    private final EditBox seedEdit;
    private final Button randomSeedButton;
    private final Button saveSeed;
    private final Button settings;
    private final Button resetToZeroZero;
    private final ToggleButton toggleCaves;
    private final ToggleButton toggleShowStructures;
    private final ToggleButton toggleBiomes;
    private final ToggleButton toggleNoise;
    private final ToggleButton toggleHeightmap;
    private final ToggleButton toggleIntersections;
    private final ToggleButton toggleExpand;
    private final CycleButton<RenderSettings.RenderMode> noiseCycleButton;
    private final Button resetDefaultStructureVisibility;
    private final Button switchBiomes;
    private final Button switchStructures;
    private final Button switchSeeds;
    private final PreviewDisplay previewDisplay;
    private final BiomesList biomesList;
    private final StructuresList structuresList;
    private final SeedsList seedsList;
    private BiomesList.BiomeEntry[] allBiomes;
    private StructuresList.StructureEntry[] allStructures;
    private NativeImage[] allStructureIcons;
    private NativeImage playerIcon;
    private NativeImage spawnIcon;
    private List<SeedsList.SeedEntry> seedEntries;
    private ScreenRectangle lastScreenRectangle;

    private boolean inhibitUpdates = true;
    private boolean isUpdating = false;
    private boolean setupFailed = false;
    private final Executor reloadExecutor = Executors.newSingleThreadExecutor();
    private final Executor serverThreadPoolExecutor;
    private final AtomicInteger reloadRevision = new AtomicInteger(0);

    private final List<AbstractWidget> toRender = new ArrayList<>();

    public PreviewContainer(Screen screen, PreviewContainerDataProvider previewContainerDataProvider) {
        final Font font = ((ScreenAccessor) screen).getFont();
        dataProvider = previewContainerDataProvider;
        minecraft = Minecraft.getInstance();
        allBiomes = new BiomesList.BiomeEntry[0];
        worldPreview = WorldPreview.get();
        cfg = worldPreview.cfg();
        workManager = worldPreview.workManager();
        previewMappingData = worldPreview.biomeColorMap();
        renderSettings = worldPreview.renderSettings();
        serverThreadPoolExecutor = worldPreview.serverThreadPoolExecutor();

        seedEdit = new EditBox(font, 0, 0, 100, LINE_HEIGHT - 2, SEED_FIELD);
        seedEdit.setHint(SEED_FIELD);
        seedEdit.setValue(dataProvider.seed());
        seedEdit.setResponder(this::setSeed);
        seedEdit.setTooltip(Tooltip.create(SEED_LABEL));
        seedEdit.active = dataProvider.seedIsEditable();
        toRender.add(seedEdit);

        // seedLabel = new WGLabel(font, 0, 0, 100, LINE_HEIGHT, WGLabel.TextAlignment.LEFT, SEED_LABEL, 0xFFFFFF);
        // toRender.add(seedLabel);

        randomSeedButton = new OldStyleImageButton(
                0, 0, 20, 20, /* x, y, width, height */
                0, 20, 20, /* xTexStart, yTexStart, yDiffTex */
                BUTTONS_TEXTURE, BUTTONS_TEX_WIDTH, BUTTONS_TEX_HEIGHT, /* resourceLocation, textureWidth, textureHeight*/
                this::randomizeSeed
        );
        randomSeedButton.setTooltip(Tooltip.create(BTN_RANDOM));
        randomSeedButton.active = dataProvider.seedIsEditable();
        toRender.add(randomSeedButton);

        saveSeed = new OldStyleImageButton(
                0, 0, 20, 20, /* x, y, width, height */
                20, 20, 20, /* xTexStart, yTexStart, yDiffTex */
                BUTTONS_TEXTURE, BUTTONS_TEX_WIDTH, BUTTONS_TEX_HEIGHT, /* resourceLocation, textureWidth, textureHeight*/
                this::saveCurrentSeed
        );
        saveSeed.setTooltip(Tooltip.create(BTN_SAVE_SEED));
        saveSeed.active = false;
        toRender.add(saveSeed);

        settings = new OldStyleImageButton(
                0, 0, 20, 20, /* x, y, width, height */
                60, 20, 20, /* xTexStart, yTexStart, yDiffTex */
                BUTTONS_TEXTURE, BUTTONS_TEX_WIDTH, BUTTONS_TEX_HEIGHT, /* resourceLocation, textureWidth, textureHeight*/
                x -> {
                    workManager.cancel();
                    minecraft.setScreen(new SettingsScreen(screen, this));
                }
        );
        settings.setTooltip(Tooltip.create(BTN_SETTINGS));
        settings.active = false; // Do not allow clicking away until we loaded levelStemKeys
        toRender.add(settings);

        resetToZeroZero = new OldStyleImageButton(
                0, 0, 20, 20, /* x, y, width, height */
                120, 20, 20, /* xTexStart, yTexStart, yDiffTex */
                BUTTONS_TEXTURE, BUTTONS_TEX_WIDTH, BUTTONS_TEX_HEIGHT, /* resourceLocation, textureWidth, textureHeight*/
                x -> renderSettings.resetCenter()
        );
        resetToZeroZero.setTooltip(Tooltip.create(BTN_HOME));
        toRender.add(resetToZeroZero);

        resetDefaultStructureVisibility = Button
                .builder(BTN_RESET_STRUCTURES, x -> Arrays.stream(allStructures).forEach(StructuresList.StructureEntry::reset))
                .build();
        resetDefaultStructureVisibility.setTooltip(Tooltip.create(BTN_RESET_STRUCTURES_TOOLTIP));
        resetDefaultStructureVisibility.visible = false;
        toRender.add(resetDefaultStructureVisibility);

        switchBiomes = Button.builder(DisplayType.BIOMES.component(), x -> onTabButtonChange(x, DisplayType.BIOMES))
                .size(100, LINE_HEIGHT)
                .build();
        switchStructures = Button.builder(DisplayType.STRUCTURES.component(), x -> onTabButtonChange(x, DisplayType.STRUCTURES))
                .size(100, LINE_HEIGHT)
                .build();
        switchSeeds = Button.builder(DisplayType.SEEDS.component(), x -> onTabButtonChange(x, DisplayType.SEEDS))
                .size(100, LINE_HEIGHT)
                .build();

        toRender.add(switchBiomes);
        toRender.add(switchStructures);
        toRender.add(switchSeeds);

        biomesList = new BiomesList(this, minecraft, 200, 300, 4, 100, true);
        toRender.add(biomesList);

        structuresList = new StructuresList(minecraft, 200, 300, 4, 100);
        toRender.add(structuresList);

        seedsList = new SeedsList(minecraft, this);
        updateSeedListWidget();
        toRender.add(seedsList);

        previewDisplay = new PreviewDisplay(minecraft, this, TITLE);
        toRender.add(previewDisplay);

        toggleCaves = new ToggleButton(
                0, 0, 20, 20, /* x, y, width, height */
                80, 20, 20, 20, /* xTexStart, yTexStart, xDiffTex, yDiffTex */
                BUTTONS_TEXTURE, BUTTONS_TEX_WIDTH, BUTTONS_TEX_HEIGHT, /* resourceLocation, textureWidth, textureHeight*/
                x -> {
                    biomesList.setSelected(null);
                    previewDisplay.setSelectedBiomeId((short) -1);
                    previewDisplay.setHighlightCaves(((ToggleButton) x).selected);
                }
        );
        toggleCaves.setTooltip(Tooltip.create(BTN_CAVES));
        toRender.add(toggleCaves);

        toggleShowStructures = new ToggleButton(
                0, 0, 20, 20, /* x, y, width, height */
                140, 20, 20, 20, /* xTexStart, yTexStart, xDiffTex, yDiffTex */
                BUTTONS_TEXTURE, BUTTONS_TEX_WIDTH, BUTTONS_TEX_HEIGHT, /* resourceLocation, textureWidth, textureHeight*/
                x -> renderSettings.hideAllStructures = !((ToggleButton) x).selected
        );
        toggleShowStructures.selected = !renderSettings.hideAllStructures;
        toggleShowStructures.active = false; // Deactivate first in case sampleStructures is off
        toRender.add(toggleShowStructures);

        toggleBiomes = new ToggleButton(
                0, 0, 20, 20, /* x, y, width, height */
                360, 20, 20, 20, /* xTexStart, yTexStart, xDiffTex, yDiffTex */
                BUTTONS_TEXTURE, BUTTONS_TEX_WIDTH, BUTTONS_TEX_HEIGHT, /* resourceLocation, textureWidth, textureHeight*/
                x -> selectViewMode(BIOMES)
        );
        toggleBiomes.visible = false;
        toggleBiomes.active = true;
        toggleBiomes.setTooltip(Tooltip.create(BTN_TOGGLE_BIOMES));
        toRender.add(toggleBiomes);

        toggleNoise = new ToggleButton(
                0, 0, 20, 20, /* x, y, width, height */
                280, 20, 20, 20, /* xTexStart, yTexStart, xDiffTex, yDiffTex */
                BUTTONS_TEXTURE, BUTTONS_TEX_WIDTH, BUTTONS_TEX_HEIGHT, /* resourceLocation, textureWidth, textureHeight*/
                x -> selectViewMode(renderSettings.lastNoise)
        );
        toggleNoise.visible = false;
        toggleNoise.active = false;
        toggleNoise.setTooltip(Tooltip.create(BTN_TOGGLE_NOISE));
        toRender.add(toggleNoise);

        toggleHeightmap = new ToggleButton(
                0, 0, 20, 20, /* x, y, width, height */
                200, 20, 20, 20, /* xTexStart, yTexStart, xDiffTex, yDiffTex */
                BUTTONS_TEXTURE, BUTTONS_TEX_WIDTH, BUTTONS_TEX_HEIGHT, /* resourceLocation, textureWidth, textureHeight*/
                x -> selectViewMode(HEIGHTMAP)
        );
        toggleHeightmap.visible = false;
        toggleHeightmap.active = false;
        toRender.add(toggleHeightmap);

        toggleIntersections = new ToggleButton(
                0, 0, 20, 20, /* x, y, width, height */
                240, 20, 20, 20, /* xTexStart, yTexStart, xDiffTex, yDiffTex */
                BUTTONS_TEXTURE, BUTTONS_TEX_WIDTH, BUTTONS_TEX_HEIGHT, /* resourceLocation, textureWidth, textureHeight*/
                x -> selectViewMode(INTERSECTIONS)
        );
        toggleIntersections.active = false;
        toggleIntersections.visible = false;
        toRender.add(toggleIntersections);

        noiseCycleButton = CycleButton
                .builder(RenderSettings.RenderMode::toComponent)
                .withValues(List.of(NOISE_TEMPERATURE, NOISE_HUMIDITY, NOISE_DEPTH, NOISE_CONTINENTALNESS, NOISE_WEIRDNESS, NOISE_EROSION, NOISE_PEAKS_AND_VALLEYS))
                .withInitialValue(renderSettings.lastNoise)
                .create(0, 0, 200, 20, BTN_CYCLE_NOISE, (btn, val) -> selectViewMode(val));
        noiseCycleButton.active = false;
        noiseCycleButton.visible = false;
        toRender.add(noiseCycleButton);

        toggleExpand = new ToggleButton(
                0, 0, 20, 20, /* x, y, width, height */
                320, 20, 20, 20, /* xTexStart, yTexStart, xDiffTex, yDiffTex */
                BUTTONS_TEXTURE, BUTTONS_TEX_WIDTH, BUTTONS_TEX_HEIGHT, /* resourceLocation, textureWidth, textureHeight*/
                x -> {
                    final boolean expanded = ((ToggleButton) x).selected;
                    toggleBiomes.visible = expanded;
                    toggleNoise.visible = expanded;
                    toggleIntersections.visible = expanded;
                    toggleHeightmap.visible = expanded;
                    noiseCycleButton.visible = expanded;
                    doLayout(lastScreenRectangle);
                }
        );
        toggleExpand.setTooltip(Tooltip.create(BTN_TOGGLE_EXPAND));
        toRender.add(toggleExpand);

        biomesList.setBiomeChangeListener(x -> {
            previewDisplay.setSelectedBiomeId(x == null ? -1 : x.id());
            toggleCaves.selected = x == null && toggleCaves.selected;
            previewDisplay.setHighlightCaves(x == null && toggleCaves.selected);
        });
        dataProvider.registerSettingsChangeListener(this::updateSettings);

        onTabButtonChange(switchBiomes, DisplayType.BIOMES);
        selectViewMode(BIOMES);
    }


    public void patchColorData() {
        Map<ResourceLocation, PreviewMappingData.ColorEntry> configured = Arrays.stream(allBiomes)
                .filter(x -> x.dataSource() == PreviewData.DataSource.CONFIG)
                .collect(
                        Collectors.toMap(
                                x -> x.entry().key().location(),
                                x -> new PreviewMappingData.ColorEntry(PreviewData.DataSource.MISSING, x.color(), x.isCave(), x.name())
                        )
                );

        Map<ResourceLocation, PreviewMappingData.ColorEntry> defaults = Arrays.stream(allBiomes)
                .filter(x -> x.dataSource() == PreviewData.DataSource.RESOURCE)
                .collect(
                        Collectors.toMap(
                                x -> x.entry().key().location(),
                                x -> new PreviewMappingData.ColorEntry(PreviewData.DataSource.RESOURCE, x.color(), x.isCave(), x.name())
                        )
                );

        Map<ResourceLocation, PreviewMappingData.ColorEntry> missing = Arrays.stream(allBiomes)
                .filter(x -> x.dataSource() == PreviewData.DataSource.MISSING)
                .collect(
                        Collectors.toMap(
                                x -> x.entry().key().location(),
                                x -> new PreviewMappingData.ColorEntry(PreviewData.DataSource.CONFIG, x.color(), x.isCave(), x.name())
                        )
                );

        previewMappingData.update(missing);
        previewMappingData.update(defaults);
        previewMappingData.update(configured);
        updateSettings();
    }

    private void selectViewMode(RenderSettings.RenderMode mode) {
        toggleBiomes.selected = false;
        toggleHeightmap.selected = false;
        toggleIntersections.selected = false;
        toggleNoise.selected = false;
        noiseCycleButton.active = false;

        synchronized (renderSettings) {
            switch (mode) {
                case BIOMES -> toggleBiomes.selected = true;
                case HEIGHTMAP -> toggleHeightmap.selected = true;
                case INTERSECTIONS -> toggleIntersections.selected = true;
                case NOISE_TEMPERATURE, NOISE_HUMIDITY, NOISE_CONTINENTALNESS, NOISE_EROSION, NOISE_DEPTH,
                     NOISE_WEIRDNESS, NOISE_PEAKS_AND_VALLEYS -> {
                    renderSettings.lastNoise = mode;
                    toggleNoise.selected = true;
                    noiseCycleButton.active = true;
                }
            }
            renderSettings.mode = mode;
        }
    }

    private synchronized void updateSettings() {
        if (inhibitUpdates) {
            return;
        }
        inhibitUpdates = true;
        try {
            final int revision;
            synchronized (reloadRevision) {
                revision = reloadRevision.incrementAndGet();
            }
            isUpdating = true;
            CompletableFuture
                    .supplyAsync(() -> {
                        // Check if we are the latest update
                        if (reloadRevision.get() > revision) {
                            return null;
                        }
                        return dataProvider.previewWorldCreationContext();
                    }, reloadExecutor)
                    .thenAcceptAsync(x -> {
                        // Check if we are the latest update
                        if (reloadRevision.get() > revision) {
                            return;
                        }
                        updateSettings_real(x);
                        synchronized (reloadRevision) {
                            if (reloadRevision.get() <= revision) {
                                isUpdating = false;
                            }
                        }
                    }, minecraft)
                    .handle((r, e) -> {
                        if (e == null) {
                            setupFailed = false;
                        } else {
                            e.printStackTrace();
                            setupFailed = true;
                        }
                        return null;
                    });
        } finally {
            inhibitUpdates = false;
        }
    }

    private void updateSettings_real(@Nullable WorldCreationContext wcContext) {
        saveSeed.active = !dataProvider.seed().isEmpty() && !cfg.savedSeeds.contains(dataProvider.seed());
        updateSeedListWidget();
        seedEdit.setValue(dataProvider.seed());
        if (!seedEdit.isFocused()) {
            seedEdit.moveCursorToStart(false);
        }

        // Range validation
        if (cfg.heightmapMinY == cfg.heightmapMaxY) {
            cfg.heightmapMaxY++;
        } else if (cfg.heightmapMaxY < cfg.heightmapMinY) {
            int tmp = cfg.heightmapMaxY;
            cfg.heightmapMaxY = cfg.heightmapMinY;
            cfg.heightmapMinY = tmp;
        }

        // Basic world loading / generation setup
        WorldDataConfiguration worldDataConfiguration = dataProvider.worldDataConfiguration(wcContext);
        Registry<Biome> biomeRegistry = dataProvider.registryAccess(wcContext).registryOrThrow(Registries.BIOME);
        Registry<Structure> strucutreRegistry = dataProvider.registryAccess(wcContext).registryOrThrow(Registries.STRUCTURE);
        levelStemRegistry = dataProvider.levelStemRegistry(wcContext);
        levelStemKeys = levelStemRegistry.keySet().stream().sorted(Comparator.comparing(Object::toString)).toList();

        // Now that the level stem keys are loaded, allow the user to go into properties!
        settings.active = true;

        if (renderSettings.dimension == null || !levelStemRegistry.containsKey(renderSettings.dimension)) {
            if (levelStemRegistry.containsKey(LevelStem.OVERWORLD)) {
                renderSettings.dimension = LevelStem.OVERWORLD.location();
            } else {
                renderSettings.dimension = levelStemRegistry.keySet().iterator().next();
            }
        }
        LevelStem levelStem = levelStemRegistry.get(renderSettings.dimension);

        Set<ResourceLocation> caveBiomes = new HashSet<>();
        for (TagKey<Biome> tagKey : List.of(C_CAVE, C_IS_CAVE, FORGE_CAVE, FORGE_IS_CAVE)) {
            caveBiomes.addAll(
                    StreamSupport.stream(biomeRegistry.getTagOrEmpty(tagKey).spliterator(), false)
                            .map(x -> x.unwrapKey().orElseThrow().location())
                            .toList()
            );
        }

        previewData = previewMappingData.generateMapData(
                biomeRegistry.keySet(),
                caveBiomes,
                strucutreRegistry.keySet(),
                StreamSupport.stream(strucutreRegistry.getTagOrEmpty(DISPLAY_BY_DEFAULT).spliterator(), false)
                        .map(x -> x.unwrapKey().orElseThrow().location())
                        .collect(Collectors.toSet())
        );

        // Check whether we have a valid colormap stored
        ColorMap colorMap = previewData.colorMaps().get(cfg.colorMap);
        if (colorMap == null) {
            cfg.colorMap = "world_preview:inferno";
        }

        // WorkManager update
        LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess = dataProvider.layeredRegistryAccess(wcContext);

        workManager.cancel();
        Runnable changeWorldGenState = () -> {
            workManager.changeWorldGenState(
                    levelStem,
                    layeredRegistryAccess,
                    previewData,
                    dataProvider.worldOptions(wcContext),
                    worldDataConfiguration,
                    dataProvider,
                    minecraft.getProxy(),
                    dataProvider.tempDataPackDir(),
                    dataProvider.minecraftServer()
            );
        };

        // Some forge mods require running the server setup in a specific thread pool to switch
        // to the server specific logic (`EffectiveSide.get().isClient()`)
        if (serverThreadPoolExecutor != null) {
            try {
                CompletableFuture.runAsync(changeWorldGenState, serverThreadPoolExecutor).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        } else {
            changeWorldGenState.run();
        }

        // Do NOT run this in the lambda because this call might change screens
        workManager.postChangeWorldGenState();

        // Biomes
        List<String> missing = Arrays.stream(previewData.biomeId2BiomeData())
                .filter(x -> x.dataSource() == PreviewData.DataSource.MISSING)
                .map(PreviewData.BiomeData::tag)
                .map(ResourceLocation::toString)
                .toList();
        worldPreview.writeMissingColors(missing);

        allBiomes = biomeRegistry.holders()
                .map(x -> {
                    final short id = previewData.biome2Id().getShort(x.key().location().toString());
                    final PreviewData.BiomeData biomeData = previewData.biomeId2BiomeData()[id];
                    final int color = biomeData.color();
                    final int initialColor = biomeData.resourceOnlyColor();
                    final boolean isCave = biomeData.isCave();
                    final boolean initialIsCave = biomeData.resourceOnlyIsCave();
                    final String explicitName = biomeData.name();
                    final PreviewData.DataSource dataSource = biomeData.dataSource();
                    return biomesList.createEntry(x, id, color, initialColor, isCave, initialIsCave, explicitName, dataSource);
                })
                .sorted(Comparator.comparing(BiomesList.BiomeEntry::id))
                .toArray(BiomesList.BiomeEntry[]::new);

        biomesList.replaceEntries(new ArrayList<>());
        biomesList.setSelected(null);

        // Structures
        missing = Arrays.stream(previewData.structId2StructData())
                .filter(x -> x.dataSource() == PreviewData.DataSource.MISSING)
                .map(PreviewData.StructureData::tag)
                .map(ResourceLocation::toString)
                .toList();
        worldPreview.writeMissingStructures(missing);

        //  - Icons
        freeStructureIcons();
        ResourceManager builtinResourceManager = minecraft.getResourceManager();
        Map<ResourceLocation, NativeImage> icons = new HashMap<>();
        allStructureIcons = new NativeImage[previewData.structId2StructData().length];
        for (int i = 0; i < previewData.structId2StructData().length; ++i) {
            PreviewData.StructureData data = previewData.structId2StructData()[i];
            allStructureIcons[i] = icons.computeIfAbsent(data.icon(), x -> {
                if (x == null) {
                    x = new ResourceLocation("world_preview:textures/structure/unknown.png");
                }
                Optional<Resource> resource = builtinResourceManager.getResource(x);
                if (resource.isEmpty()) {
                    resource = workManager.sampleResourceManager().getResource(x);
                }
                if (resource.isEmpty()) {
                    LOGGER.error("Failed to load structure icon: '{}'", x);
                    resource = builtinResourceManager.getResource(new ResourceLocation("world_preview:textures/structure/unknown.png"));
                }
                if (resource.isEmpty()) {
                    LOGGER.error("FATAL ERROR LOADING: '{}' -- unable to load fallback!", x);
                    return new NativeImage(16, 16, true);
                }
                try {
                    try(InputStream in = resource.get().open()) {
                        return NativeImage.read(in);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return new NativeImage(16, 16, true);
                }
            });
        }

        //  - Player and spawn icon
        final Optional<Resource> playerResource;
        final Optional<Resource> spawnResource;
        playerResource = builtinResourceManager.getResource(new ResourceLocation("world_preview:textures/etc/player.png"));
        spawnResource = builtinResourceManager.getResource(new ResourceLocation("world_preview:textures/etc/bed.png"));
        try {
            try(InputStream inPlayer = playerResource.orElseThrow().open(); InputStream inSpawn = spawnResource.orElseThrow().open()) {
                playerIcon = NativeImage.read(inPlayer);
                spawnIcon = NativeImage.read(inSpawn);
            }
        } catch (IOException e) {
            playerIcon = new NativeImage(16, 16, true);
            spawnIcon = new NativeImage(16, 16, true);
            e.printStackTrace();
        }

        //  - List entries
        Registry<Item> itemRegistry = layeredRegistryAccess.compositeAccess().registryOrThrow(Registries.ITEM);
        allStructures = strucutreRegistry.holders()
                .map(x -> {
                    final short id = previewData.struct2Id().getShort(x.key().location().toString());
                    final PreviewData.StructureData structureData = previewData.structId2StructData()[id];
                    return structuresList.createEntry(
                            id,
                            x.key().location(),
                            allStructureIcons[id],
                            structureData.item() == null ? null : itemRegistry.get(structureData.item()),
                            structureData.name(),
                            structureData.showByDefault(),
                            structureData.showByDefault()
                    );
                })
                .sorted(Comparator.comparing(StructuresList.StructureEntry::id))
                .toArray(StructuresList.StructureEntry[]::new);

        structuresList.replaceEntries(new ArrayList<>());

        // Finalize the GUI
        renderSettings.resetCenter();

        if (cfg.sampleStructures) {
            toggleShowStructures.active = true;
            toggleShowStructures.setTooltip(Tooltip.create(BTN_TOGGLE_STRUCTURES));
        } else {
            toggleShowStructures.active = false;
            toggleShowStructures.setTooltip(Tooltip.create(BTN_TOGGLE_STRUCTURES_DISABLED));
        }

        if (cfg.sampleHeightmap) {
            toggleHeightmap.active = true;
            toggleHeightmap.setTooltip(Tooltip.create(BTN_TOGGLE_HEIGHTMAP));
        } else {
            toggleHeightmap.active = false;
            toggleHeightmap.setTooltip(Tooltip.create(BTN_TOGGLE_HEIGHTMAP_DISABLED));
            renderSettings.mode = renderSettings.mode == HEIGHTMAP ? BIOMES : renderSettings.mode;
        }

        if (cfg.sampleIntersections) {
            toggleIntersections.active = true;
            toggleIntersections.setTooltip(Tooltip.create(BTN_TOGGLE_INTERSECT));
        } else {
            toggleIntersections.active = false;
            toggleIntersections.setTooltip(Tooltip.create(BTN_TOGGLE_INTERSECT_DISABLED));
            renderSettings.mode = renderSettings.mode == INTERSECTIONS ? BIOMES : renderSettings.mode;
        }

        if (cfg.storeNoiseSamples) {
            toggleNoise.active = true;
            toggleNoise.setTooltip(Tooltip.create(BTN_TOGGLE_NOISE));
        } else {
            toggleNoise.active = false;
            toggleNoise.setTooltip(Tooltip.create(BTN_TOGGLE_NOISE_DISABLED));
            renderSettings.mode = renderSettings.mode.isNoise() ? BIOMES : renderSettings.mode;
        }

        selectViewMode(renderSettings.mode);

        previewDisplay.reloadData();
        previewDisplay.setSelectedBiomeId((short) -1);
        previewDisplay.setHighlightCaves(false);
        toggleCaves.selected = false;
    }

    @Override
    public void onVisibleBiomesChanged(Short2LongMap visibleBiomes) {
        List<BiomesList.BiomeEntry> res = visibleBiomes.short2LongEntrySet()
                .stream()
                .sorted(Comparator.comparing(Short2LongMap.Entry::getLongValue).reversed())
                .map(Short2LongMap.Entry::getShortKey)
                .map(x -> allBiomes[x])
                .toList();

        biomesList.replaceEntries(res);
    }

    @Override
    public void onVisibleStructuresChanged(Short2LongMap visibleStructures) {
        List<StructuresList.StructureEntry> res = visibleStructures.short2LongEntrySet()
                .stream()
                .sorted(Comparator.comparing(Short2LongMap.Entry::getLongValue))
                .map(Short2LongMap.Entry::getShortKey)
                .map(x -> allStructures[x])
                .toList();

        structuresList.replaceEntries(res);
    }

    private void randomizeSeed(Button btn) {
        UUID uuid = UUID.randomUUID();
        ByteBuffer bb = ByteBuffer.allocate(Long.BYTES * 2);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());

        String uuidSeed = Base64.getEncoder().encodeToString(bb.array()).substring(0, 16);
        setSeed(uuidSeed);
        // setSeed(String.valueOf(WorldOptions.randomSeed()));
    }

    private void saveCurrentSeed(Button btn) {
        cfg.savedSeeds.add(dataProvider.seed());
        saveSeed.active = false;
        updateSeedListWidget();
    }

    public void deleteSeed(String seed) {
        cfg.savedSeeds.remove(seed);
        updateSeedListWidget();
    }

    public void setSeed(String seed) {
        if (Objects.equals(dataProvider.seed(), seed) || !dataProvider.seedIsEditable()) {
            return;
        }

        boolean initialInhibitUpdates = inhibitUpdates;
        inhibitUpdates = true;
        try {
            dataProvider.updateSeed(seed);
        } finally {
            inhibitUpdates = initialInhibitUpdates;
        }
        updateSettings();
    }

    private void updateSeedListWidget() {
        seedEntries = cfg.savedSeeds.stream().map(seedsList::createEntry).toList();
        seedsList.replaceEntries(seedEntries);
        int idx = cfg.savedSeeds.indexOf(dataProvider.seed());
        if (idx >= 0) {
            seedsList.setSelected(seedEntries.get(idx));
        }
    }

    public void resetTabs() {
        onTabButtonChange(switchBiomes, DisplayType.BIOMES);
    }

    // TODO: Check if this can be removed in a future MC version
    private void moveList(BaseObjectSelectionList<?> theList) {
        if ((theList.active && theList.getX() > 0) || (!theList.active && theList.getX() < 0)) {
            return;
        }
        final int newX = theList.getX() + (theList.active ? 4096 : -4096);
        theList.setPosition(newX, theList.getY());
    }

    private void onTabButtonChange(Button btn, DisplayType type) {
        biomesList.visible = false;
        biomesList.active = false;
        structuresList.visible = false;
        structuresList.active = false;
        seedsList.visible = false;
        seedsList.active = false;

        switchBiomes.active = true;
        switchStructures.active = true;
        switchSeeds.active = true;

        resetDefaultStructureVisibility.visible = false;

        if (cfg.sampleStructures) {
            switchStructures.setTooltip(null);
        } else {
            switchStructures.setTooltip(Tooltip.create(BTN_SWITCH_STRUCT_DISABLED));
            switchStructures.active = false;
        }

        btn.active = false;
        switch (type) {
            case BIOMES -> {
                biomesList.visible = true;
                biomesList.active = true;
            }
            case STRUCTURES -> {
                resetDefaultStructureVisibility.visible = true;
                structuresList.visible = true;
                structuresList.active = true;
            }
            case SEEDS -> {
                seedsList.visible = true;
                seedsList.active = true;
            }
        }

        moveList(biomesList);
        moveList(structuresList);
        moveList(seedsList);
    }

    /**
     * Start generating the biome data
     */
    public synchronized void start() {
        LOGGER.info("Start generating biome data...");
        if (dataProvider.seed().isEmpty()) {
            randomizeSeed(null);
        }
        inhibitUpdates = false;
        updateSettings();
    }

    /**
     * Stop processing
     */
    public synchronized void stop() {
        LOGGER.info("Stop generating biome data...");
        inhibitUpdates = true;
        workManager.cancel();
    }

    public void doLayout(ScreenRectangle screenRectangle) {
        if (screenRectangle == null) {
            screenRectangle = minecraft.screen.getRectangle();
        }
        lastScreenRectangle = screenRectangle;

        int leftWidth = Math.max(130, Math.min(180, screenRectangle.width() / 3));
        int left = screenRectangle.left() + 3;
        int previewLeft = left + leftWidth + 3;
        int top = screenRectangle.top() + 2;
        int bottom = screenRectangle.bottom() - 32;

        // Preview
        final int expand = toggleExpand.selected ? 22 + 2 : 0;

        previewDisplay.setPosition(previewLeft, top + expand + 1);
        previewDisplay.setSize(screenRectangle.right() - previewDisplay.getX() - 4, screenRectangle.bottom() - previewDisplay.getY() - 14);

        // BOTTOM

        seedEdit.setWidth(leftWidth - 1 - 22 * 2);
        seedEdit.setX(left);
        seedEdit.setY(bottom + 1);

        randomSeedButton.setX((left + leftWidth) - 20);
        randomSeedButton.setY(bottom);

        saveSeed.setX((left + leftWidth) - 22 - 20);
        saveSeed.setY(bottom);

        // TOP
        int cycleWith = leftWidth - 22 * 4;

        int btnStart = left + cycleWith + 2;
        settings.setPosition(left, top);
        int i = 0;
        toggleShowStructures.setPosition(btnStart + 22 * i++, top);
        toggleCaves.setPosition(btnStart + 22 * i++, top);
        resetToZeroZero.setPosition(btnStart + 22 * i++, top);
        toggleExpand.setPosition(btnStart + 22 * i++, top);

        // TOP - hidden buttons
        i = 0;
        toggleBiomes.setPosition(previewLeft + 22 * i++, top);
        toggleIntersections.setPosition(previewLeft + 22 * i++, top);
        toggleHeightmap.setPosition(previewLeft + 22 * i++, top);
        toggleNoise.setPosition(previewLeft + 22 * i++, top);
        noiseCycleButton.setPosition(previewLeft + 22 * i++, top);

        //  - new row
        top += LINE_HEIGHT + LINE_VSPACE;
        int switchBiomesWidth = 45;
        int switchSeedsWidth = 45;
        int switchStructuresWidth = leftWidth - switchBiomesWidth - switchSeedsWidth - 4;
        switchBiomes.setPosition(left, top);
        switchStructures.setPosition(left + switchBiomesWidth + 2, top);
        switchSeeds.setPosition(left + switchBiomesWidth + switchStructuresWidth + 4, top);

        switchBiomes.setWidth(switchBiomesWidth);
        switchStructures.setWidth(switchStructuresWidth);
        switchSeeds.setWidth(switchSeedsWidth);

        //  - new row
        top += LINE_HEIGHT + LINE_VSPACE;

        biomesList.setPosition(left, top);
        biomesList.setSize(leftWidth, bottom - top - LINE_VSPACE);
        biomesList.setRenderBackground(true);

        seedsList.setPosition(left, top);
        seedsList.setSize(leftWidth, bottom - top - LINE_VSPACE);
        seedsList.setRenderBackground(true);

        // BOTTOM
        //  - new row
        bottom -= LINE_HEIGHT + LINE_VSPACE;

        resetDefaultStructureVisibility.setPosition(left, bottom);
        resetDefaultStructureVisibility.setWidth(leftWidth);

        structuresList.setPosition(left, top);
        structuresList.setSize(leftWidth, bottom - top - LINE_VSPACE);
        structuresList.setRenderBackground(true);

        moveList(biomesList);
        moveList(structuresList);
        moveList(seedsList);
    }

    @Override
    public void close() {
        workManager.cancel();
        previewDisplay.close();
        freeStructureIcons();
    }

    private void freeStructureIcons() {
        if (allStructureIcons == null) {
            return;
        }
        Arrays.stream(allStructureIcons).filter(Objects::nonNull).forEach(NativeImage::close);
    }

    public List<BiomesList.BiomeEntry> allBiomes() {
        return Arrays.stream(allBiomes).sorted(Comparator.comparing(BiomesList.BiomeEntry::name)).toList();
    }

    public List<ResourceLocation> levelStemKeys() {
        return levelStemKeys;
    }

    public Registry<LevelStem> levelStemRegistry() {
        return levelStemRegistry;
    }

    @Override
    public BiomesList.BiomeEntry biome4Id(int id) {
        return allBiomes[id];
    }

    @Override
    public StructuresList.StructureEntry structure4Id(int id) {
        return allStructures[id];
    }

    @Override
    public NativeImage[] structureIcons() {
        return allStructureIcons;
    }

    @Override
    public NativeImage playerIcon() {
        return playerIcon;
    }

    @Override
    public NativeImage spawnIcon() {
        return spawnIcon;
    }

    @Override
    public ItemStack[] structureItems() {
        return Arrays.stream(allStructures).map(StructuresList.StructureEntry::itemStack).toArray(ItemStack[]::new);
    }

    @Override
    public void onBiomeVisuallySelected(BiomesList.BiomeEntry entry) {
        biomesList.setSelected(entry, true);
        toggleCaves.selected = false;
        previewDisplay.setHighlightCaves(false);
    }

    @Override
    public PreviewData previewData() {
        return previewData;
    }

    public WorkManager workManager() {
        return workManager;
    }

    @Override
    public PreviewDisplayDataProvider.StructureRenderInfo[] renderStructureMap() {
        return allStructures;
    }

    @Override
    public int[] heightColorMap() {
        ColorMap colorMap = previewData.colorMaps().get(cfg.colorMap);
        if (colorMap == null) {
            int[] black = new int[workManager.yMax() - workManager.yMin()];
            Arrays.fill(black, 0xFF000000);
            return black;
        }
        return colorMap.bake(workManager.yMin(), workManager.yMax(), cfg.heightmapMinY, cfg.heightmapMaxY);
    }

    @Override
    public int[] noiseColorMap() {
        ColorMap colorMap = previewData.colorMaps().get(cfg.colorMap);
        if (colorMap == null) {
            int[] black = new int[256];
            Arrays.fill(black, 0xFF000000);
            return black;
        }
        return colorMap.bake(1024);
    }

    @Override
    public int yMin() {
        return workManager.yMin();
    }

    @Override
    public int yMax() {
        return workManager.yMax();
    }

    @Override
    public boolean isUpdating() {
        return isUpdating;
    }

    @Override
    public boolean setupFailed() {
        return setupFailed;
    }

    @Override
    public @NotNull PlayerData getPlayerData(UUID playerId) {
        if (workManager == null || workManager.sampleUtils() == null) {
            return new PlayerData(null, null);
        }
        ServerPlayer player = workManager.sampleUtils().getPlayers(playerId);
        if (player == null) {
            return new PlayerData(null, null);
        }
        ResourceKey<Level> playerDimension = player.level().dimension();
        ResourceKey<Level> respawnDimension = player.getRespawnDimension();
        ResourceKey<Level> currentDimension = workManager.sampleUtils().dimension();

        return new PlayerData(
                currentDimension.equals(playerDimension) ? player.blockPosition() : null,
                currentDimension.equals(respawnDimension) ? player.getRespawnPosition() : null
        );
    }

    public ToggleButton toggleCaves() {
        return toggleCaves;
    }

    public ToggleButton toggleShowStructures() {
        return toggleShowStructures;
    }

    public ToggleButton toggleHeightmap() {
        return toggleHeightmap;
    }

    public ToggleButton toggleIntersections() {
        return toggleIntersections;
    }

    public PreviewContainerDataProvider dataProvider() {
        return dataProvider;
    }

    public enum DisplayType {
        BIOMES,
        STRUCTURES,
        SEEDS,
        ;

        public Component component() {
            return toComponent(this);
        }

        public static Component toComponent(PreviewContainer.DisplayType x) {
            return Component.translatable("world_preview.preview.btn-cycle." + x.name());
        }
    }

    public List<AbstractWidget> widgets() {
        return toRender;
    }
}
