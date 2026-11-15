Carpet SOG Addition (sog_carpet)

https://img.shields.io/badge/license-MIT-blue.svg
https://img.shields.io/badge/version-1.2.0-green.svg

A Fabric-based Carpet addon mod that provides configurable survival and Technical Minecraft auxiliary rules, along with powerful built-in visualization features.

📖 Introduction

carpet-SOG-addition is a Carpet addon mod focused on Minecraft survival and Technical Minecraft (Redstone/Survival Tech) assistance. It offers a range of practical game mechanic adjustments for server administrators and players, along with built-in powerful visualization and debugging tools. Rules are uniformly enforced by the server and persist via carpet.conf, ensuring consistent behavior across single-player, LAN, and dedicated servers.

Version 1.2.0 is a stable release (the previous beta suffix has been dropped). Every rule below is available on all supported Minecraft versions.

✨ Features

🎮 Survival & Technical Rules

· Environment Control: disable copper natural oxidation, cap amethyst bud/cluster growth (small / medium / large / cluster), disable ice & snow melting, force-thaw light level, instant ice melting, disable snow generation, disable snow golem snow trails, disable lightning.
· Mob Restrictions: disable Chicken Jockeys, disable Warden spawning, disable Nitwit spawning and bed claiming, adjustable/lockable Sculk Shrieker levels, Giant with its 14w50a AI (moves, attacks, infects villagers).
· Blocks & Items: silk touch reinforced deepslate and suspicious sand/gravel, bonemeal-able chorus flowers, lightning turns clay into terracotta, simplified crafting for tinted glass / elytra / snow, wood stripping recipes, shears and elytra never lose durability.
· Dispensers: copper scraping / wax removal with an axe, tilling with a hoe, planting crops and chorus flowers, harvesting sweet and glow berries with shears (durability and drop tables match vanilla).
· Beacons: custom beacon effects with an in-game searchable picker, nether-star payment giving an extra effect level, effect blacklist / whitelist modes, and a portable beacon that applies effects straight from your inventory.
· Fake Players: fake players only pick up tools (per-bot tool toggles), per-bot render / simulation distance sliders.
· Building & Movement: Tweakeroo flexible placement four-corner triangles, FarPearl long-range pearl teleporting (velocity vectors built from named paper and spears).

👁️ Visualization System

Every visualizer is toggled globally with Ctrl + O, configured per player in the Ctrl + V panel, and can be switched on or off per rule on the server side.

· Sculk detection range, Creaking Heart links, Ender Dragon pathfinding & break range, Wither break range
· Mob spawn paths, spawn success statistics (Tab list footer), spawnable block ranges, Villager workstation/bed binding
· Explosion preview (destruction, rays, sample points, fuse timer, range sphere), b36 destination preview
· Projectile trajectories with landing preview, velocity / momentum / acceleration vectors, aggro lines, targeting ranges
· Lightning strike ranges & lightning rod attraction range, sweep attack range, drop count labels, item retention time
· Health change numbers, illegal block rendering (barriers, structure voids, light blocks), item flow tracking
· World Eater Helper with an editable block list (Ctrl + E) and ghost block rendering

⌨️ Shortcuts

Shortcut Action
Ctrl + O Toggle the mod on/off (Master switch)
Ctrl + V Open settings menu
Ctrl + E Open World Eater Helper block list

⚙️ Technical Features

· Server-side Sync: Visualization and client-side toggles are synced by the server to clients with the mod installed.
· Performance Optimization: Mob spawning trajectories are sent tick by tick. When no one is watching, the server stops collecting data and only sends trajectories near players.
· Personalized Configurations: Visualization rendering lists are saved per player on the server (/sogcarpet visualizer). Administrators can configure it even if the player doesn't have the client-side mod installed. Player settings do not affect each other.
· Client-only fallbacks: most visualizers still work on servers without the mod installed.

📦 Supported Versions

The repository root holds the main 26.2 project; every other supported Minecraft version lives in its own folder under port/, each a complete, buildable source tree.

· Main (root): Minecraft 26.2
· port/26.3, port/26.1.2
· port/1.21, port/1.21.1 … port/1.21.11

Version strings use the form `1.2.0-<minecraft-version>` (for example 1.2.0-26.2, 1.2.0-1.21.5). All 15 trees are built with ./gradlew build and started with ./gradlew runServer without a single mixin failure.

📋 Requirements

· Minecraft: ~26.2 (see port/ for other versions)
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

🔧 Building from Source

cd <repository-root>      # or: cd port/1.21.5
./gradlew build           # result: build/libs/sog_carpet-1.2.0-<mcversion>.jar

🔗 Links

· Source Code: GitHub Repository
· Issues & Bug Reports: Issues Page

📜 License

This project is licensed under the MIT License. See the LICENSE file for details.

👥 Credits

· Author: WuLi_oi
· Note: Developed with the help of AI tools.
· Built upon the Carpet Mod framework.
