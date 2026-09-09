# PacPack Quests

PacPack Quests is a lightweight questing mod for Fabric 1.21.11. It features a fully integrated in-game visual editor, allowing modpack creators and server admins to build, edit, and organize drag-and-drop quest trees directly in the game without ever touching a configuration file.

## ✨ Features

* **In-Game Visual Editor:** Create, move, and edit quests via a canvas-based GUI.
* **Drag-and-Drop Organization:** Seamlessly reorder categories and arrange quest nodes.
* **Multiplayer Synchronization:** Full client-server sync. Edit quests live on your server, and all connected players will see the updates instantly.
* **Smart Validation:** Built-in error checking prevents typos and invalid item IDs from breaking your quests.
* **Custom Categories:** Group your quests into tabs (e.g., Magic, Tech, Exploration).

## 🚀 Getting Started

Press the **`O`** key (default) to open the Quest Menu.
To modify quests, you must be a Server Operator (Permission Level 2 / Gamemaster). Click the **Edit: OFF** button in the top right to toggle Edit Mode.
* **Left-Click & Drag:** Pan the camera or move a quest node.
* **Right-Click:** Edit an existing quest or click on empty space to create a new one.

## 📝 Quest Creation Guide (Mini-Tutorial)

When you right-click to create or edit a quest, you will see a form. Here is exactly what is accepted in each field:

* **Quest ID:** The unique internal identifier. *Accepted:* Lowercase letters, numbers, and underscores only (e.g., `getting_started`, `mine_10_iron`). Cannot be changed once created.
* **Title:** The display name of the quest. *Accepted:* Any text.
* **Task Type:** Toggle between `MINE_BLOCK`, `CRAFT_ITEM`, and `KILL_MOB`.
* **Task Target:** The specific block, item, or entity to interact with.
    * *Single Item/Mob:* Use the exact registry name (e.g., `minecraft:stone` or `minecraft:zombie`).
    * *Group/Tag:* Use a hashtag to target any item in a group (e.g., `#minecraft:logs` or `#c:ores`).
* **Required Amount:** The number of times the task must be completed. *Accepted:* Numbers > 0.
* **Icon Item ID:** The item displayed on the quest node. *Accepted:* Valid Minecraft item IDs (e.g., `minecraft:diamond_pickaxe`).
* **Reward Type:** Toggle between `ITEM`, `XP`, and `LEVEL`.
* **Reward Target:** If the type is `ITEM`, enter the item ID here. Ignored for XP/Levels.
* **Reward Amount:** The quantity of the item, or the amount of XP/Levels given. *Accepted:* Numbers > 0.
* **Parents (Comma separated):** Quest IDs that must be completed before this one unlocks. *Accepted:* Existing quest IDs separated by commas (e.g., `quest_1, quest_2`). Leave blank for starting quests.

> **💡 Pro-Tip for finding IDs:** Press **`F3 + H`** in-game to enable Advanced Tooltips. Hovering over any item in your inventory will reveal its exact registry name (e.g., `minecraft:apple`). You can also browse a complete list of vanilla IDs at [MinecraftItemIDs.com](https://minecraftitemids.com/).

## 📂 Configuration Architecture

While you never have to leave the game to build your quests, everything is safely saved as standard JSON files. This makes it easy to back up, share, or include your quests in a modpack.

Navigate to your `.minecraft/config/pacpackquests/` folder:

```text
config/pacpackquests/
├── modconfig.json                 <-- Stores general settings (like Category tab order)
└── quests/                        <-- The root folder for all quests
    ├── main/                      <-- A category folder
    │   ├── getting_started.json   <-- Individual quest data
    │   └── craft_pickaxe.json
    └── nether/                    <-- Another category folder
        └── enter_nether.json
```

*Note: If you manually edit the JSON files while the server is running, you will need to restart the server or rely on the in-game editor to overwrite changes. The in-game editor updates the server RAM and JSON files simultaneously.*

## 🔮 Future Evolutions
PacPack Quests is actively in development. Future updates plan to expand the task variety (e.g., location-based tasks), introduce new reward choices (such as pool table), and maybe revamp the interface.