Carpet SOG Addition (sog_carpet)

https://img.shields.io/badge/license-MIT-blue.svg
https://img.shields.io/badge/version-1.1.0-green.svg

A Fabric-based Carpet addon mod that provides configurable survival and Technical Minecraft auxiliary rules, along with powerful built-in visualization features.

📖 Introduction

carpet-SOG-addition is a Carpet addon mod focused on Minecraft survival and Technical Minecraft (Redstone/Survival Tech) assistance. It offers a range of practical game mechanic adjustments for server administrators and players, along with built-in powerful visualization and debugging tools. Rules are uniformly enforced by the server and persist via carpet.conf, ensuring consistent behavior across single-player, LAN, and dedicated servers.

✨ Features

🎮 Survival & Technical Rules

· Environment Control: Disable copper natural oxidation, restrict amethyst bud/cluster growth, disable ice/snow melting, disable snow generation, disable lightning.
· Mob Restrictions:
  · Disable Chicken Jockeys (Baby Zombies, Drowned, Husks, Zombie Villagers, and Zombified Piglins will no longer spawn as chicken jockeys).
  · Disable Warden spawning.
  · Disable Nitwit villager spawning and bed claiming.
· Blocks & Items:
  · Silk touch reinforced deepslate and suspicious blocks.
  · Adjustable/lockable Sculk Shrieker levels.
  · Beacon additional effects.
  · Simplified crafting for tinted glass, elytra, and snow.
  · Shears don't lose durability.
· Fake Players: Fake players only pick up tools, fake player render settings.
· Building Assistance: Tweakerro flexible placement of four-corner triangles.

👁️ Visualization System

Comprehensive visualization rendering support for:

· Sculk detection range
· Creaking Heart
· Ender Dragon pathfinding and destruction range
· Wither destruction range
· Mob spawning wandering
· World Eater Helper (including block list)
· Explosion preview
· b36 destination preview

⌨️ Shortcuts

Shortcut Action
Ctrl + O Toggle the mod on/off (Master switch)
Ctrl + V Open settings menu
Ctrl + E Open World Eater Helper block list

⚙️ Technical Features

· Server-side Sync: Visualization and client-side toggles are synced by the server to clients with the mod installed.
· Performance Optimization: Mob spawning trajectories are sent tick by tick. When no one is watching, the server stops collecting data and only sends trajectories near players.
· Personalized Configurations: Visualization rendering lists are saved per player on the server (/sogcarpet visualizer). Administrators can configure it even if the player doesn't have the client-side mod installed. Player settings do not affect each other.

📋 Requirements

· Minecraft: ~26.2
· Fabric Loader: >=0.19.3
· Fabric API: Any version (*)
· Carpet Mod: >=26.2
· Java: >=25

Optional Dependencies (Recommendations)

· Recommended: gca (*)
· Suggested: tweakeroo (*)

🚀 Installation

1. Ensure you have Fabric Loader (>=0.19.3), Fabric API, and Carpet Mod (>=26.2) installed.
2. Download the latest carpet-SOG-addition-[version].jar from the Releases page.
3. Drop the downloaded .jar file into your .minecraft/mods folder.
4. (Optional but recommended) Install gca and tweakeroo for the best experience.
5. Launch the game and enjoy!

🔗 Links

· Source Code: GitHub Repository
· Issues & Bug Reports: Issues Page

📜 License

This project is licensed under the MIT License. See the LICENSE file for details.

👥 Credits

· Author: WuLi_oi
· Note: Developed with the help of AI tools.
· Built upon the Carpet Mod framework.
