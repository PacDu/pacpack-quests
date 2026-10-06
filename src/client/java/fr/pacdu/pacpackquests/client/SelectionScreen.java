package fr.pacdu.pacpackquests.client;

import fr.pacdu.pacpackquests.network.RequestRegistryPayload;
import fr.pacdu.pacpackquests.util.IconUtils;
import fr.pacdu.pacpackquests.util.RegistryType;
import fr.pacdu.pacpackquests.util.TagUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static fr.pacdu.pacpackquests.util.TranslationUtils.getTranslatedName;

public class SelectionScreen extends Screen {

    private final Screen parent;
    private final RegistryType contextType;
    private final Consumer<String> onSelected;

    private TextFieldWidget searchBox;
    private final List<String> allEntries = new ArrayList<>();
    private final List<String> filteredEntries = new ArrayList<>();

    private fr.pacdu.pacpackquests.client.gui.ScrollbarWidget gridScrollbar = new fr.pacdu.pacpackquests.client.gui.ScrollbarWidget(0, 0, 6, 0, 1.0, false);

    private int columns;
    private int rows;
    private int maxVisible;
    private boolean withTags = true;

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

    private void populateEntries() {
        allEntries.clear();
        var world = MinecraftClient.getInstance().world;

        switch (contextType) {
            case ITEM -> {
                Registries.ITEM.getIds().forEach(id -> allEntries.add(id.toString()));
                if (this.withTags) Registries.ITEM.streamTags().forEach(tag -> tag.getTagKey().ifPresent(key -> allEntries.add("#" + key.id().toString())));
            }
            case BLOCK -> {
                Registries.BLOCK.getIds().forEach(id -> allEntries.add(id.toString()));
                Registries.BLOCK.streamTags().forEach(tag -> tag.getTagKey().ifPresent(key -> allEntries.add("#" + key.id().toString())));
            }
            case MOB -> {
                java.util.Set<Identifier> validMobs = new java.util.HashSet<>();

                // 1. Filter the raw entity IDs to keep only real mobs
                Registries.ENTITY_TYPE.getIds().forEach(id -> {
                    net.minecraft.entity.EntityType<?> type = Registries.ENTITY_TYPE.get(id);
                    net.minecraft.entity.SpawnGroup group = type.getSpawnGroup();

                    // Check if a spawn egg exists for this entity (covers MISC mobs like Wither, Villagers, Golems)
                    Identifier eggId = Identifier.of(id.getNamespace(), id.getPath() + "_spawn_egg");
                    boolean hasSpawnEgg = Registries.ITEM.containsId(eggId);

                    // A valid mob is either in a natural spawn group, has a spawn egg, or is the player explicitly
                    if (group != net.minecraft.entity.SpawnGroup.MISC || hasSpawnEgg || id.getPath().equals("player")) {
                        validMobs.add(id);
                        allEntries.add(id.toString());
                    }
                });

                // 2. Add a Tag (group) only if at least ONE of its entities is a valid mob
                Registries.ENTITY_TYPE.streamTags().forEach(tagList -> {
                    boolean hasValidMob = tagList.stream().anyMatch(entry ->
                            validMobs.contains(Registries.ENTITY_TYPE.getId(entry.value()))
                    );

                    if (hasValidMob) {
                        tagList.getTagKey().ifPresent(key -> allEntries.add("#" + key.id().toString()));
                    }
                });
            }
            case BIOME -> {
                if (world != null) {
                    var registry = world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
                    registry.getIds().forEach(id -> allEntries.add(id.toString()));
                    registry.streamTags().forEach(tag -> tag.getTagKey().ifPresent(key -> allEntries.add("#" + key.id().toString())));
                }
            }
            case STRUCTURE -> {
                // The structures exist only on the server side. We check whether we're playing solo to retrieve them.
                var server = MinecraftClient.getInstance().getServer();
                if (server != null) {
                    var registry = server.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
                    registry.getIds().forEach(id -> allEntries.add(id.toString()));
                    registry.streamTags().forEach(tag -> tag.getTagKey().ifPresent(key -> allEntries.add("#" + key.id().toString())));
                } else {
                    allEntries.addAll(PacPackQuestsClient.SERVER_STRUCTURES);
                    ClientPlayNetworking.send(new RequestRegistryPayload(RegistryType.STRUCTURE));
                }
            }
            case DIMENSION -> {
                var handler = MinecraftClient.getInstance().getNetworkHandler();
                if (handler != null) {
                    handler.getWorldKeys().forEach(key -> allEntries.add(key.getValue().toString()));
                }
            }
            case LOOT_TABLE -> {
                allEntries.add("#pacpackquests:loot_chest");
                var server = MinecraftClient.getInstance().getServer();
                if (server != null) {
                    var opt = server.getReloadableRegistries().createRegistryLookup().getOptional(RegistryKeys.LOOT_TABLE);
                    opt.ifPresent(lootTableImpl -> lootTableImpl.streamKeys().forEach(key -> {
                        Identifier id = key.getValue();
                        if (id.getPath().startsWith("chests/")) {
                            allEntries.add(id.toString());
                        }
                    }));
                } else {
                    allEntries.addAll(PacPackQuestsClient.SERVER_LOOT_TABLES);
                    ClientPlayNetworking.send(new RequestRegistryPayload(RegistryType.LOOT_TABLE));
                }
            }
        }


        this.allEntries.sort(this::compareEntries);
    }

