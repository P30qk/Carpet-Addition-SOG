package cn.blockforge.generated.sogcarpet;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import java.util.HashMap;
import java.util.Map;

/**
 * Carpet 附属入口。
 *
 * <p>注册方式（本轮改动）：规则不再挂在附属自己的 SettingsManager 上——那会额外
 * 生成一条 {@code /sog_carpet} 指令。现在直接把 {@link SogSettings} 解析进 Carpet
 * 核心 manager（identifier 为 {@code carpet}），于是：</p>
 * <ul>
 *   <li>所有规则用 {@code /carpet <规则> <值>} 查看与修改，不再有独立 sog 指令；</li>
 *   <li>{@code /carpet} 的“浏览分类”里出现 [SOG]（规则 categories 带 SOG）；</li>
 *   <li>规则随 carpet.conf 一起持久化。</li>
 * </ul>
 *
 * <p>时机：Fabric 先跑所有模组的 main 入口（我们的 {@code GeneratedMod} 在那里
 * {@code CarpetServer.manageExtension}），之后才调 carpet 的 client/server 入口
 * {@code CarpetServer.onGameStarted}。核心 manager 先 parse 自己的规则、再回调各
 * 扩展的 onGameStarted——那一刻我们已在扩展列表里，{@code parseSettingsClass}
 * 会先刷新翻译表（收进我们的 {@code carpet.rule.*} 文案），再构造 ParsedRule，
 * 不会踩上一轮那个 “No language key provided” 的 NPE。</p>
 */
public final class SogCarpetExtension implements CarpetExtension {
    public static final String VERSION = "1.2.0-1.21.2";
    public static final String IDENTIFIER = "sog_carpet";
    public static final String NAME = "carpet-SOG-addition";

    private boolean registered;

    @Override
    public void onGameStarted() {
        registerRules();
    }

