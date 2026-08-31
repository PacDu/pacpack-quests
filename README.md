# PacPack Quests

A lightweight, datapack-driven questing mod for Minecraft Fabric 1.21.11.

## 🚀 Current State
The foundational framework is complete. Currently, quests must be defined as JSON files within the mod's source (`data/<namespace>/quests/`) prior to compilation, or injected via traditional world datapacks.

**Core Features:**
* **JSON Definitions:** Quests are loaded dynamically via the server's resource manager.
* **Persistent Data:** Player progress is securely saved per-world.
* **Client/Server Sync:** The server handles all logic and anti-cheat validation, syncing visual data to the client UI.
* **Task Types:** Support for `MINE_BLOCK`, `KILL_MOB`, and `CRAFT_ITEM`.
* **Dynamic UI:** Press `O` to open an interface that scales automatically with registered quests.

## 🗺️ Roadmap
* **External Config/In-Game Editor:** Transition from pre-compiled JSONs to a global config folder and an in-game UI editor.
* **Advanced UI:** Add quest dependencies, visual skill trees, and categories.
* **New Task Types:** Item submission, dimension travel, biome exploration and more.
* **Expanded Rewards:** Support for Loot Tables, XP, and server commands.
* **Multiplayer:** Party system for shared quest progression.