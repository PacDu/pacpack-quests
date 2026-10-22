package fr.pacdu.pacpackquests.client.screen;

import fr.pacdu.pacpackquests.client.PacPackQuestsClient;
import fr.pacdu.pacpackquests.client.gui.ScrollbarWidget;
import fr.pacdu.pacpackquests.client.util.TranslationUtils;
import fr.pacdu.pacpackquests.network.RequestRegistryPayload;
import fr.pacdu.pacpackquests.util.IconUtils;
import fr.pacdu.pacpackquests.util.ItemUtils;
import fr.pacdu.pacpackquests.util.RegistryType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Screen for selecting elements across various Minecraft registries (items, blocks, entities, biomes, etc.).
 * For items, it replicates JEI's creative tab ordering, multi-line tooltips (mod name, components, IDs),
 * and advanced search (@mod, #tags, names, and tooltip contents).
 */
public class SelectionScreen extends Screen {

    /**
     * Cached entry data for efficient search and rendering.
     */
    private record SelectionEntry(String id, ItemStack stack, String displayName, String namespace, String modName,
                                  String path, String searchableTooltip, boolean isTag) {}

    private final Screen parent;
    private final RegistryType contextType;
    private final Consumer<String> onSelected;

    private TextFieldWidget searchBox;
    private final List<SelectionEntry> allEntries = new ArrayList<>();
    private final List<SelectionEntry> filteredEntries = new ArrayList<>();

    private ScrollbarWidget gridScrollbar = new ScrollbarWidget(0, 0, 6, 0, 1.0, false);

    private int columns;
    private int rows;
    private int maxVisible;
    private boolean withTags = true;
    private boolean isReward = false;

    public SelectionScreen(Screen parent, RegistryType contextType, Consumer<String> onSelected) {
        super(Text.translatable("gui.pacpack-quests.select_element"));
        this.parent = parent;
        this.contextType = contextType;
        this.onSelected = onSelected;
    }

    public SelectionScreen(Screen parent, RegistryType contextType, Consumer<String> onSelected, boolean withTags) {
        this(parent, contextType, onSelected);
        this.withTags = withTags;
    }

    public SelectionScreen(Screen parent, RegistryType contextType, Consumer<String> onSelected, boolean withTags, boolean isReward) {
        this(parent, contextType, onSelected, withTags);
        this.isReward = isReward;
    }

    @Override
    protected void init() {
        super.init();

        this.columns = Math.clamp(this.width / 18 - 5, 9, 64);
        this.rows = this.height / 18 - 5;
        this.maxVisible = this.columns * this.rows;

        // 1. Search Box
        int searchWidth = 160;
        this.searchBox = new TextFieldWidget(this.textRenderer, (this.width - searchWidth) / 2, 20, searchWidth, 20, Text.empty());
        this.searchBox.setPlaceholder(Text.translatable("gui.pacpack-quests.search").formatted(Formatting.DARK_GRAY));
        this.searchBox.setChangedListener(this::updateSearch);
        this.addDrawableChild(this.searchBox);
        this.setFocused(this.searchBox);

        // 2. Cancel Button
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.pacpack-quests.cancel"), button -> this.client.setScreen(this.parent))
                .dimensions((this.width - 100) / 2, this.height - 30, 100, 20).build());

        // 3. Populate initial data based on context
        this.populateEntries();
        this.updateSearch("");
    }

    /**
     * Populates all available entries based on current registry context.
     */
    private void populateEntries() {
        allEntries.clear();
        var client = MinecraftClient.getInstance();
        var world = client.world;
        RegistryWrapper.WrapperLookup registries = world != null ? world.getRegistryManager() : DynamicRegistryManager.EMPTY;

        switch (contextType) {
            case ITEM -> {
                // 1. Ensure creative search tab stacks are initialized
                Collection<ItemStack> searchStacks = ItemGroups.getSearchGroup().getSearchTabStacks();
                if (searchStacks.isEmpty() && world != null) {
                    var enabledFeatures = world.getEnabledFeatures();
                    boolean operator = client.player != null && client.player.isCreativeLevelTwoOp();
                    ItemGroups.updateDisplayContext(enabledFeatures, operator, registries);
                    searchStacks = ItemGroups.getSearchGroup().getSearchTabStacks();
                }

                if (searchStacks.isEmpty()) {
                    searchStacks = Registries.ITEM.stream().map(Item::getDefaultStack).toList();
                }

                // 2. Populate stacks in creative tab order
                for (ItemStack stack : searchStacks) {
                    if (stack.isEmpty()) continue;

                    String commandString = ItemUtils.toCommandString(stack, registries);
                    Identifier baseId = Registries.ITEM.getId(stack.getItem());
                    String namespace = baseId.getNamespace();
                    String modName = TranslationUtils.getModName(namespace);
                    String displayName = stack.getName().getString();

                    // Precompute searchable tooltip text (for enchantments, potion effects, lore)
                    StringBuilder tooltipText = new StringBuilder();
                    try {
                        List<Text> tooltipLines = Screen.getTooltipFromItem(client, stack);
                        for (Text line : tooltipLines) {
                            tooltipText.append(line.getString().toLowerCase(Locale.ROOT)).append(" ");
                        }
                    } catch (Exception ignored) {}

                    allEntries.add(new SelectionEntry(commandString, stack.copy(), displayName, namespace, modName, baseId.getPath(), tooltipText.toString(), false));
                }

                // 3. Add tags last if enabled
                if (this.withTags) {
                    Registries.ITEM.streamTags().forEach(tag -> tag.getTagKey().ifPresent(key -> {
                        Identifier tagId = key.id();
                        String tagString = "#" + tagId;
                        addEntry(tagString, tagId, true);
                    }));
                }
            }
            case BLOCK -> {
                Registries.BLOCK.getIds().forEach(id -> addEntry(id.toString(), id, false));
                if (this.withTags) {
                    Registries.BLOCK.streamTags().forEach(tag -> tag.getTagKey().ifPresent(key -> addEntry("#" + key.id().toString(), key.id(), true)));
                }
                this.allEntries.sort(this::compareEntries);
            }
            case MOB -> {
                java.util.Set<Identifier> validMobs = new java.util.HashSet<>();

                // Filter the raw entity IDs to keep only real mobs
                Registries.ENTITY_TYPE.getIds().forEach(id -> {
                    var type = Registries.ENTITY_TYPE.get(id);
                    var group = type.getSpawnGroup();

                    // Check if a spawn egg exists for this entity (covers MISC mobs like Wither, Villagers, Golems)
                    Identifier eggId = Identifier.of(id.getNamespace(), id.getPath() + "_spawn_egg");
                    boolean hasSpawnEgg = Registries.ITEM.containsId(eggId);

                    // A valid mob is either in a natural spawn group, has a spawn egg, or is the player explicitly
                    if (group != net.minecraft.entity.SpawnGroup.MISC || hasSpawnEgg || id.getPath().equals("player")) {
                        validMobs.add(id);
                        addEntry(id.toString(), id, false);
                    }
                });

                // Add tag only if at least ONE of its entities is a valid mob
                Registries.ENTITY_TYPE.streamTags().forEach(tagList -> {
                    boolean hasValidMob = tagList.stream().anyMatch(entry -> validMobs.contains(Registries.ENTITY_TYPE.getId(entry.value())));
                    if (hasValidMob) {
                        tagList.getTagKey().ifPresent(key -> addEntry("#" + key.id().toString(), key.id(), true));
                    }
                });
                this.allEntries.sort(this::compareEntries);
            }
            case BIOME -> {
                if (world != null) {
                    var registry = world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
                    registry.getIds().forEach(id -> addEntry(id.toString(), id, false));
                    registry.streamTags().forEach(tag -> tag.getTagKey().ifPresent(key -> addEntry("#" + key.id().toString(), key.id(), true)));
                    this.allEntries.sort(this::compareEntries);
                }
            }
            case STRUCTURE -> {
                var server = MinecraftClient.getInstance().getServer();
                if (server != null) {
                    var registry = server.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
                    registry.getIds().forEach(id -> addEntry(id.toString(), id, false));
                    registry.streamTags().forEach(tag -> tag.getTagKey().ifPresent(key -> addEntry("#" + key.id().toString(), key.id(), true)));
                    this.allEntries.sort(this::compareEntries);
                } else {
                    refreshEntries(PacPackQuestsClient.SERVER_STRUCTURES);
                    ClientPlayNetworking.send(new RequestRegistryPayload(RegistryType.STRUCTURE));
                }
            }
            case DIMENSION -> {
                var handler = MinecraftClient.getInstance().getNetworkHandler();
                if (handler != null) {
                    handler.getWorldKeys().forEach(key -> addEntry(key.getValue().toString(), key.getValue(), false));
                    this.allEntries.sort(this::compareEntries);
                }
            }
            case LOOTABLE -> {
                addEntry("#pacpackquests:loot_chest", Identifier.of("pacpackquests", "loot_chest"), true);
                var server = MinecraftClient.getInstance().getServer();
                if (server != null) {
                    var opt = server.getReloadableRegistries().createRegistryLookup().getOptional(RegistryKeys.LOOT_TABLE);
                    opt.ifPresent(lootTableImpl -> lootTableImpl.streamKeys().forEach(key -> {
                        Identifier id = key.getValue();
                        if (id.getPath().startsWith("chests/")) {
                            addEntry(id.toString(), id, false);
                        }
                    }));
                    this.allEntries.sort(this::compareEntries);
                } else {
                    refreshEntries(PacPackQuestsClient.SERVER_LOOT_TABLES);
                    ClientPlayNetworking.send(new RequestRegistryPayload(RegistryType.LOOTABLE));
                }
            }
            case COMMAND -> {}
        }
    }

    private void addEntry(String idStr, Identifier id, boolean isTag) {
        String namespace = id != null ? id.getNamespace() : "minecraft";
        String path = id != null ? id.getPath() : idStr;
        String modName = TranslationUtils.getModName(namespace);
        ItemStack stack = IconUtils.getRepresentativeItem(idStr, this.contextType).getDefaultStack();
        String displayName = TranslationUtils.getTranslatedName(idStr, this.contextType, this.isReward);
        allEntries.add(new SelectionEntry(idStr, stack, displayName, namespace, modName, path, "", isTag));
    }

    public RegistryType getRegistryType() {
        return this.contextType;
    }

    public void refreshEntries(List<String> newEntries) {
        this.allEntries.clear();
        if (this.contextType == RegistryType.LOOTABLE) {
            addEntry("#pacpackquests:loot_chest", Identifier.of("pacpackquests", "loot_chest"), true);
        }
        for (String id : newEntries) {
            Identifier idObj = Identifier.tryParse(id.startsWith("#") ? id.substring(1) : id);
            addEntry(id, idObj, id.startsWith("#"));
        }
        this.allEntries.sort(this::compareEntries);
        this.updateSearch(this.searchBox != null ? this.searchBox.getText() : "");
    }

    /**
     * Filters entries according to user input.
     * Supports:
     * - "@mod" prefix: searches mod namespace or mod title
     * - "#tag" prefix: searches tags
     * - Normal search: matches item name, translation, path, or any text in the tooltip (enchantment name, potion effect)
     */
    private void updateSearch(String query) {
        filteredEntries.clear();
        String trimmed = query.trim();
        String lowerQuery = trimmed.toLowerCase(Locale.ROOT);

        for (SelectionEntry entry : allEntries) {
            if (lowerQuery.isEmpty()) {
                filteredEntries.add(entry);
            } else if (lowerQuery.startsWith("@")) {
                String modQuery = lowerQuery.substring(1).trim();
                if (entry.namespace.toLowerCase(Locale.ROOT).contains(modQuery)
                        || entry.modName.toLowerCase(Locale.ROOT).contains(modQuery)) {
                    filteredEntries.add(entry);
                }
            } else if (lowerQuery.startsWith("#")) {
                String tagQuery = lowerQuery.substring(1).trim();
                if (entry.isTag && (entry.path.toLowerCase(Locale.ROOT).contains(tagQuery)
                        || entry.id.toLowerCase(Locale.ROOT).contains(tagQuery)
                        || entry.displayName.toLowerCase(Locale.ROOT).contains(tagQuery))) {
                    filteredEntries.add(entry);
                }
            } else {
                if (entry.displayName.toLowerCase(Locale.ROOT).contains(lowerQuery)
                        || entry.path.toLowerCase(Locale.ROOT).contains(lowerQuery)
                        || entry.searchableTooltip.contains(lowerQuery)) {
                    filteredEntries.add(entry);
                }
            }
        }

        // Reset scroll when searching
        gridScrollbar = new ScrollbarWidget(0, 0, 6, 0, 1.0, false);

        // Reduce columns number when there are fewer items displayed
        this.columns = Math.clamp(this.filteredEntries.size(), 9, Math.clamp(this.width / 18 - 5, 9, 64));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 5, 0xFFFFFFFF);

        int startX = (this.width - (columns * 18)) / 2;
        int startY = 50;

        SelectionEntry hoveredEntry = null;

        // Render the Grid
        for (int i = 0; i < maxVisible; i++) {
            int dataIndex = (gridScrollbar.getStepOffset() * columns) + i;
            if (dataIndex >= filteredEntries.size()) break;

            SelectionEntry entry = filteredEntries.get(dataIndex);
            int col = i % columns;
            int row = i / columns;
            int cellX = startX + (col * 18);
            int cellY = startY + (row * 18);

            // Draw Item Icon
            context.drawItem(entry.stack, cellX + 1, cellY + 1);

            // If it's a tag, draw a small yellow '#' overlay
            if (entry.isTag) {
                context.drawText(this.textRenderer, "#", cellX + 2, cellY + 2, 0xFFFFFF00, true);
            }

            // Hover logic
            if (mouseX >= cellX && mouseX < cellX + 18 && mouseY >= cellY && mouseY < cellY + 18) {
                context.fill(cellX, cellY, cellX + 18, cellY + 18, 0x88FFFFFF); // Highlight box
                hoveredEntry = entry;
            }
        }

        // Draw JEI-styled Tooltip on top of everything
        if (hoveredEntry != null) {
            List<Text> tooltip = new ArrayList<>();

            if (hoveredEntry.isTag) {
                Identifier tagId = Identifier.tryParse(hoveredEntry.id.substring(1));
                int count = TranslationUtils.getTagSize(tagId, this.contextType);
                String countStr = count >= 0 ? Integer.toString(count) : "?";

                tooltip.add(Text.literal(hoveredEntry.displayName).formatted(Formatting.GOLD));
                tooltip.add(Text.translatable("gui.pacpack-quests.tag")
                        .append(Text.literal(" (" + countStr + " "))
                        .append(Text.translatable("registry.pacpack-quests." + contextType.name().toLowerCase(Locale.ROOT)))
                        .append(Text.literal(")"))
                        .formatted(Formatting.GRAY));
            } else if (!hoveredEntry.stack.isEmpty() && contextType == RegistryType.ITEM) {
                // Vanilla item tooltip (handles item name color, enchantments, potion effects)
                tooltip.addAll(Screen.getTooltipFromItem(this.client, hoveredEntry.stack));
            } else {
                tooltip.add(Text.literal(hoveredEntry.displayName).formatted(Formatting.GOLD));
            }

            // JEI mod name line in Blue Italic
            tooltip.add(Text.literal(hoveredEntry.modName).formatted(Formatting.BLUE, Formatting.ITALIC));

            context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
        }

        // Scrollbar Render
        int maxScroll = Math.max(0, (filteredEntries.size() - maxVisible + columns - 1) / columns);
        int scrollX = startX + (columns * 18) + 5;
        gridScrollbar.setBounds(scrollX, startY, 6, rows * 18);
        gridScrollbar.setMaxScroll(maxScroll, rows);
        gridScrollbar.render(context, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) { // Left click
            int startX = (this.width - (columns * 18)) / 2;
            int startY = 50;

            if (gridScrollbar.mouseClicked(click.x(), click.y(), 0)) {
                return true;
            }

            // Normal Grid Click Logic
            for (int i = 0; i < maxVisible; i++) {
                int dataIndex = (gridScrollbar.getStepOffset() * columns) + i;
                if (dataIndex >= filteredEntries.size()) break;

                int col = i % columns;
                int row = i / columns;
                int cellX = startX + (col * 18);
                int cellY = startY + (row * 18);

                // If user clicked this cell, trigger callback and close
                if (click.x() >= cellX && click.x() < cellX + 18 && click.y() >= cellY && click.y() < cellY + 18) {
                    this.onSelected.accept(filteredEntries.get(dataIndex).id);
                    this.client.setScreen(this.parent);
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (gridScrollbar.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (gridScrollbar.mouseDragged(click.x(), click.y(), click.button(), offsetX, offsetY)) return true;
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (gridScrollbar.mouseReleased(click.x(), click.y(), click.button())) return true;
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
            this.client.setScreen(this.parent);
            return true;
        } else if (input.getKeycode() == GLFW.GLFW_KEY_ENTER) {
            this.onSelected.accept(this.searchBox.getText());
            this.client.setScreen(this.parent);
            return true;
        }
        return super.keyPressed(input);
    }

    /**
     * Compares entries for non-item contexts.
     * Tags are placed at the very end, preceded by raw numeric ID order or alphabetical order.
     */
    private int compareEntries(SelectionEntry a, SelectionEntry b) {
        if (a.isTag && !b.isTag) return 1;
        if (!a.isTag && b.isTag) return -1;
        if (a.isTag && b.isTag) return a.id.compareTo(b.id);

        int idA = getRawId(a.id);
        int idB = getRawId(b.id);

        if (idA != -1 && idB != -1) {
            return Integer.compare(idA, idB);
        }

        return a.id.compareTo(b.id);
    }

    private int getRawId(String entry) {
        Identifier id = Identifier.tryParse(entry);
        if (id == null) return -1;

        var world = MinecraftClient.getInstance().world;
        var server = MinecraftClient.getInstance().getServer();

        return switch (this.contextType) {
            case ITEM -> Registries.ITEM.containsId(id) ? Registries.ITEM.getRawId(Registries.ITEM.get(id)) : -1;
            case BLOCK -> Registries.BLOCK.containsId(id) ? Registries.BLOCK.getRawId(Registries.BLOCK.get(id)) : -1;
            case MOB -> Registries.ENTITY_TYPE.containsId(id) ? Registries.ENTITY_TYPE.getRawId(Registries.ENTITY_TYPE.get(id)) : -1;
            case BIOME -> {
                if (world != null) {
                    var reg = world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
                    yield reg.containsId(id) ? reg.getRawId(reg.get(id)) : -1;
                }
                yield -1;
            }
            case STRUCTURE -> {
                if (server != null) {
                    var reg = server.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
                    yield reg.containsId(id) ? reg.getRawId(reg.get(id)) : -1;
                }
                yield -1;
            }
            case DIMENSION, LOOTABLE, COMMAND -> -1;
        };
    }
}