    /**
     * 幂等注册：把规则解析进核心 manager，并挂上规则切换反馈的观察器。
     * 观察器会收到核心 manager 全部规则（含原版 carpet 规则）的变更，
     * {@link SogFeedback} 内部按 {@link SogSettings#RULE_NAMES} 过滤。
     */
    private synchronized void registerRules() {
        if (this.registered) {
            return;
        }
        this.registered = true;
        CarpetServer.settingsManager.parseSettingsClass(SogSettings.class);
        CarpetServer.settingsManager.registerRuleObserver(SogFeedback::onRuleChanged);
        // 假人渲染设置的规则开关要即时改变“实际生效”的视距与票据（不改各自保存的值）。
        CarpetServer.settingsManager.registerRuleObserver(SogFakeRenderService::onRuleChanged);
        // 可视化规则开关变化后把最新状态广播给装了本模组的客户端。
        CarpetServer.settingsManager.registerRuleObserver(SogRuleSyncService::onRuleChanged);
        // “刷怪游走可视化”关掉后立刻停止采集并清掉还没下发的轨迹，避免停用后仍闪一下残影。
        CarpetServer.settingsManager.registerRuleObserver((source, rule, value) -> {
            boolean spawnRule = "mobSpawnVisualizer".equals(rule.name())
                    || "spawnStatsVisualizer".equals(rule.name());
            if (spawnRule && !SogSettings.mobSpawnVisualizer && !SogSettings.spawnStatsVisualizer) {
                SogSpawnTrace.setRecording(false);
                SogSpawnTrace.clear();
                SogSpawnStats.setRecording(false);
                SogSpawnStats.clear();
            }
        });
        // 配方规则的开关变化后重发配方书：26.2 只在“加入”与“刚刚解锁”两个时机给客户端
        // 发配方的展示条目，规则中途被打开时没人重发，配方就永远不会出现在配方书里。
        // 这里给所有在线玩家补解锁 + 整份重发，所以原版配方书与 EasierCrafting 等配方书模组
        // 都能立刻看到/不再看到对应配方。
        CarpetServer.settingsManager.registerRuleObserver((source, rule, value) -> {
            if (SogRecipeBook.RULES.contains(rule.name())) {
                net.minecraft.server.MinecraftServer server = source.getServer();
                if (server != null) {
                    SogRecipeBook.refreshAll(server);
                }
            }
        });
        // 物品流向跟踪关闭后立刻清除全部追踪会话与高亮显示实体。
        CarpetServer.settingsManager.registerRuleObserver((source, rule, value) -> {
            if ("itemFlowTracker".equals(rule.name()) && !SogSettings.itemFlowTracker) {
                net.minecraft.server.MinecraftServer server = source.getServer();
                if (server != null) {
                    cn.blockforge.generated.sogcarpet.itemflow.ItemFlowTrackerFeature.clear(server);
                }
            }
        });
        // 巨人旧版 AI 开启后，给所有已经加载的巨人补装 AI；之后新召唤 / 新加载的巨人
        // 由 GeneratedMod 里挂的 ServerEntityEvents.Load 处理。
        CarpetServer.settingsManager.registerRuleObserver((source, rule, value) -> {
            if ("giantOldAi".equals(rule.name()) && SogSettings.giantOldAi) {
                net.minecraft.server.MinecraftServer server = source.getServer();
                if (server != null) {
                    for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                            if (entity instanceof net.minecraft.world.entity.monster.Giant giant) {
                                ((GiantAiAccess) giant).sog$ensureOldAi();
                            }
                        }
                    }
                }
            }
        });
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public Map<String, String> canHasTranslations(String language) {
        boolean chinese = language != null && language.toLowerCase().startsWith("zh");
        if (!chinese) {
            return enTranslations();
        }
        // 繁體中文（zh_tw / zh_hk / zh_hant / zh_mo）：规则名与说明直接放在代码里，不走语言文件，
        // 所以这里从随包资源读取预先转换好的繁体文案；读不到时退回简体，功能不受影响。
        if (isTraditionalChinese(language)) {
            Map<String, String> traditional = SogTraditionalChinese.loadRules();
            if (traditional != null && !traditional.isEmpty()) {
                return traditional;
            }
        }
        return zhTranslations();
    }

    /** 这个语言代码是不是繁体中文（台湾 / 香港 / 澳门 / 通用繁体）。 */
    private static boolean isTraditionalChinese(String language) {
        if (language == null) {
            return false;
        }
        String code = language.toLowerCase().replace('_', '-');
        return code.startsWith("zh-tw") || code.startsWith("zh-hk")
                || code.startsWith("zh-hant") || code.startsWith("zh-mo");
    }

    /**
     * 键前缀说明：规则现在挂在核心 manager（identifier {@code carpet}）上，
     * ParsedRule 查的是 {@code carpet.rule.<规则>.name/desc}；分类文案查
     * {@code carpet.category.SOG}。为了兼容“附属独立 manager”的旧形态，
     * 每个键同时以 {@code sog_carpet.} 前缀再给一份。
     */
    private static void rule(Map<String, String> map, String name, String label, String desc) {
        map.put("carpet.rule." + name + ".name", label);
        map.put("carpet.rule." + name + ".desc", desc);
        map.put("sog_carpet.rule." + name + ".name", label);
        map.put("sog_carpet.rule." + name + ".desc", desc);
    }

    private static Map<String, String> enTranslations() {
        Map<String, String> m = new HashMap<>();
        m.put("carpet.category.SOG", "SOG");
        m.put("sog_carpet.category.SOG", "SOG");
        rule(m, "copperOxidationDisabled", "Copper oxidation disabled",
                "Unwaxed copper blocks, cut copper, stairs, slabs and the rest no longer advance to the next oxidation stage on random ticks. Waxing and axe scraping stay vanilla.");
        rule(m, "dispenserCopperScrape", "Dispenser can scrape / dewax",
                "A dispenser holding an axe of any material (anything in the #minecraft:axes tag, modded axes included) treats the copper block it faces exactly like a player using that axe: waxed copper loses its wax, and unwaxed oxidized copper is scraped back one oxidation level (oxidized -> weathered -> exposed -> unaffected), with the vanilla scrape sounds and particles. Every successful scrape costs the axe one point of durability (the same vanilla durability path a player's axe uses, so Unbreaking still helps and the axe breaks normally when it wears out); with nothing scrapable in front, nothing happens and the axe stays undamaged in the dispenser. The rule runs on the server, so singleplayer, LAN hosts and dedicated servers all behave the same and clients need nothing installed.");
        rule(m, "dispenserTilling", "Dispenser can till",
                "A dispenser holding a hoe of any material (a vanilla HoeItem or a subclass of it, which covers every material and modded hoes that reuse the vanilla tilling logic) treats the block it faces exactly like a player using that hoe: dirt, grass blocks and dirt paths are tilled into farmland, coarse dirt into dirt, and rooted dirt into dirt plus one hanging roots drop, with the vanilla sound and game event (including vanilla restrictions such as needing free space above the target). Every successful till costs the hoe one point of durability, just like a player's hoe; with nothing tillable in front, nothing happens and the hoe stays undamaged in the dispenser. The rule runs on the server, so singleplayer, LAN hosts and dedicated servers all behave the same and clients need nothing installed.");
        rule(m, "dispenserPlanting", "Dispenser can plant crops",
                "A dispenser holding a crop item (anything whose block is in the #minecraft:crops tag: wheat seeds, beetroot seeds, carrots, potatoes, melon and pumpkin seeds, torchflower seeds, pitcher pods; modded crops that join the tag work too) plants it in front after being triggered: aimed at farmland it plants on top of the farmland, and aimed at the free spot right above farmland it plants directly into that spot. A dispenser holding a chorus flower plants it onto end stone (or into the free spot right above end stone) the same way. Planting goes through the vanilla placement path, so replaceability, light, survival and entity-obstruction checks all match a player placing the block, and a successful plant consumes one item with the vanilla place sound; nothing is consumed or changed when the spot does not work out. The rule runs on the server, so singleplayer, LAN hosts and dedicated servers all behave the same and clients need nothing installed.");
        rule(m, "dispenserBerryHarvest", "Dispenser can harvest berries",
                "A dispenser holding a pair of shears (vanilla or modded) harvests the ripe sweet berry bush or the cave vines with glow berries right in front of it: sweet berries are collected through the vanilla loot table (the bush returns to its just-picked age=1 state) and cave vines drop one glow berry and turn bare again, with the vanilla sounds and game events of a manual pick. Every successful harvest costs the shears one point of durability (cancelled while the 'Shears take no durability' rule is on); with nothing to pick in front no durability is spent and the vanilla shears dispenser behaviour is used instead, so shearing beehives and entities keeps working exactly as before. The rule runs on the server, so singleplayer, LAN hosts and dedicated servers all behave the same and clients need nothing installed.");
        rule(m, "chorusFlowerBonemeal", "Chorus flowers can be bonemealed",
                "Bone meal can ripen chorus flowers and the chorus plants they are attached to (player right-click or a dispenser). Each successful use makes the nearest still-growing chorus flower run exactly one vanilla random-tick growth decision, including the vanilla 'grow upwards?' die: it extends upwards when that passes (the old spot becomes a chorus plant and a same-age flower appears above it), otherwise it tries to branch sideways with one or more new flowers one age older, and when there is truly nowhere left to grow it wilts into a dead flower exactly like natural growth. Ripening therefore always runs down like nature - flowers eventually wilt, and a wilted flower can no longer be bonemealed (the only 'nothing happens' case is a flower with its head room blocked, where the bone meal is not consumed). With the rule off everything is exactly vanilla (bone meal does nothing to chorus blocks). The rule runs on the server, so singleplayer, LAN hosts and dedicated servers all behave the same.");
        rule(m, "amethystBudGrowthLimit", "Amethyst bud growth limit",
                "Freezes amethyst growth at the chosen stage: a bud that has reached that stage stops upgrading, while smaller buds still grow up to it (with medium, a small bud still turns into a medium one) and any bigger buds that already exist keep growing as usual. small, medium, large or cluster pick the stage; cluster is the end of the chain and equals vanilla; off = vanilla, buds grow all the way to a cluster.");
        rule(m, "iceSnowMeltDisabled", "Ice and snow melting disabled",
                "Ice, snow layers and frosted ice no longer melt on random or scheduled ticks. Existing blocks are kept as they are.");
        rule(m, "forceThawLightLevel", "Forced thaw light level",
                "Ice turns to water as soon as block light reaches this level on its random tick (0-15). 12 equals the vanilla melting light level.");
        rule(m, "iceInstantMelt", "Instant ice melt",
                "Ice that has reached the melting light level melts immediately on placement or neighbour updates, without waiting for a random tick.");
        rule(m, "snowGenerationDisabled", "Snow generation disabled",
                "Cold biomes and thunderstorms no longer build up new snow layers. Existing snow is kept and water freezing stays vanilla.");
        rule(m, "snowGolemSnowDisabled", "Snow golem snow trail disabled",
                "Snow golems no longer place snow under themselves while moving.");
        rule(m, "thunderDisabled", "Thunder and lightning disabled",
                "No lightning bolts are spawned during thunderstorms. Rain and thunder weather itself are unchanged.");
        rule(m, "chickenJockeyDisabled", "Chicken jockeys disabled",
                "Baby zombies and the four baby zombie variants - drowned, husk, zombie villager and zombified piglin - never mount chickens, so no new chicken jockey variant is formed by baby zombies, baby drowned, baby husks, baby zombie villagers or baby zombified piglins. Already existing jockeys are kept and nothing else about these mobs changes.");
        rule(m, "silkTouchReinforcedDeepslate", "Silk touch reinforced deepslate",
                "Mining reinforced deepslate with a Silk Touch pickaxe drops the block itself.");
        rule(m, "silkTouchSuspicious", "Silk touch suspicious sand & gravel",
                "Mining suspicious sand or suspicious gravel with a Silk Touch shovel drops the block itself.");
        rule(m, "sculkShriekerLevel", "Sculk shrieker level",
                "0 = vanilla. 1-4: whenever a shrieker would update its warning level after a successful shriek, it is set to this value instead.");
        rule(m, "sculkShriekerLevelLocked", "Sculk shrieker level lock",
                "Freezes every shrieker's warning level at its current value: no increases, no resets while shrieking.");
        rule(m, "wardenSpawnDisabled", "Warden spawn disabled",
                "Sculk shriekers can no longer summon the warden. Spawn eggs and commands are unaffected.");
        rule(m, "nitwitSpawnDisabled", "Nitwit villager spawn disabled",
                "Newly spawned villagers never start as nitwits; they become unemployed instead and can take a job site later.");
        rule(m, "nitwitBedDisabled", "Nitwit villagers cannot claim beds",
                "Nitwits never claim or climb into a bed, so beds stay free for working villagers. A nitwit that is already lying in a bed when the rule is turned on is kicked out immediately.");
        rule(m, "tintedGlassEasyCraft", "Easy tinted glass crafting",
                "Adds a shaped recipe: one glass with one black dye above, below, left and right (four dye in total) yields one tinted glass.");
        rule(m, "easyElytraCraft", "Easy elytra crafting",
                "Adds a shaped recipe: six phantom membranes and three string, laid out as membrane-string-membrane in every row, yield one elytra.");
        rule(m, "customBeaconEffects", "Custom beacon effects",
                "A beacon with at least one layer gets an extra slot in the empty space beside its primary effect rows, and a fully powered (4 layer) beacon gets a second slot beside its secondary row. Click a slot to open a searchable list of every effect in the game: pick one and its icon fills that slot, then press the vanilla confirm button to apply it. Levels, duration and refresh rate keep using the vanilla formulas (with 4 layers and the same effect chosen for both slots you still get the vanilla level II). The beacon's payment slot also accepts a nether star while the rule is on (its icon is drawn right after the diamond; placing it needs this mod on the client, the server side is ready either way); a beacon paid for with a nether star applies its primary and secondary effects one level higher than usual, and that is stored in the beacon's NBT so it survives restarts. Reopening the beacon with an ordinary payment removes that bonus again. Turning the rule off hides the extra slots and lets every beacon that used a custom effect or a nether star go back to a plain vanilla beacon.");
        rule(m, "beaconEffectFilterMode", "Beacon effect filter mode",
                "Chooses which of the two independent lists is active. blacklist (default) = every effect in the blacklist is blocked and all other effects stay selectable. whitelist = only the effects in the whitelist are selectable (an empty whitelist means nothing is selectable, which keeps the shown state identical to the stored list). The two lists never overwrite each other: switching the mode only changes which one is in effect. The lists apply to the custom effect slots of a beacon block only - the portable beacon is not affected. Operators change them with /carpet, /sogcarpet beaconfilter, or by right-clicking an effect in the picker (that edits the list of the current mode).");
        rule(m, "beaconEffectBlacklist", "Beacon effect blacklist",
                "Comma separated effect ids (for example minecraft:night_vision,minecraft:bad_omen) that cannot be picked in the custom effect slots of a beacon block while beaconEffectFilterMode is blacklist. An empty list blocks nothing. Independent from beaconEffectWhitelist. Duplicates, spaces and unknown ids are cleaned up automatically; the list is persisted in carpet.conf and synced to every client that has this mod. The portable beacon ignores it.");
        rule(m, "beaconEffectWhitelist", "Beacon effect whitelist",
                "Comma separated effect ids that are the only ones pickable in the custom effect slots of a beacon block while beaconEffectFilterMode is whitelist. An empty whitelist allows nothing (the picker shows a hint and operators can right-click effects to add them); switching the mode seeds it with the six effects a vanilla beacon can give so the feature never locks itself out by accident. Independent from beaconEffectBlacklist. Duplicates, spaces and unknown ids are cleaned up automatically; the list is persisted in carpet.conf and synced to every client that has this mod. The portable beacon ignores it.");
        rule(m, "portableBeacon", "Portable beacon",
                "While a beacon is anywhere in your inventory (hotbar, offhand and armour slots included), hold a beacon and press Ctrl + right-click to open the effect picker. The click is intercepted on the client before vanilla sees it, so the beacon is never placed, and the chosen effects are validated and applied by the server. You can pick up to two effects: two give level I each, a single one gives level II. The effects last as long as a beacon stays in your inventory - the server refreshes them every 4 seconds while the beacon is carried and removes them the moment it leaves your inventory (dropped, stored in a chest, taken away, lost on death). Effects follow the custom beacon rule: with it on every effect in the game can be picked, with it off only the six effects a vanilla beacon can give. The beacon effect blacklist / whitelist does not apply to the portable beacon. The beacon is not consumed. Runs on the server, so dedicated servers work the same; players without the client mod can use /sogcarpet portablebeacon <effect> [second] instead of the GUI.");
        rule(m, "lightningTurnsClay", "Lightning turns clay into terracotta",
                "Every real lightning strike (thunderstorms and trident channeling alike) turns clay inside the strike's area of effect into terracotta. The area matches vanilla's lightning check: 3 blocks horizontally and from 3 blocks below to 6 blocks above the strike point (a 7x10x7 block region), which is exactly the region the lightning range visualizer draws. Lightning that only plays visuals (visualOnly) changes nothing. Runs on the server, so singleplayer, LAN hosts and dedicated servers behave the same.");
        rule(m, "lightningRangeVisualizer", "Lightning range visualizer",
                "Draws a translucent box over the area a lightning strike affects (3 blocks horizontally, 3 below to 6 above - exactly vanilla's lightning check range) at the point where it landed; the box fades out and disappears after 5 seconds. Nearby lightning rods are drawn continuously: each rod is boxed, its attraction range is shown as a 128 block ring (the radius vanilla uses when it redirects a strike to the nearest rod), and its strike area of effect is drawn so you can see what a strike on that rod would convert. Everything is read from lightning entities and blocks the client already has, so the server sends nothing. Ctrl+O is the master switch, Ctrl+V opens the render settings.");
        rule(m, "snowCraftable", "Snow craftable",                "Adds a shapeless recipe: one snow block yields eight snow (snow layers), exactly one full block's worth.");
        rule(m, "shearsNoDurability", "Shears take no durability",
                "Shears never lose durability: shearing sheep, harvesting honeycombs, mining leaves and all other shear uses leave the tool untouched.");
        rule(m, "elytraNoDurability", "Elytra takes no durability",
                "An elytra never loses durability: gliding and firework boosts no longer wear it out. Everything else about elytra flight is unchanged.");
        rule(m, "woodStrippingRecipe", "Wood stripping recipe",
                "Adds a crafting-table recipe for every strippable wood type: place the log, wood, stem, hyphae or bamboo block with any axe (the #minecraft:axes tag) directly below it to get the matching stripped block. The axe is put back into the crafting grid afterwards and is not consumed or damaged, so one axe can strip any amount of wood.");
        rule(m, "fakePlayerToolsOnly", "Fake players only pick up tools",
                "Fake players only pick up tool items (sword, axe, pickaxe, shovel, hoe, bow, crossbow, trident, mace, fishing rod, shears, flint and steel). Armour and ordinary items stay on the ground. While the rule is on, a column of per-category switches appears on the right of the GCA fake inventory and is stored per fake; a fake that was never configured picks up every category.");
        rule(m, "sculkRangeVisualizer", "Sculk detection range visualizer",
                "Draws a translucent solid detection ball (light blue by default) around every sculk or calibrated sculk sensor, plus links between the nodes and a translucent solid cube over each node block (a deeper shade than the ball). Links between sensors are green; links to a shrieker that can summon the warden are red with a light-red ball, turning yellow with a light-yellow ball when that link is blocked by wool; links to a shrieker that cannot summon the warden are white with a light-white ball, turning grey with a light-grey ball when blocked. A sensor linked to both kinds uses the warden-capable side for its ball and cube, while each link still keeps the colour it belongs to. Wool is the only blocker. A warden is drawn as its own node in the same style: a translucent teal detection ball marks its 16-block listening range, a teal cube marks the block it stands in, and a teal line links it to every sculk sensor inside that range (shriekers are not linked). A sensor linked to a warden is drawn entirely in the warden's teal, for both its ball and its cube (this overrides the shrieker-chain colour; priority: warden, then a warden-capable shrieker, then a shrieker that cannot summon one, then a sensor-only chain). A warden link that is blocked by wool turns yellow exactly like a blocked shrieker-chain link, and the warden's own ball and cube turn yellow once every link out of it is blocked. Such a sensor is flagged with an orange warning sign above the block, and orange warning signs are spread densely and evenly across its detection ball surface; the warden's own ball is covered the same way. Whenever a sensor or a warden actually receives a vibration, a bright magenta trigger line connects the sound source (a mob, a sound-emitting block or a thrown projectile) to that detector for about a second. Toggle with Ctrl+O; the key can be rebound.");
        rule(m, "enderDragonPathVisualizer", "Ender dragon path visualizer",
                "Draws the ender dragon's 24 pathfinding nodes the way lucidity does: each node is a full block-sized translucent box, middle/inner nodes (radius 40 and 20) in the waypoint colour (255,0,118) and outer nodes (radius 60) in its inverse (0,255,137), plus a matching-colour polyline for the recent flight path and a yellow dot for the current position. Only drawn in the End, with the vanilla node layout exactly. Ctrl+O is the master switch for all visualizations, Ctrl+V opens the render settings.");
        rule(m, "witherBreakRangeVisualizer", "Wither block-breaking range visualizer",
                "Draws the area a wither destroys the way lucidity does: one translucent red solid cuboid, floor(bbWidth / 2 + 1) blocks horizontally and floor(bbHeight) blocks up from its feet, exactly like the vanilla loop. Per-block outlines are gone. Ctrl+O is the master switch, Ctrl+V opens the render settings.");
        rule(m, "enderDragonBreakRangeVisualizer", "Ender dragon block-breaking range visualizer",
                "The head, neck and body boxes that vanilla really uses to break blocks are snapped to the block grid and drawn as translucent purple (137,101,255) solids, the way lucidity does. No white frame or per-block outlines. Ctrl+O is the master switch, Ctrl+V opens the render settings.");
        rule(m, "mobSpawnVisualizer", "Mob walk path visualizer",
                "Draws the route a mob has really walked since it spawned, so the mob is always at the head of its own trail. The server only reports a spawn event when natural spawning actually puts a mob into the world (its entity id plus the spawn block); the client then follows that mob and samples its position every tick, so the trail is the mob's own path instead of the spawner's position-picking walk, and every living mob within 128 blocks of the player gets a trail rather than only the one that spawned most recently. The trail starts with a thick bright green dot, runs as a near-white polyline with yellow direction arrows every few blocks, and ends with a red arrow pointing along the mob's last movement (its facing while standing still); the trail fades out after the mob dies or leaves the range. Sampling and drawing stay entirely on the client. The spawner's roam path, the Y-axis spawn height column and the spawn success statistics are all part of the separate 'Mob spawn visualizer' (spawnStatsVisualizer) rule. Ctrl+O is the master switch, Ctrl+V opens the render settings.");
        rule(m, "spawnStatsVisualizer", "Mob spawn visualizer",
                "Visualizes the whole natural-spawn process. In the world it draws the spawner's complete random-walk roam path for every successful spawn (every random offset it distance-checked, failed ones included) as a magenta polyline with the hit point highlighted white, a Y-axis spawn height column at the start (the [minY, surface+1] range that vanilla getRandomPosWithin picks the spawn Y from), and a success-rate label next to the latest spawn. It also records success rates: per MobCategory (successes / attempts) and per check of the spawn algorithm (redstone conductor, too close, mob exists, position, spawn potential, too far / obstruction - each as passed / reached), and writes them into the player's Tab-list footer about once a second, like carpet's TPS/mobcaps stats: long-press Tab and it shows together with the online player list, and because it uses the vanilla tab-list packet even a vanilla client without this mod can see it. In Ctrl+V -> Configure the stats panel can be switched on or off as a whole, and every category line and every check line can be switched on or off separately (the choices are sent to the server); the roam-path and height-column toggles and colours are there too. While /tick freeze or /tick step is active the in-world rendering pauses. Ctrl+O is the master switch, Ctrl+V opens the render settings.");
        rule(m, "spawnRangeVisualizer", "Spawnable block range visualizer",
                "Draws the spawn range of the blocks that spawn mobs themselves. Monster spawners and trial spawners use the vanilla offset (horizontal +/-spawnRange, vertical +/-1), i.e. a 9x3x9 box around the block; a monster spawner's range is read from its real SpawnRange NBT value (4 by default), and a trial spawner's from its config's spawn_range (a custom config is not synced to clients and reads as the default 4; all vanilla data uses the defaults anyway). A sculk shrieker summons the warden with SpawnUtil within horizontal +/-5 and vertical +/-6. Each range is shown as a translucent red cuboid, and the centre spawn block gets a yellow outline. Everything is read on the client from block entities, so the server sends nothing. Ctrl+O is the master switch, Ctrl+V opens the render settings.");
        rule(m, "villagerBindingVisualizer", "Villager function block binding visualizer",
                "A green line links every villager to the bed it has claimed, with a green box around that bed; a red line links it to its job-site block, with a red box around that block. The binding only lives in the villager's server-side brain, so the server samples nearby villagers and sends the bindings to clients that asked for them (singleplayer, LAN and dedicated servers behave the same). On a pure client (a server without this mod) it falls back to a local approximation: each villager is linked to the nearest bed and the nearest workstation matching its profession, so it still renders. Ctrl+O is the master switch, Ctrl+V opens the render settings.");
        rule(m, "giantOldAi", "Giant old AI (14w50a)",
                "Gives giants the old 14w50a behaviour where they actually move and attack: melee attacks, random strolling, looking at players, retaliating when hit, and actively targeting players, villagers and iron golems. A giant that kills a villager infects it into a zombie villager exactly like a vanilla zombie: this only happens on Normal and Hard (Hard always, Normal with a 50% chance), and the villager's profession data, trades, experience and gossip are carried over. It applies to giants that already exist (when the rule is switched on), to newly summoned ones and to every giant loaded from a chunk. Their vanilla attributes are kept (100 health, 0.5 speed, 50 attack damage). Off = vanilla, giants stand still.");
        rule(m, "worldEaterHelper", "World eater mine helper",
                "Highlights the blocks a world eater cannot blast away and that you have to mine by hand: immovable blocks (obsidian, bedrock, enchanting table, respawn anchor and the like), ores and ancient debris, amethyst clusters and budding amethyst, kelp, plus the waterlogged blocks on the list (coral, brick walls, copper and iron chains and bars, candles, glow lichen, rails, pointed dripstone and sulfur spikes, lanterns, copper grates/trapdoors/stairs and the rest). Each one gets a full block-sized (1x1x1) translucent ghost block seven blocks above it (coloured by category) with a thin line back to the real block, so you can see from above what is left. A waterlogged block also gets a translucent water shell wrapped around its model, so the water clearly encloses the inner block. Scanning follows the client's render distance (up to 16 chunks) and only walks loaded chunks, done a few chunks per tick, so anything inside the loaded range is drawn. A block is still marked when the machine or other blocks completely cover it (toggle 'show covered blocks' in the config); when more blocks qualify than the draw limit, the ones nearest the player win. A height limit is on by default: a ghost whose lifted position is 8 or more blocks above its real block is not drawn (the number is editable in the config screen, and the whole limit can be switched off). Ctrl+E opens the block list config, Ctrl+O is the master switch, Ctrl+V opens the render settings.");
        rule(m, "explosionVisualizer", "Explosion visualizer",
                "Port of lucidity's explosion visualizer, with every lucidity display option and one extra. Sources: primed TNT, swelling creepers, end crystals, primed TNT minecarts, flying wither skulls, the wither while invulnerable, ghast fireballs, wind charges (breeze and player), charged respawn anchors in dimensions where they do not work, and beds in dimensions where the bed rule explodes. Every part can be toggled separately in Ctrl+V -> Configure: block destruction (orange translucent blocks, more solid closer to the centre), block ray cast (blue sample points along the blast rays), explosion centre, entity sample points (green safe / red exposed), sample-point ray cast (a line from each sample point to where it hits or to the centre), and the explosion timer (the fuse countdown in seconds above the source). This mod also adds a 'range sphere' toggle - the translucent light green sphere around the blast, radius equal to the furthest reach of the blast. Ctrl+O is the master switch, Ctrl+V opens the render settings and the Configure button.");
        rule(m, "randomWalkVisualizer", "Spawn walk visualizer",
                "Independent rule that draws the spawner's real random walk. The server reports, for each successful natural spawn, the complete walk the spawner took - the start point and every random offset it distance-checked, failed ones included - and the client connects them into a magenta polyline with the hit point highlighted white. When the server does not have this mod (or there is no data yet), the client instead simulates a walk with exactly the same vanilla offset rule (x and z each step by nextInt(6) - nextInt(6)) starting at the player, so a pure client still renders it. In Ctrl+V -> Configure you can toggle the server path and the simulation separately and set the colours. While /tick freeze or /tick step is active the rendering pauses. Ctrl+O is the master switch, Ctrl+V opens the render settings.");
        rule(m, "itemMergeRangeVisualizer", "Item merge range visualizer",
                "Port of lucidity's item merge range. Draws a translucent yellow box around every dropped item, exactly the area vanilla ItemEntity#mergeWithNeighbours checks each tick (the item's bounding box grown by 0.5 blocks horizontally), so with a lot of drops you can see at a glance which ones are about to be pulled together. Everything is computed and drawn on the client from synced item entities, so the server does nothing; only items within 32 blocks are scanned and at most 256 boxes are drawn (nearest first). Ctrl+O is the master switch, Ctrl+V opens the render settings.");
        rule(m, "b36TargetPreview", "B36 destination preview",
                "Port of lucidity's b36 preview. While a piston pushes a block, a translucent ghost of that block is drawn in the cell it is heading for, using the block's own model and lighting with every vertex alpha fixed at 100, so you can see where it will land while the real block is still travelling. Ctrl+O is the master switch for all visualizations, Ctrl+V opens the render settings.");
        rule(m, "projectileTrajectoryVisualizer", "Projectile trajectory visualizer",
                "Draws the real path of every throwable/projectile entity - arrows, spectral arrows, tridents, snowballs, eggs, ender pearls, splash and lingering potions, experience bottles, fireballs and wind charges, firework rockets, eyes of ender, fishing hooks, llama spit, shulker bullets and the rest of the Projectile family. The client samples each projectile's position every tick, connects the trail into a polyline with a green start dot and an amber head point plus a direction arrow, and clears the whole trail about five seconds (100 ticks) after the projectile lands, settles or disappears. It also draws trajectory previews as dashed lines: while you hold a throwable (snowball, egg, ender pearl, experience bottle, splash or lingering potion, wind charge, bow, crossbow or trident) it shows where the shot would land, for a flying projectile it shows the part of the path it has not travelled yet, and for a hovering FarPearl it shows the landing point using the speed the server has accumulated for it. Previews reuse the same gravity/drag the velocity visualizer draws, so both agree on a given projectile; only block collisions are predicted (entity hits are skipped because their outcome differs too much). The held and in-flight previews are pure client work, so they also work on a server without this mod; only the FarPearl preview needs the server side of this mod to send the accumulated speed. Everything else stays on the client: it only tracks entities within 128 blocks that the client can actually see, and caps the points per trail, trails on screen, previews per tick and primitives per frame, so it adds no server load. The per-player render list is stored the same way as the other visualizers and can be set from the server side with /sogcarpet visualizer. Ctrl+O is the master switch, Ctrl+V opens the render settings and the toggle for each preview.");
        rule(m, "motionVisualizer", "Velocity / momentum / acceleration visualizer",
                "Select an entity by right-clicking it with a wooden sword (with Tweakeroo's freecam you can aim anywhere and pick an entity beyond normal reach, as long as it can receive velocity). A coordinate frame is built around it: each axis shows that axis's velocity, momentum and acceleration, and a separate arrow from the frame centre points along the resultant. Momentum is velocity times a mass estimated from the entity's bounding box; acceleration is the per-tick change of velocity. You can also show the externally applied impulse (from addDeltaMovement, pushes and explosions). Open the render settings with Ctrl+V and press Configure on this rule to switch between velocity and momentum, and to toggle the axes, the per-axis vectors, the external vector and the resultant. Everything is computed and drawn on the client; the server only syncs the rule switch. Ctrl+O is the master switch.");
        rule(m, "aggroVisualizer", "Aggro visualizer",
                "Draws hostility relationships the way you would want to read them instantly. A creature that some mob has picked as its attack target gets a red targeting ring above its head; the mob that is trying to attack gets a red exclamation mark above its head; a red line connects the two. Every mob takes part: players only ever appear as the locked-on target (the ring), while other creatures can carry both marks at once when they are attacker and target at the same time. The Warden has a third, yellow question mark state: when it has not locked onto anything but is drawn to a sound, it shows a yellow '?' and a yellow line to the sound source (a mob, a sound-emitting block, a thrown projectile, and so on). The server samples each nearby mob's target (and the Warden's disturbance location) every few ticks and sends a tiny snapshot only to nearby players that asked for it; the client just draws labels and lines from entity ids. Ctrl+O is the master switch, Ctrl+V opens the render settings.");
        rule(m, "aggroRangeVisualizer", "Targeting range visualizer",
                "Draws a red translucent sphere at the outer edge of the targeting range of every hostile mob near you, including the ghast (follow range 100) and command-only hostiles such as the killer bunny, illusioner and giant. The radius is the mob's follow_range attribute multiplied by how visible the local player is to it, so sneaking shrinks it to 0.8x and wearing the matching mob head (skeleton skull, zombie head, piglin head) shrinks it to 0.5x - the sphere is exactly how far that mob can spot you right now. Two modes (the mode button in the config screen): 'all' draws every hostile mob, while 'select' draws only the one entity you pick by right-clicking it with a stone sword (the same selection as the velocity visualizer). Press Configure in Ctrl+V to open a searchable list where every hostile mob (all MONSTER-category types plus the killer bunny) can be toggled on or off individually; all are on by default and the choice is stored per player. Everything is computed and drawn on the client from synced attributes, so the server does nothing. In all mode only mobs within 48 blocks are scanned and at most 12 spheres are drawn (nearest first), each a low-resolution shell with back faces only; the ender dragon and wither are skipped. Ctrl+O is the master switch, Ctrl+V opens the render settings.");
        rule(m, "fakePlayerRenderSettings", "Fake player render settings",
                "Adds per-fake-player render distance and simulation distance sliders to the GCA fake player backpack screen. This is the only thing that follows the player: a newly summoned fake aligns to the player's settings once at summon time. There is no follow option any more (right-click no longer exists) - drag a slider to set that fake's own value, and each fake is stored separately. Turning the rule off freezes the settings at that moment, and turning it back on does not overwrite them.");
        rule(m, "flexiblePlacementCorners", "Tweakeroo flexible placement corner triangles",
                "Adds a triangle to each of the four corners of Tweakeroo's flexible block placement overlay. While the flexible placement offset hotkey is held, aiming at a corner triangle places the block diagonally above or below that spot: the block is offset by one block along both axes of the face at once (on a wall that means diagonally up or diagonally down). The overlay is redrawn by this mod so the aimed corner is highlighted like the other regions, and Tweakeroo's offset step itself is extended on the corner. Needs Tweakeroo on the client; without it the rule does nothing.");
        rule(m, "visualFeedback", "Rule change feedback",
                "Show a chat line, play a soft click and spawn a few themed particles at the player's feet whenever a SOG rule is changed.");
        rule(m, "pearlTeleport", "Simple long-range pearl teleport",
                "Rename an ender pearl (or a snowball, which is only for testing before a real teleport) to FarPearl (Chinese: 远传珍珠) and throw it: instead of falling it hovers where it was thrown. Now hold a paper named with a plain number (positive or negative) in your off hand and a wooden shovel (X axis), stone shovel (Y axis) or iron shovel (Z axis) in your main hand, then attack the hovering pearl once: that number is added as speed along the matching axis (the unit is blocks per tick, so it is easy to read; negative values push the other way). Attack again with another shovel / paper value to build up the vector. Finally right-click the pearl with an empty main hand and it flies off at the accumulated speed, teleporting its owner on landing exactly like a vanilla ender pearl. The client also needs this mod, because a vanilla client cannot target a thrown projectile with a melee attack. Empty-hand right-clicks and ordinary attacks never change the accumulated speed, and every claim in this mod can be turned off safely. The rule runs on the server, so it works on dedicated servers too.");
        rule(m, "sweepRangeVisualizer", "Sweeping attack range visualizer",
                "While a player is holding a sword and aiming at an entity, draws the two areas the vanilla sweeping attack really checks: the attacked entity's bounding box inflated by 1 block horizontally and 0.25 blocks vertically (outline plus translucent faces), and the 3 block reach sphere centred on the attacker's feet. Two modes (Ctrl+V -> Configure): the default one only draws for a sword-holding player aiming at an entity, while the 'always render' mode ignores both conditions - your own reach sphere is always drawn and the aimed entity's box is drawn whenever you point at one. Everything is computed and drawn on the client every frame, so the server does nothing. Ctrl+O is the master switch.");
        rule(m, "dropCountVisualizer", "Dropped item count labels",
                "Draws the item name and the merged stack count above dropped items, as a name tag in world space. Drops of the same item whose boxes touch once grown by 0.5 blocks horizontally (exactly the ones vanilla would merge) share one label with their counts added up, and overlapping labels stack upward automatically. Everything is computed and drawn on the client from synced item entities, so the server does nothing. Ctrl+O is the master switch, Ctrl+V opens the render settings.");
        rule(m, "itemRetentionVisualizer", "Item retention time visualizer",
                "Draws how long a dropped item will still stay before it despawns (vanilla clears a drop once age reaches 6000 ticks, i.e. 5 minutes). The age is incremented on the client every tick too (only the actual removal is server-side), so this works on a pure client as well as on a server running this mod, with no server data needed. Two formats, switched in Ctrl+V -> Configure: sexagesimal x:xx (minutes:seconds, the default) or game ticks xgt. By default it links with the drop-count labels, so the remaining time is appended right after 'name xCount'; it can also run as its own independent rule (each drop then gets a time-only label). A drop that never despawns (age == -32768) shows the infinity symbol. Everything is computed and drawn on the client: only drops within 32 blocks that pass frustum culling are scanned, at most 256 labels are drawn (nearest first) and overlapping labels stack upward, so it stays light. Ctrl+O is the master switch, Ctrl+V opens the render settings.");
        rule(m, "healthChangeVisualizer", "Health change visualizer",
                "Every time an entity's health changes a number pops out above it, floats about one block upwards and fades away after three seconds (60 ticks): taking damage shows a red number with a minus sign (for example -6) and healing shows a green number with a plus sign (for example +4). With this mod on the server the numbers are the real ones - damage is the amount that actually went into its health (armour, enchantments, a blocking shield and absorption are all already accounted for, captured through Fabric's AFTER_DAMAGE event), and healing is the health difference across LivingEntity#heal, the single funnel every healing path goes through (instant health, regeneration, eating and natural regeneration, golden apples and modded healing alike). Events are sent only to nearby players that asked for them. When the server does not have the mod (a pure client) the client falls back to comparing the health it already has synced, so both damage and healing still render. Both colours and the pure-client estimate can be changed in Ctrl+V -> Configure. Ctrl+O is the master switch.");
        rule(m, "illegalBlockVisualizer", "Illegal block visualizer",
                "Renders the blocks you normally cannot see as whole blocks, without having to hold anything: barriers, structure voids and light blocks, plus an optional group of other technical blocks (moving piston, piston head, end portal, end gateway). The look is close to holding those blocks in Bedrock Edition: every block becomes a full-block translucent cube with an outline in its own colour (the block's map colour when it has one, otherwise that block's signature colour - red for barriers, grey-green for structure voids, warm yellow for light blocks, white for the other technical blocks; a map colour that is too dark to see counts as no colour as well), and a light block also shows its light level (0-15) in the middle of the block. Scanning follows the client's render distance and only walks loaded chunks, all of it on the client, so the server does nothing; every group can be switched on or off in Ctrl+V -> Configure. Ctrl+O is the master switch.");
        rule(m, "itemFlowTracker", "Item flow tracker",
                "Mark the item in your hand (or a dropped item) from the item flow panel in Ctrl+V to give it a colour mark. Wherever a marked item flows - into a hopper, a chest, a player inventory and so on - a glowing display entity is spawned on the server to highlight that container, so even a vanilla client without this mod can see it. An optional 'record path' toggle records the item's movement path. Everything runs on the server; turning the rule off clears every highlight immediately. Dedicated servers work the same way.");
        return m;
    }

    private static Map<String, String> zhTranslations() {
        Map<String, String> m = new HashMap<>();
        m.put("carpet.category.SOG", "SOG 附属");
        m.put("sog_carpet.category.SOG", "SOG 附属");
        rule(m, "copperOxidationDisabled", "禁铜自然生锈",
                "未涂蜡的铜块、切制铜、铜台阶等不再因随机刻进入下一氧化阶段；涂蜡与斧头除锈保持原版。");
        rule(m, "dispenserCopperScrape", "发射器可除锈/除蜡",
                "发射器内装有斧头（任意材质，按 #minecraft:axes 标签识别，模组斧头也算）时，发射器被触发后就会像玩家用斧头一样处理正前方对准的铜类方块：涂蜡的除蜡，未涂蜡但已氧化的降一级氧化（氧化→风化→斑驳→未氧化），音效与粒子与原版斧头一致。每成功处理一次，斧头消耗 1 点耐久（与玩家用斧头走同一套原版结算路径，耐久附魔照常生效，耐久耗尽时斧头照常损坏消失）；正前方没有可处理的铜类方块时不消耗耐久、不改变方块，斧头留在发射器里。规则由服务端生效，专用服务器同样可用。");
        rule(m, "dispenserTilling", "发射器可耕地",
                "发射器内装有任意材质的锄头（原版 HoeItem 及其子类，含沿用原版耕作逻辑的模组锄头）时，发射器被触发后就会像玩家用锄头一样处理正前方对准的方块：泥土、草方块与土径耕成耕地，粗泥耕成泥土，缠根泥土耕成泥土并掉落 1 个垂根，音效与游戏事件与原版锄头一致（含“目标上方需要为空”等原版限制）。每成功耕地一次，锄头消耗 1 点耐久（与玩家用锄头一致，耐久耗尽时锄头照常损坏消失）；正前方没有可耕的方块时不消耗耐久、不改变方块，锄头留在发射器里。规则由服务端生效，专用服务器同样可用。");
        rule(m, "dispenserPlanting", "发射器可种植作物",
                "发射器内装有作物物品（方块标签 #minecraft:crops 里的作物：小麦种子、甜菜种子、胡萝卜、土豆、西瓜/南瓜种子、火把花种子、瓶子草荚果；模组作物加入该标签后同样可用）时，发射器被触发后把它种到正前方：对着耕地，就种在耕地上方那一格；对着耕地正上方的空位，就直接种在该空位。装有紫颂花时，对着末地石（或末地石正上方的空位）把紫颂花种到末地石上。种植完全走原版放置流程：位置是否可替换、光照是否足够、能否成活、有没有实体挡路都与玩家放置一致，种下后消耗 1 个物品并播放原版放置音效；不满足条件时不消耗、不改变方块。规则由服务端生效，专用服务器同样可用。");
        rule(m, "dispenserBerryHarvest", "发射器可采集浆果",
                "发射器内装有剪刀（任意剪刀，含模组剪刀）时，发射器被触发后会采集正前方对准的成熟甜浆果丛（age>1）或长有发光浆果的洞穴藤蔓：甜浆果丛按原版掉落表掉落甜浆果并回到刚采摘过的状态（age=1，之后重新结果）；洞穴藤蔓掉 1 个发光浆果并转为无浆果。音效、掉落与游戏事件与原版手动采摘完全一致。每成功采集一次，剪刀消耗 1 点耐久（与玩家用剪刀一样；“剪刀不消耗耐久”规则开启时则不损耗）；正前方没有可采集的浆果时不消耗耐久，并退回原版剪刀发射器行为（蜂巢取蜜、剪实体等照旧）。规则由服务端生效，专用服务器同样可用。");
        rule(m, "chorusFlowerBonemeal", "紫颂花可催熟",
                "骨粉可以催熟紫颂花与紫颂植株（玩家右键或用发射器都行）：每次成功使用让最近一朵还能生长的紫颂花执行一次与原版随机刻完全相同的生长判定——能向上就整株向上续一格（原位置变紫颂植株、上方长出一朵同龄新花）；不能向上就按原版在侧面随机分叉出一朵或多朵大一岁的新花；真的无处可长时按原版把花变成枯萎的死花。因此催熟总会像自然生长那样收束：花迟早会枯萎，枯萎后无法再被催熟（唯一“什么都不发生”的情形是花的上方被占住，此时骨粉不消耗）。规则关闭时与原版完全一致（骨粉对紫颂无效）。规则由服务端生效，专用服务器同样可用；客户端会同步开关用于骨粉右键预测。");
        rule(m, "amethystBudGrowthLimit", "紫水晶芽生长限制",
                "把紫水晶的生长卡在选定阶段：处在选定阶段的芽不再继续升级，比它更小的芽仍会长上来（选 medium 时小芽仍会变成中芽），已经存在的更大芽也仍按原版继续生长。small=小芽停止生长、medium=中芽停止生长、large=大芽停止生长（不再长成晶簇）、cluster=晶簇本身已是终点（等同不限制）、off=原版（芽可一直长到晶簇）。");
        rule(m, "iceSnowMeltDisabled", "禁冰融雪",
                "冰、雪层与霜冰不再因随机刻或计划刻融化；已有的冰与雪原样保留。");
        rule(m, "forceThawLightLevel", "强制解冻亮度阈值",
                "冰的方块亮度达到该值（0-15）时，其随机刻立即变水；12 即原版融冰亮度。");
        rule(m, "iceInstantMelt", "冰即刻融化",
                "达到融化亮度的冰在放置或邻居方块变化时立即融化，无需等待随机刻。");
        rule(m, "snowGenerationDisabled", "禁雪生成",
                "寒冷群系与雷暴不再形成新的雪层；已有雪保留，水结冰保持原版。");
        rule(m, "snowGolemSnowDisabled", "禁雪傀儡产雪",
                "雪傀儡移动时不再在脚下放置雪。");
        rule(m, "thunderDisabled", "禁打雷",
                "雷雨期间不再生成闪电和落雷；降雨与雷雨天气本身不受影响。");
        rule(m, "chickenJockeyDisabled", "禁鸡骑士",
                "幼年的僵尸、溺尸、尸壳、僵尸村民、僵尸猪灵不再骑鸡，因此这五种生物都不会再生成鸡骑士变种；已经存在的鸡骑士保留，这些生物的其他行为一律保持原版。");
        rule(m, "silkTouchReinforcedDeepslate", "精准采集强化深板岩",
                "用附有精准采集的镐挖强化深板岩，掉落方块本体。");
        rule(m, "silkTouchSuspicious", "精准采集可疑的沙子/沙砾",
                "用附有精准采集的锹挖可疑的沙子或可疑的沙砾，掉落方块本体。");
        rule(m, "sculkShriekerLevel", "幽匿尖啸等级可调",
                "0 为原版；1-4 时，尖啸器每次成功尖啸更新等级后直接置为该值（4 即立即满足召唤坚守者的等级）。");
        rule(m, "sculkShriekerLevelLocked", "幽匿尖啸等级锁定",
                "把尖啸器的等级冻结在当前值：不升级，也不在尖啸时重置。");
        rule(m, "wardenSpawnDisabled", "禁止坚守者生成",
                "幽匿尖啸体不再能召唤坚守者；刷怪蛋与指令生成不受影响。");
        rule(m, "nitwitSpawnDisabled", "禁止傻子村民生成",
                "新诞生的村民不再以傻子（nitwit）职业出现，改为无业，之后仍可正常认领工作站点。");
        rule(m, "nitwitBedDisabled", "禁止傻子村民占用床",
                "傻子村民不会认领床，也不会躺上去，床留给其他村民用；规则开启时已经躺在床上的傻子会被立刻踢下床。");
        rule(m, "tintedGlassEasyCraft", "简易合成遮光玻璃",
                "加入合成配方：工作台 3x3，中心 1 个玻璃、上下左右各 1 个黑色染料（共 4 个），产出 1 个遮光玻璃。");
        rule(m, "easyElytraCraft", "简易合成鞘翅",
                "加入合成配方：工作台 3x3，6 个幻翼膜与 3 个线（每行“幻翼膜-线-幻翼膜”）合成 1 个鞘翅。");
        rule(m, "customBeaconEffects", "自定义信标效果",
                "只要信标有 1 层，主效果按钮那行右侧的空白处就会多出一个效果槽；满级（4 层）信标在副效果行下方再多一个槽位。点槽位会打开一个可搜索的界面，列出游戏内全部效果：选中后图标会填进该槽位，再点原版确认按钮即可生效。等级、持续时间与刷新节奏全部沿用原版公式（4 层且主副选同一个效果时仍是原版 II 级）。规则开启时信标的支付格也接受下界之星（图标排在钻石后面；把星放进槽里需要客户端也装本模组，服务端一侧已经接好）；用下界之星开启的信标，其主/副效果会在原有等级上再 +1 级，标记写进信标 NBT，重进世界或重启服务器都不会丢；之后用普通支付物重开信标会去掉这 +1 级。关闭规则后额外槽位消失，用了自定义效果或下界之星的信标会失效（效果清空），变回原版的空信标。");
        rule(m, "beaconEffectFilterMode", "Beacon effect filter mode",
                "Chooses which of the two independent lists is active. blacklist (default) = every effect in the blacklist is blocked and all other effects stay selectable. whitelist = only the effects in the whitelist are selectable; an empty whitelist falls back to the six effects a vanilla beacon can give (speed, haste, resistance, jump boost, strength, regeneration) so the feature cannot lock itself out. The two lists never overwrite each other: switching the mode only changes which one is in effect. The lists apply to the custom effect slots of a beacon block only - the portable beacon is not affected. Operators change them with /carpet, /sogcarpet beaconfilter, or by right-clicking an effect in the picker (that edits the list of the current mode).");
        rule(m, "beaconEffectBlacklist", "Beacon effect blacklist",
                "Comma separated effect ids (for example minecraft:night_vision,minecraft:bad_omen) that cannot be picked in the custom effect slots of a beacon block while beaconEffectFilterMode is blacklist. An empty list blocks nothing. Independent from beaconEffectWhitelist. Duplicates, spaces and unknown ids are cleaned up automatically; the list is persisted in carpet.conf and synced to every client that has this mod. The portable beacon ignores it.");
        rule(m, "beaconEffectWhitelist", "Beacon effect whitelist",
                "Comma separated effect ids that are the only ones pickable in the custom effect slots of a beacon block while beaconEffectFilterMode is whitelist; an empty whitelist falls back to the six effects a vanilla beacon can give. Independent from beaconEffectBlacklist. Duplicates, spaces and unknown ids are cleaned up automatically; the list is persisted in carpet.conf and synced to every client that has this mod. The portable beacon ignores it.");
        rule(m, "portableBeacon", "随身信标",
                "只要背包里（含快捷栏、副手与装备槽）带着信标，手持信标按 Ctrl+右键即可打开效果选择界面。这次右键在客户端就被拦下，原版看不到它，所以信标绝不会被当成方块放出去；选中的效果由服务端校验后直接发给玩家。最多可选两个效果：选两个各 I 级，只选一个为 II 级。时长跟着信标走——只要信标还在背包里，服务端每 4 秒续期一次，效果一直存在；信标一旦离开背包（丢弃、放进箱子、被拿走、死亡掉落）效果立刻消失。可选效果与“自定义信标效果”联动：规则开启时是全部效果，关闭时只给原版信标能给的六种。黑/白名单不影响随身信标。信标不会被消耗。服务端发放效果，专用服务器同样可用；没装客户端模组的玩家可以用 /sogcarpet portablebeacon <效果> [第二个] 代替界面。");
        rule(m, "lightningTurnsClay", "雷击粘土转化",
                "每一次真正的落雷（雷暴、三叉戟引雷等）都会把雷击影响范围内的粘土变成陶瓦。范围与原版闪电的判定一致：水平 ±3 格、落雷点下方 3 格到上方 6 格（7×10×7 的方块区域），正好是“雷击范围可视化”画出来的那一块。只播放视觉效果（visualOnly）的闪电不改变世界。规则由服务端生效，单机、局域网与专用服务器表现一致。");
        rule(m, "lightningRangeVisualizer", "雷击范围可视化",
                "落雷时在落雷点画出半透明的影响范围（水平 ±3 格、向下 3 格到向上 6 格，与原版闪电判定范围完全一致），5 秒后淡出消失。附近的避雷针会一直显示：本体用方框框住，用半径 128 格的水平圆环画出它的引雷范围（原版就是把 128 格内最近的避雷针作为落雷点），并画出“落在该避雷针上时会被影响/转化”的那块区域。数据全部来自客户端已有的闪电实体与方块，服务端不需要下发任何东西。Ctrl+O 是总开关，Ctrl+V 打开渲染设置。");
        rule(m, "snowCraftable", "雪可合成",
                "加入无序合成配方：1 个雪块合成 8 个雪（雪层），正好铺满一整个雪块。");
        rule(m, "shearsNoDurability", "剪刀不消耗耐久",
                "剪刀做任何事都不掉耐久：剪羊毛、取蜂巢蜜、挖树叶、剪蘑菇等全部保留原版行为，只是工具不再损耗。");
        rule(m, "elytraNoDurability", "鞘翅不消耗耐久",
                "鞘翅滑翔、烟花推进等引起的耐久损耗一律不再发生；鞘翅飞行的其它行为保持原版。");
        rule(m, "woodStrippingRecipe", "木头去皮配方",
                "为每一种可去皮的木头解锁工作台配方：把原木、木头、菌柄、菌核或竹块放在任意种类斧头（#minecraft:axes 标签）正上方，即可合成对应的去皮木头。合成后斧头会原样放回配方栏、既不消耗也不掉耐久，一把斧头就能一直去皮。");
        rule(m, "fakePlayerToolsOnly", "假人只拾取工具",
                "假人只捡起工具类物品（剑、斧、镐、铲、锄、弓、弩、三叉戟、重锤、钓鱼竿、剪刀、打火石）；护甲与普通物品留在原地不被拾取。开启规则后，GCA 假人背包界面右侧会出现一列工具开关，可逐类决定这个假人捡哪几类工具，每个假人独立保存；没设置过的假人默认全部捡。");
        rule(m, "sculkRangeVisualizer", "幽匿检测范围可视化",
                "为每个幽匿感测体/校频感测体画出实心半透明检测球（默认淡蓝）与节点之间的传播连线，并在每个节点方块上罩一个半透明实心立方体（颜色比球浓一档）。感测体之间连绿线；连到可生成坚守者的尖啸体时线为红色、球淡红、立方体红，这条线被羊毛挡住则线转黄、球淡黄、立方体黄；连到不能生成坚守者的尖啸体时线为白色、球浅白、立方体白，被挡住则感测体与连线转灰、球浅灰（该尖啸体方块保持白色立方体）。同一个感测体同时连到两类尖啸体时，球与立方体按能生成坚守者的一侧渲染，但每条连线仍用自己该有的颜色（接到不能生成的那条不会跟着变红）。只有羊毛算阻挡。坚守者本身也是一个振动监听者，按同一套风格单独渲染：用一个半透明青色的检测球标出它 16 格的监测范围、在它所在方块罩一个青色立方体，并把它和 16 格内每个幽匿感测体（检测体）用青线连起来（不连尖啸体）。与坚守者相连的感测体整体改用坚守者那支青色：检测球与框都是青色（优先于尖啸体链，优先级为 坚守者 &gt; 能召唤的尖啸体 &gt; 不能召唤的尖啸体 &gt; 纯感测体）。与尖啸体链一样，被羊毛挡住的坚守者连线也变黄，坚守者自己的连线全被挡住时它的球与框同样变黄。方块上方标一个橙色标准警告符号 ⚠，检测球面上密集、均匀地铺满橙色 ⚠ 警告标志，坚守者的检测球也同样铺满；每当感测体或坚守者真正收到振动时，会用一条亮品红的触发判定线把声音源（生物／发声方块／投掷物）与它连起来，约 1 秒后淡出。Ctrl+O 切换显示，按键可在控制设置里修改。");
        rule(m, "enderDragonPathVisualizer", "末影龙寻路可视化",
                "按 lucidity 的做法画出末影龙的 24 个寻路节点：每个节点是一个整块大小的半透明盒子，半径 40 与 20 的中内圈用寻路点本色（255,0,118），半径 60 的外圈用它的反色（0,255,137）；龙最近一段飞行轨迹连成同色折线、当前位置标成黄点。只在末地绘制，节点位置与原版布局逐格一致。Ctrl+O 是全部可视化的总开关，Ctrl+V 打开渲染设置。");
        rule(m, "witherBreakRangeVisualizer", "凋零破坏范围可视化",
                "按 lucidity 的做法用半透明红色实心立方体标出凋灵会打掉方块的范围：水平 ±floor(包围盒宽/2+1) 格、从脚下起 floor(包围盒高) 格，与原版循环完全一致；只画这一个范围盒子，不再逐格描边。Ctrl+O 是全部可视化的总开关，Ctrl+V 打开渲染设置。");
        rule(m, "enderDragonBreakRangeVisualizer", "末影龙破坏范围可视化",
                "按 lucidity 的做法，把原版真正用来拆方块的头、脖子、身体三个部位的包围盒对齐到整数方块后，用同一种紫色（137,101,255）半透明实心盒子画出来；不再画整体白框与逐格红边。Ctrl+O 是全部可视化的总开关，Ctrl+V 打开渲染设置。");
        rule(m, "mobSpawnVisualizer", "怪物游走路径可视化",
                "把一只生物刷出来之后真实走过的路线画成轨迹，轨迹末端始终连着它，怪走到哪线就跟到哪。服务端只在自然刷怪真正把生物放进世界那一刻上报一条出生事件（生物实体号＋出生坐标），之后由客户端盯住这只生物逐 tick 采样，所以画出来的是它自己走的路，而不是刷怪器“挑位置”的游走；玩家附近 128 格内每一只活着的生物都会有轨迹。轨迹起点是亮绿色粗点，行走过程线是近白折线并叠黄色方向箭头，终点是沿生物最后移动方向的红箭头；生物死亡或走远后轨迹淡出。采样与绘制全在客户端。刷怪器的游走路径、Y 轴生成高度柱、成功率统计都归独立的“怪物生成可视化（spawnStatsVisualizer）”规则。Ctrl+O 切换显示，按键可在控制设置里修改。");
        rule(m, "spawnStatsVisualizer", "怪物生成可视化",
                "把整个自然刷怪过程可视化。世界内：对每次成功刷怪画刷怪器完整的随机游走路径（每次随机偏移都做距离检查，含失败的偏移）品红折线、命中点高亮白色；在起始点画一根 Y 轴生成高度柱——原版 getRandomPosWithin 取起始 Y 的 [minY, 地表+1] 可选范围；并在最新出生点旁标出成功率。统计：按 MobCategory 统计“成功/尝试”，并按刷怪算法的每个检查环节（红石导体、距离过近、生物存在、位置、生成势、距离过远/障碍）统计“通过/到达”；约每秒写进玩家的 Tab 列表页脚——类似 carpet 的 TPS/怪物数量统计：长按 Tab 与在线玩家列表一起显示，用的是原版 Tab 数据包，连没装本模组的纯客户端也能看到。在 Ctrl+V →“配置”里可整体开关统计面板，并逐项开关每个分类、每个检查环节的统计（选择同步到服务端）；游走路径、高度柱的开关与颜色也在那里。/tick freeze、/tick step 时世界内渲染暂停。Ctrl+O 是总开关，Ctrl+V 打开渲染设置。");
        rule(m, "spawnRangeVisualizer", "可刷怪方块生成范围可视化",
                "把会自己刷怪的方块（可刷怪方块）的生成范围画出来。刷怪笼与试炼刷怪笼用原版偏移（水平 ±刷怪范围、竖直 ±1 格），也就是方块周围的 9×3×9 盒子；刷怪笼读取它真实的 SpawnRange（NBT，默认 4），试炼刷怪笼读取其配置的 spawn_range（自定义配置不会同步到客户端、显示为默认 4，原版数据全部等于默认值）。幽匿尖啸体召唤坚守者时在水平 ±5、竖直 ±6 格内找位置。每种范围用一个半透明红色长方体罩住，中心的刷怪方块再用一个黄色线框框住。数据全在客户端读方块实体得到，服务端不需要下发任何数据。Ctrl+O 是总开关，Ctrl+V 打开渲染设置。");
        rule(m, "villagerBindingVisualizer", "村民功能方块绑定可视化",
                "用绿线把每个村民和它认领的床连起来、用绿框框住床；用红线把它和绑定的工作方块连起来、用红框框住工作方块。绑定关系只存在于服务端村民的 Brain 里，所以由服务端采集附近村民的绑定并发给明确要收的客户端（单机、局域网、专用服务器表现一致）。连到没装本模组的服务器（纯客户端）时退回本地近似：把村民连到最近的床，以及与自己职业匹配的最近工作站，保证也能渲染。Ctrl+O 是总开关，Ctrl+V 打开渲染设置。");
        rule(m, "giantOldAi", "巨人旧版 AI（14w50a）",
                "给巨人恢复 14w50a 那套“会移动、会攻击”的行为：近战攻击、随机游走、看向玩家、被打了还手，并主动锁定玩家、村民与铁傀儡。巨人打死村民时会像原版普通僵尸一样把村民感染成僵尸村民：只有普通/困难难度才会发生（困难必定、普通 50% 概率），村民的职业数据、交易、经验与声望一并带过去。对规则开启时已经存在的巨人、之后新召唤的巨人、以及每一个从区块加载出来的巨人都生效；属性沿用原版（生命 100、速度 0.5、攻击 50）。默认关闭＝原版巨人站立不动。");
        rule(m, "worldEaterHelper", "世吞小助手",
                "高亮世界吞噬者炸不掉、需要手动补挖的方块：不可推动的方块（黑曜石、基岩、附魔台、重生锚等）、各类矿石与远古残骸、紫水晶晶簇与母岩、海带，以及清单里的含水方块（珊瑚、砖墙、铜与铁的链/栏杆、蜡烛、发光地衣、铁轨、滴水石锥与硫磺石锥、灯笼、铜格栅/活板门/楼梯等）。每个这样的方块上方 7 格画一个整块大小（1×1×1）的半透明幽灵方块（按类别配色），并用细线连回本体，方便从世吞上方俯视查看还剩什么；含水方块还会在模型外面再套一层半透明水壳，做出“外部的水包裹着内部的方块”的效果。扫描范围跟着客户端渲染距离走（最多 16 个区块）且只扫已加载的区块，每 tick 分几个区块进行，所以只要在加载范围内就会渲染出来。方块被机器或其他方块完全盖住时也照常显示（可在配置界面关掉“显示被覆盖方块”），幽灵原位如果还埋在实心方块里会自动抬到最近的空位、描边与连线始终置顶，保证埋住的方块也看得见；符合条件者超过绘制上限时优先保留离玩家最近的。默认启用高度限制：幽灵被抬到距原方块 8 格及以上时不再渲染（数值可在配置界面手动输入，整条限制也能关掉）。开启规则后 Ctrl+E 打开方块清单配置，Ctrl+O 是全部可视化的总开关，Ctrl+V 打开渲染设置。");
        rule(m, "explosionVisualizer", "爆炸可视化",
                "移植自 lucidity 的爆炸可视化，lucidity 的每个显示项都保留，并额外多一个。爆炸源：点燃的 TNT、膨胀的苦力怕、末地水晶、已点火的 TNT 矿车、飞行的凋灵之首、处于无敌阶段的凋灵、恶魂火球、风弹（旋风人／玩家）、在“重生锚不起作用”的维度里充能过的重生锚，以及床规则判定会爆炸的维度里的床。每一部分都能在 Ctrl+V →“配置”里单独开关：方块破坏（橙色半透明，越靠中心越实）、方块射线投射（爆炸射线上的蓝色采样点）、爆炸中心、实体采样点（安全绿／暴露红）、采样点射线投射（从采样点到命中点或爆心的连线）、爆炸计时器（爆炸源头顶的引信倒计时，单位秒）。本模组还额外提供“渲染圆球”开关——爆炸范围最外围的浅绿色半透明圆球。Ctrl+O 是总开关，Ctrl+V 打开渲染设置并点“配置”进入本界面。");
        rule(m, "randomWalkVisualizer", "刷怪游走可视化",
                "独立规则，画刷怪器真正的随机游走。服务端为每次成功刷怪上报它完整走过的路径——起始点与每一步随机偏移（含距离检查失败的偏移），客户端连成品红折线、命中点高亮白色。对面没装本模组（或暂时没有数据）时，客户端改用完全相同的原版偏移规则（x/z 每步加 nextInt(6) - nextInt(6)）从玩家脚下模拟一条随机游走，于是纯客户端也能渲染。在 Ctrl+V →“配置”里可分别开关服务端路径与模拟路径，并改颜色。/tick freeze、/tick step 时渲染暂停。Ctrl+O 是总开关，Ctrl+V 打开渲染设置。");
        rule(m, "itemMergeRangeVisualizer", "物品合并范围可视化",
                "移植自 lucidity 的物品合并范围。为玩家附近的每个掉落物画一个半透明黄色盒子，范围与原版 ItemEntity#mergeWithNeighbours 每 tick 检查的区域完全一致（物品包围盒在水平方向外扩 0.5 格），物品一多就能一眼看出哪些马上会被吸到一起。全部在客户端根据同步下来的物品实体计算与绘制，服务端零负担；只扫 32 格以内的物品、同屏最多画 256 个盒子（优先保留离玩家最近的）。Ctrl+O 是总开关，Ctrl+V 打开渲染设置。");
        rule(m, "b36TargetPreview", "b36 目的地预览",
                "移植自 lucidity 的 b36 预览。活塞推动方块时，用该方块自己的模型与光照，在它将要到达的那一格再画一份半透明重影（所有顶点的 alpha 固定为 100）；本体还由原版画在路上的插值位置，两者错开，于是提前就能看到它会落到哪一格。Ctrl+O 是全部可视化的总开关，Ctrl+V 打开渲染设置。");
        rule(m, "projectileTrajectoryVisualizer", "投掷物轨迹可视化",
                "把每一个可投掷/投射实体真实走过的路线画成轨迹：箭、光灵箭、三叉戟、雪球、鸡蛋、末影珍珠、喷溅与滞留药水、附魔之瓶、火球与风弹、烟花火箭、末影之眼、鱼钩、羊驼唾沫、潜影贝导弹等，凡属于 Projectile 家族都包含。客户端每 tick 采集它的位置连成折线，起点画绿点、末端画琥珀色位置点和方向箭头；投掷物落地停住或消失后约 5 秒（100 tick）内整条轨迹淡出并清除。同时还会画虚线形式的轨迹预览：手持可投掷物（雪球、鸡蛋、末影珍珠、附魔之瓶、喷溅/滞留药水、风弹、弓、弩、三叉戟）时提前显示“会落到哪”，飞行中的投掷物显示它还没走到的那一段，悬停的远传珍珠则用服务端累积的待施加速度画出落点。预览用的重力与阻力跟速度/动量可视化是同一套参数，所以两处对同一枚投掷物给出的预测一致；只预测方块碰撞，不预测打中实体（箭矢/珍珠命中生物后的行为差异太大）。手持预览与飞行预览全部在客户端计算，所以对面服务器没装本模组也能画；只有悬停珍珠那一路需要服务端发送累积速度。其余与原来一致：只跟踪玩家 128 格以内、本地真正看得见的实体，并对单条轨迹的采样点、同屏轨迹条数、每 tick 预览条数、一帧图元数设上限，对服务器没有负担。逐玩家渲染清单和其它可视化一样保存，也能用 /sogcarpet visualizer 从服务端配置。Ctrl+O 是总开关，Ctrl+V 打开渲染设置并可逐路开关预览。");
        rule(m, "motionVisualizer", "速度/动量/加速度可视化",
                "用木剑右键某个实体即可把它设为观察对象（用 Tweakeroo 的灵魂出窍时，鼠标朝哪就选哪，可以超出正常手长范围；能施加速度的实体都行）。以它为中心建立 X/Y/Z 坐标轴，每个轴上画出该轴的速度、动量与加速度，另有一个从轴心指出的合矢量箭头。动量＝速度×质量（质量按实体包围盒体积估算），加速度＝速度逐 tick 的差值；还能单独显示外部施加的冲量（来自 addDeltaMovement、推动、爆炸等）。Ctrl+V 打开渲染设置后点本规则的“配置”按钮，可切换速度/动量模式，并分别开关坐标轴、每轴矢量、外部矢量、合矢量。全部在客户端计算与绘制，服务端只同步规则开关。Ctrl+O 是总开关。");
        rule(m, "aggroVisualizer", "仇恨可视化",
                "把生物之间的仇恨关系画得一目了然。被某个生物选为攻击目标的一方，头顶画红色瞄准环；正在尝试去攻击的一方，头顶画红色感叹号；再用一条红线把两者连起来。对所有生物生效：玩家只会作为“被锁定的一方”出现（只画瞄准环），其它生物则可能同时是攻击者和目标，于是头顶会同时出现感叹号和瞄准环。坚守者多一个黄色疑问标签：当它没有锁定目标、却被声音吸引时，头顶画黄色问号，并用黄线连到声音源（引起它的生物、发声方块、投掷物等）所在处。服务端每几 tick 采样一次附近生物的锁定目标（以及坚守者的“干扰位置”），只把很小的快照发给附近明确要收的玩家；客户端按实体号还原实体再画标签和连线。Ctrl+O 是总开关，Ctrl+V 打开渲染设置。");
        rule(m, "aggroRangeVisualizer", "索敌范围可视化",
                "为玩家附近的每一只敌对生物，在它索敌范围的最外围画一个红色半透明圆球，包含恶魂（follow_range 100）以及只能靠指令召唤的敌对生物（杀手兔、幻术师、巨人等）。半径取该生物的 follow_range 属性值，再乘上本地玩家对它的“可见度系数”，所以潜行会让球缩到 0.8 倍、戴上与该生物对应的头颅（骷髅头、僵尸头、猪灵头）会缩到 0.5 倍——圆球画的就是“这只怪现在最远能在多远发现你”。两种模式（配置界面里的模式按钮）：全部模式画所有敌对生物；选择模式只画石剑右键选中的那一个实体（与速度可视化同样的选择方式）。在 Ctrl+V 里点“配置”会打开一个可搜索的清单，所有敌对生物（全部 MONSTER 类别生物 + 杀手兔）都能单独开关，默认全部开启，每个玩家各自保存。属性由原版同步到客户端，全部在客户端计算与绘制，服务端零负担。全部模式只扫 48 格以内的生物、同屏最多画 12 个球（优先保留离玩家最近的），每个球都是低分辨率球壳且只画背面；末影龙、凋灵跳过不画。Ctrl+O 是总开关，Ctrl+V 打开渲染设置。");
        rule(m, "fakePlayerRenderSettings", "假人渲染设置可调",
                "在 GCA 假人背包界面左侧加入渲染距离与模拟距离滑条。“跟随玩家”只剩召唤那一次：新召唤的假人在召唤时对齐一次玩家设置。不再提供右键跟随玩家设置的选项，拖滑条就是给这个假人定自己的值，每个假人独立保存；关掉规则时设置停在关闭那一刻，重新开启不会被覆盖。");
        rule(m, "flexiblePlacementCorners", "Tweakeroo 灵活放置四角三角",
                "在 Tweakeroo“灵活方块放置”的“如何放置”界面四个角各加入一个三角形放置区。按住“灵活方块放置 - 偏移位置”快捷键瞄准某个角的三角形时，方块落在该位置的斜上方／斜下方：同时沿该面的两个方向各偏移一格（墙面上就是斜上、斜下）。开启后这个界面由本模组重画，被瞄准的三角形会像其他区域一样高亮；Tweakeroo 那边只改了偏移那一格的计算。只在客户端装了 Tweakeroo 时生效，没装则规则不产生任何行为。");
        rule(m, "visualFeedback", "规则切换反馈",
                "切换 SOG 规则时在聊天栏显示状态、播放轻柔点击音效，并在玩家脚边生成少量对应主题粒子。");
        rule(m, "pearlTeleport", "简易珍珠远距传送",
                "把末影珍珠（或雪球，雪球只用于正式传送前的调试）命名为“远传珍珠”（英文 FarPearl）后投出，它不再下落，而是悬停在抛出点。此时副手拿一张名字是纯数字（可正可负）的纸、主手拿<b>木铲（X 轴）/石铲（Y 轴）/铁铲（Z 轴）</b>，攻击悬停的珍珠一次：纸上的数值就是对应轴的叠加速度（单位取“格/tick”，便于理解；负数反向）。换铲子、换数值多次攻击即可累积矢量。最后主手空手右键珍珠，它就以累积的速度飞出，落地后按原版末影珍珠传送主人。客户端也要装本模组，原版客户端无法用近战选中投掷物；空手右键与普通攻击都不会改变累积速度。规则由服务端生效，专用服务器同样可用。");
        rule(m, "sweepRangeVisualizer", "横扫攻击范围可视化",
                "当玩家手持剑并瞄准某个实体时，把原版横扫攻击真正判定的两个范围画出来：被攻击目标碰撞箱水平外扩 1 格、竖直外扩 0.25 格的方框（线框 + 半透明面），以及以攻击者脚底为球心、半径 3 格的球体。两种模式（Ctrl+V → 配置）：默认模式只在玩家持剑并瞄准实体时绘制；“总是渲染”模式不要求持剑/瞄准，自身的 3 格触及球常显、准星指向实体时再画它的方框。全部在客户端逐帧计算与绘制，服务端零负担。Ctrl+O 是总开关。");
        rule(m, "dropCountVisualizer", "掉落物数量标签",
                "在掉落物上方以名牌的方式画出物品名称与合并后的数量。同一种物品、包围盒水平外扩 0.5 格后会挨在一起的掉落物（正好是原版会合并的那些）共用一个标签并累加数量，重叠的标签自动向上错开。全部在客户端根据同步下来的物品实体计算与绘制，服务端零负担。Ctrl+O 是总开关，颜色在 Ctrl+V 里改。");
        rule(m, "itemRetentionVisualizer", "物品留存时长可视化",
                "在掉落物上方画出它距离自动消失还剩多久（原版掉落物 age 到达 6000 tick、即 5 分钟后清除）。这个 age 客户端本地也会逐 tick 累加（真正的清除才是服务端行为），所以纯客户端与装了本模组的服务端都能算出剩余时长，不需要任何服务端数据。两种制式，在 Ctrl+V →“配置”里切换：六十制 x:xx（分:秒，默认）或游戏刻制 xgt。默认与“掉落物数量标签”联动，剩余时长直接接在“名称 x数量”后面；也可以作为一条独立规则渲染（此时每个掉落物上方只画剩余时长）。永不消失的掉落物（age == -32768）显示 ∞。全部在客户端计算与绘制：只扫 32 格以内、且通过视锥裁剪的掉落物，同屏最多画 256 条（优先保留最近的），重叠的标签自动向上错开，因此不会拖慢客户端。Ctrl+O 是总开关，Ctrl+V 打开渲染设置。");
        rule(m, "healthChangeVisualizer", "生命数值变化可视化",
                "实体生命值变化时，在它头顶浮出一个数字：受伤是红色带负号的数（例如 -6），回血是绿色带正号的数（例如 +4），数字向上飘约一格并逐渐变淡，3 秒（60 tick）后消失。服务端装了本模组时用的是真实数值——受伤取 Fabric 的 AFTER_DAMAGE 事件里真正结算进血量的伤害（护甲、附魔、盾牌格挡、吸收都已经算完），回血取 LivingEntity#heal 前后的血量差（它是所有治疗路径的唯一出口：瞬间治疗、再生、进食与自然回血、金苹果以及沿用原版治疗的模组都走它），只发给附近明确要收的玩家；对面没装本模组（纯客户端）时，客户端退回按已经同步下来的血量变化估算，受伤与回血都能渲染。两种颜色与“纯客户端估算”开关在 Ctrl+V →“配置”里改，Ctrl+O 是总开关。");
        rule(m, "illegalBlockVisualizer", "非法方块可视化",
                "把平时看不见的方块整块渲染出来，不需要手持任何东西：屏障、结构空位、光明方块，另外还有一组可选的其它技术方块（移动的活塞、活塞头、末地传送门、末地折跃门）。观感与基岩版手持这些方块时相近：每个方块画成整块大小的半透明立方体并描边，颜色取方块自身的地图色，没有地图色、或者地图色暗到看不见的用该方块的代表色（屏障红、结构空位灰绿、光明方块暖黄、其它技术方块白）；光明方块还会在方块中央标出它的光照等级（0-15）。扫描跟着客户端渲染距离走、只扫已加载的区块，全部在客户端完成，服务端零负担；每一类都能在 Ctrl+V →“配置”里单独开关，Ctrl+O 是总开关。");
        rule(m, "itemFlowTracker", "物品流向跟踪",
                "在 Ctrl+V 的“物品流向”面板里给手持物品（或指定的掉落物）打上颜色标记后，被标记的物品无论流进哪个容器——漏斗、箱子、玩家背包等等——都会在服务端生成一个发光显示实体把该容器高亮出来，于是连没装本模组的纯客户端也能看到。面板里可开关“记录路径”，按间隔记录物品移动路径。整套机制全部在服务端运行；关闭规则会立即清除所有高亮；专用服务器同样可用。");
        return m;
    }
}
