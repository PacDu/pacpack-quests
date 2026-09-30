# PacPack Quests

PacPack Quests is a modern, lightweight questing mod for Fabric 1.21.11. It features a fully integrated in-game visual editor, allowing modpack creators and server admins to build, edit, and organize drag-and-drop quest trees directly in the game without ever touching a configuration file.

![Main Interface Preview](https://cdn.modrinth.com/data/cached_images/47bbde3996755fb9df989c9d1018431cc6a608fb.jpeg)

## ✨ Features

* **In-Game Visual Editor:** Create, move, and edit quests via a canvas-based GUI with directional chevron arrows to clearly indicate quest flow.
* **Interactive Selection Menu:** Browse, search, and select items, blocks, mobs, biomes, and structures from a paginated visual grid, sorted exactly like the vanilla creative menu.
* **Exploration Quests:** Send players on adventures by requiring them to discover specific biomes, structures, or dimensions (optimized at 4Hz to ensure server performance).
* **Smart UI & Camera:** The dynamic camera automatically restricts panning to the active quest tree so players never get lost in the void, while grid-snapping and spiral-search algorithms prevent overlapping quests.
* **Multiplayer Synchronization:** Full client-server sync. Edit quests live on your server, and all connected players will see the updates instantly.
* **Custom Categories:** Group your quests into tabs (e.g., Magic, Tech, Exploration).

## 🚀 Getting Started

Press the **`O`** key (default) to open the Quest Menu.
To modify quests, you must be a Server Operator (Permission Level 2 / Gamemaster). Click the **Edit: OFF** button in the top right to toggle Edit Mode.
* **Left-Click & Drag:** Pan the camera or move a quest node.
* **Right-Click:** Edit an existing quest or click on empty space to create a new one.

## 📝 Quest Creation Guide (Mini-Tutorial)

When you right-click to create or edit a quest, you will see a form. Thanks to the in-game Selection Menu, configuration is entirely point-and-click:

* **Quest ID:** The unique internal identifier. *Accepted:* Lowercase letters, numbers, and underscores only (e.g., `getting_started`). Cannot be changed once created.
* **Title:** The display name of the quest. *Accepted:* Any text.
* **Task Type:** Toggle between `MINE_BLOCK`, `CRAFT_ITEM`, `KILL_MOB`, `EXPLORE_BIOME`, `EXPLORE_STRUCTURE`, and `EXPLORE_DIMENSION`.
* **Task Target:** Click the button to open the Selection Menu. Search for the exact block, entity, biome, or structure you need.
  * *Tags:* You can also search for and select groups (Tags) identified by a yellow `#` (e.g., `#minecraft:logs`).
* **Required Amount:** The number of times the task must be completed (or set to `1` for exploration). *Accepted:* Numbers > 0.
* **Icon:** Click to open the Selection Menu and pick the item displayed on the quest node.
* **Reward Type:** Toggle between `ITEM`, `XP`, and `LEVEL`.
* **Reward Item:** If the type is `ITEM`, click to select the reward from the grid. Ignored for XP/Levels.
* **Reward Amount:** The quantity of the item, or the amount of XP/Levels given. *Accepted:* Numbers > 0.
* **Parents (Comma separated):** Quest IDs that must be completed before this one unlocks (e.g., `quest_1, quest_2`). Leave blank for starting quests.

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
    └── exploration/               <-- Another category folder
        └── find_village.json