    public RegistryType getRegistryType() {
        return this.contextType;
    }

    public void refreshEntries(java.util.List<String> newEntries) {
        this.allEntries.clear();
        if (this.contextType == RegistryType.LOOT_TABLE) this.allEntries.add("#pacpackquests:loot_chest");
        this.allEntries.addAll(newEntries);
        this.allEntries.sort(this::compareEntries);
        this.updateSearch(this.searchBox.getText());
    }

    private void updateSearch(String query) {
        String lowerQuery = query.toLowerCase().replaceFirst("@", "");
        filteredEntries.clear();

        for (String entry : allEntries) {
            int separatorIndex = entry.indexOf(":");

            // If the query start with @, search only in the classname
            if (!query.isEmpty() && query.charAt(0) == '@') {
                String classname = entry.substring(0, separatorIndex);
                if (classname.toLowerCase().replaceFirst("#", "").startsWith(lowerQuery)) {
                    filteredEntries.add(entry);
                }
            } else {
                String path = entry.substring(separatorIndex + 1);
                if (path.toLowerCase().contains(lowerQuery) || getTranslatedName(entry, this.contextType).toLowerCase().contains(lowerQuery)) {
                    filteredEntries.add(entry);
                }
            }
        }
        // Reset scroll when searching
        gridScrollbar = new fr.pacdu.pacpackquests.client.gui.ScrollbarWidget(0, 0, 6, 0, 1.0, false);

        // Reduce columns number when there are fewer items displayed
        this.columns = Math.clamp(this.filteredEntries.size(), 9, Math.clamp(this.width / 18 - 5, 9, 64));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 5, 0xFFFFFFFF);

        int startX = (this.width - (columns * 18)) / 2;
        int startY = 50;

        String hoveredEntry = null;

        // Render the Grid
        for (int i = 0; i < maxVisible; i++) {
            int dataIndex = (gridScrollbar.getStepOffset() * columns) + i;
            if (dataIndex >= filteredEntries.size()) break;

            String entryId = filteredEntries.get(dataIndex);
            int col = i % columns;
            int row = i / columns;
            int cellX = startX + (col * 18);
            int cellY = startY + (row * 18);

            // Draw Item Icon
            ItemStack icon = IconUtils.getRepresentativeItem(entryId, this.contextType).getDefaultStack();
            context.drawItem(icon, cellX + 1, cellY + 1);

            // If it's a tag, draw a small yellow '#' overlay
            if (entryId.startsWith("#")) {
                context.drawText(this.textRenderer, "#", cellX + 2, cellY + 2, 0xFFFFFF00, true);
            }

            // Hover logic
            if (mouseX >= cellX && mouseX < cellX + 18 && mouseY >= cellY && mouseY < cellY + 18) {
                context.fill(cellX, cellY, cellX + 18, cellY + 18, 0x88FFFFFF); // Highlight box
                hoveredEntry = entryId;
            }
        }

        // Draw Tooltip on top of everything
        if (hoveredEntry != null) {
            List<Text> tooltip = new ArrayList<>();
            tooltip.add(Text.literal(getTranslatedName(hoveredEntry, this.contextType)).formatted(Formatting.GOLD));
            tooltip.add(Text.literal(hoveredEntry).formatted(Formatting.DARK_GRAY));
            context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
        }

                // --- Scrollbar Render ---
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

            if (gridScrollbar.mouseClicked(click.x(), click.y(), click.button())) {
                return true;
            }

            // 2. Normal Grid Click Logic
            int scrollOffset = gridScrollbar.getStepOffset();
            for (int i = 0; i < maxVisible; i++) {
                int dataIndex = (gridScrollbar.getStepOffset() * columns) + i;
                if (dataIndex >= filteredEntries.size()) break;

                int col = i % columns;
                int row = i / columns;
                int cellX = startX + (col * 18);
                int cellY = startY + (row * 18);

                // If user clicked this cell, trigger the callback and close
                if (click.x() >= cellX && click.x() < cellX + 18 && click.y() >= cellY && click.y() < cellY + 18) {
                    this.onSelected.accept(filteredEntries.get(dataIndex));
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
        // If the player is currently dragging the scrollbar, update the scroll offset
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

    // Custom Sort Function
    private int compareEntries(String a, String b) {
        boolean aIsTag = a.startsWith("#");
        boolean bIsTag = b.startsWith("#");

        // 1. Place all the groups (Tags #) at the very beginning of the list
        if (aIsTag && !bIsTag) return 1;
        if (!aIsTag && bIsTag) return -1;
        // If there are two tags, they are sorted alphabetically
        if (aIsTag && bIsTag) return a.compareTo(b);

        // 2. We retrieve the internal numeric IDs (Raw IDs)
        int idA = getRawId(a);
        int idB = getRawId(b);

        // 3. Primary sort: Numerical order of the set (1, 2, 3...)
        if (idA != -1 && idB != -1) {
            return Integer.compare(idA, idB);
        }

        // 4. Help: Alphabetical order by ID (e.g., minecraft:zombie)
        return a.compareTo(b);
    }

    // Function to retrieve the famous Vanilla numeric ID
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
            case DIMENSION -> -1; // Dimensions do not have standard numeric IDs; they will be listed in alphabetical order
            case LOOT_TABLE -> -1;
        };
    }
}
 
