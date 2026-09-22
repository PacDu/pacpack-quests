package fr.pacdu.pacpackquests.client;

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

public class SelectionScreen extends Screen {

    public enum SelectionContext { ITEM, BLOCK, MOB, BIOME, STRUCTURE, DIMENSION }

    private final Screen parent;
    private final SelectionContext contextType;
    private final Consumer<String> onSelected;

    private TextFieldWidget searchBox;
    private final List<String> allEntries = new ArrayList<>();
    private final List<String> filteredEntries = new ArrayList<>();

    private int scrollOffset = 0;
    private final int columns = 9;
    private final int rows = 5;
    private final int maxVisible = columns * rows; // 45 items per page

    public SelectionScreen(Screen parent, SelectionContext contextType, Consumer<String> onSelected) {
        super(Text.translatable("gui.pacpack-quests.select_element"));
        this.parent = parent;
        this.contextType = contextType;
        this.onSelected = onSelected;
    }

    @Override
    protected void init() {
        super.init();

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
                Registries.ITEM.streamTags().forEach(tag -> allEntries.add("#" + tag.getTagKey().get().id().toString()));
            }
            case BLOCK -> {
                Registries.BLOCK.getIds().forEach(id -> allEntries.add(id.toString()));
                Registries.BLOCK.streamTags().forEach(tag -> allEntries.add("#" + tag.getTagKey().get().id().toString()));
            }
            case MOB -> {
                Registries.ENTITY_TYPE.getIds().forEach(id -> allEntries.add(id.toString()));
                Registries.ENTITY_TYPE.streamTags().forEach(tag -> allEntries.add("#" + tag.getTagKey().get().id().toString()));
            }
            case BIOME -> {
                if (world != null) {
                    var registry = world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
                    registry.getIds().forEach(id -> allEntries.add(id.toString()));
                    registry.streamTags().forEach(tag -> allEntries.add("#" + tag.getTagKey().get().id().toString()));
                }
            }
            case STRUCTURE -> {
                // The structures exist only on the server side. We check whether we're playing solo to retrieve them.
                var server = MinecraftClient.getInstance().getServer();
                if (server != null) {
                    var registry = server.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
                    registry.getIds().forEach(id -> allEntries.add(id.toString()));
                    registry.streamTags().forEach(tag -> allEntries.add("#" + tag.getTagKey().get().id().toString()));
                }
            }
            case DIMENSION -> {
                var handler = MinecraftClient.getInstance().getNetworkHandler();
                if (handler != null) {
                    handler.getWorldKeys().forEach(key -> allEntries.add(key.getValue().toString()));
                }
            }
        }

        this.allEntries.sort(this::compareEntries);
    }

    private void updateSearch(String query) {
        String lowerQuery = query.toLowerCase();
        filteredEntries.clear();

        for (String entry : allEntries) {
            // Check against the raw ID or the translated name
            if (entry.toLowerCase().contains(lowerQuery) || getTranslatedName(entry).toLowerCase().contains(lowerQuery)) {
                filteredEntries.add(entry);
            }
        }
        // Reset scroll when searching
        scrollOffset = 0;
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
            int dataIndex = (scrollOffset * columns) + i;
            if (dataIndex >= filteredEntries.size()) break;

            String entryId = filteredEntries.get(dataIndex);
            int col = i % columns;
            int row = i / columns;
            int cellX = startX + (col * 18);
            int cellY = startY + (row * 18);

            // Draw Item Icon
            ItemStack icon = getRepresentativeItem(entryId);
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
            tooltip.add(Text.literal(getTranslatedName(hoveredEntry)).formatted(Formatting.GOLD));
            tooltip.add(Text.literal(hoveredEntry).formatted(Formatting.DARK_GRAY));
            context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
        }

        // Scrollbar Indicator
        int maxScroll = Math.max(0, (filteredEntries.size() - maxVisible + columns - 1) / columns);
        if (maxScroll > 0) {
            String scrollText = (scrollOffset + 1) + " / " + (maxScroll + 1);
            context.drawText(this.textRenderer, scrollText, startX + (columns * 18) + 10, startY + (rows * 18) / 2, 0xFFAAAAAA, false);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) { // Left click
            int startX = (this.width - (columns * 18)) / 2;
            int startY = 50;

            for (int i = 0; i < maxVisible; i++) {
                int dataIndex = (scrollOffset * columns) + i;
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
        int maxScroll = Math.max(0, (filteredEntries.size() - maxVisible + columns - 1) / columns);
        // verticalAmount is negative for scrolling down
        scrollOffset -= (int) Math.signum(verticalAmount);
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll);
        return true;
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

    // --- Helper Methods to construct visual representations ---

    private ItemStack getRepresentativeItem(String entryId) {
        boolean isTag = entryId.startsWith("#");
        String rawId = isTag ? entryId.substring(1) : entryId;
        Identifier id = Identifier.tryParse(rawId);

        if (id == null) return Items.BARRIER.getDefaultStack();

        return switch (this.contextType) {
            case ITEM -> isTag ? Items.NAME_TAG.getDefaultStack() : Registries.ITEM.get(id).getDefaultStack();
            case BLOCK -> {
                if (isTag) yield Items.NAME_TAG.getDefaultStack();
                var blockItem = Registries.BLOCK.get(id).asItem();
                // Si le bloc n'a pas d'item associé (ex: l'eau, le feu), on affiche une barrière
                yield blockItem != Items.AIR ? blockItem.getDefaultStack() : Items.BARRIER.getDefaultStack();
            }
            case MOB -> {
                if (isTag) yield Items.ZOMBIE_HEAD.getDefaultStack();
                // Try to find the spawn egg for this mob
                Identifier eggId = Identifier.of(id.getNamespace(), id.getPath() + "_spawn_egg");
                if (Registries.ITEM.containsId(eggId)) yield Registries.ITEM.get(eggId).getDefaultStack();
                yield Items.SPAWNER.getDefaultStack();
            }
            case BIOME -> Items.GRASS_BLOCK.getDefaultStack();
            case STRUCTURE -> Items.CHEST.getDefaultStack();
            case DIMENSION -> Items.OBSIDIAN.getDefaultStack();
        };
    }

    private String getTranslatedName(String entryId) {
        boolean isTag = entryId.startsWith("#");
        String rawId = isTag ? entryId.substring(1) : entryId;
        Identifier id = Identifier.tryParse(rawId);

        if (id == null) return entryId;

        if (isTag) {
            String path = id.getPath();
            return "Any " + path.substring(0, 1).toUpperCase() + path.substring(1).replace("_", " ");
        }

        return switch (this.contextType) {
            case ITEM -> Registries.ITEM.containsId(id) ? Registries.ITEM.get(id).getName().getString() : rawId;
            case BLOCK -> Registries.BLOCK.containsId(id) ? Registries.BLOCK.get(id).getName().getString() : rawId;
            case MOB -> Registries.ENTITY_TYPE.containsId(id) ? Registries.ENTITY_TYPE.get(id).getName().getString() : rawId;
            default -> {
                String path = id.getPath();
                yield path.substring(0, 1).toUpperCase() + path.substring(1).replace("_", " ");
            }
        };
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
        };
    }
}