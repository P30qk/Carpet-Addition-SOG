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
    public static final String VERSION = "1.0.0";
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
            if ("mobSpawnVisualizer".equals(rule.name()) && !SogSettings.mobSpawnVisualizer) {
                SogSpawnTrace.setRecording(false);
                SogSpawnTrace.clear();
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
        return chinese ? zhTranslations() : enTranslations();
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
        rule(m, "beaconExtraEffects", "Beacon extra effects",
                "Adds Saturation and Luck to the beacon's primary powers (same row as Strength) and Fire Resistance to the secondary power (same row as Regeneration). Levels, duration and refresh rate match vanilla. Turning the rule off turns any beacon that used an extra effect back into a beacon without added effects.");
        rule(m, "snowCraftable", "Snow craftable",
                "Adds a shapeless recipe: one snow block yields eight snow (snow layers), exactly one full block's worth.");
        rule(m, "shearsNoDurability", "Shears take no durability",
                "Shears never lose durability: shearing sheep, harvesting honeycombs, mining leaves and all other shear uses leave the tool untouched.");
        rule(m, "fakePlayerToolsOnly", "Fake players only pick up tools",
                "Fake players only pick up tool items (sword, axe, pickaxe, shovel, hoe, bow, crossbow, spear, mace, fishing rod, shears, trident, flint and steel). Armour and ordinary items stay on the ground. While the rule is on, a column of per-category switches appears on the right of the GCA fake inventory and is stored per fake; a fake that was never configured picks up every category.");
        rule(m, "sculkRangeVisualizer", "Sculk detection range visualizer",
                "Draws a translucent solid detection ball (light blue by default) around every sculk or calibrated sculk sensor, plus links between the nodes and a translucent solid cube over each node block (a deeper shade than the ball). Links between sensors are green; links to a shrieker that can summon the warden are red with a light-red ball, turning yellow with a light-yellow ball when that link is blocked by wool; links to a shrieker that cannot summon the warden are white with a light-white ball, turning grey with a light-grey ball when blocked. A sensor linked to both kinds uses the warden-capable side for its ball and cube, while each link still keeps the colour it belongs to. Wool is the only blocker. Toggle with Ctrl+O; the key can be rebound.");
        rule(m, "creakingHeartVisualizer", "Creaking heart visualizer",
                "After you hit a creaking that is bound to a creaking heart, its heart block is outlined in red and a yellow line connects the heart to the creaking. The highlight lasts about five seconds and is refreshed by hitting the creaking again.");
        rule(m, "enderDragonPathVisualizer", "Ender dragon path visualizer",
                "Draws the ender dragon's 24 pathfinding nodes the way lucidity does: each node is a full block-sized translucent box, middle/inner nodes (radius 40 and 20) in the waypoint colour (255,0,118) and outer nodes (radius 60) in its inverse (0,255,137), plus a matching-colour polyline for the recent flight path and a yellow dot for the current position. Only drawn in the End, with the vanilla node layout exactly. Ctrl+O is the master switch for all visualizations, Ctrl+V opens the render settings.");
        rule(m, "witherBreakRangeVisualizer", "Wither block-breaking range visualizer",
                "Draws the area a wither destroys the way lucidity does: one translucent red solid cuboid, floor(bbWidth / 2 + 1) blocks horizontally and floor(bbHeight) blocks up from its feet, exactly like the vanilla loop. Per-block outlines are gone. Ctrl+O is the master switch, Ctrl+V opens the render settings.");
        rule(m, "enderDragonBreakRangeVisualizer", "Ender dragon block-breaking range visualizer",
                "The head, neck and body boxes that vanilla really uses to break blocks are snapped to the block grid and drawn as translucent purple (137,101,255) solids, the way lucidity does. No white frame or per-block outlines. Ctrl+O is the master switch, Ctrl+V opens the render settings.");
        rule(m, "mobSpawnVisualizer", "Mob spawn trace visualizer",
                "Draws the route a mob has really walked since it spawned, so the mob is always at the head of its own trail. The server only reports a spawn event when natural spawning actually puts a mob into the world (its entity id plus the spawn block); the client then follows that mob and samples its position every tick, so the trail is the mob's own path instead of the spawner's old position-picking walk and no longer looks like the mob ignoring its line, and every living mob within 128 blocks of the player gets a trail rather than only the one that spawned most recently. The trail starts with a thick bright green dot, runs as a near-white polyline with yellow direction arrows every few blocks, and ends with a red arrow pointing along the mob's last movement (its facing while standing still); the trail fades out over about two seconds after the mob dies or leaves the range. All the heavy sampling and drawing stays on the client: the server sends a few bytes per spawn, only to players within 192 blocks that asked for it, and stops collecting entirely while no modded player is online, so it adds almost nothing to server load. The Ctrl+V render list is stored per player; on a server it can also be set from the server side with /sogcarpet visualizer, even for a player whose client does not have this mod, and one player's list never affects another's. Toggle with Ctrl+O; the key can be rebound.");
        rule(m, "worldEaterHelper", "World eater mine helper",
                "Highlights the blocks a world eater cannot blast away and that you have to mine by hand: immovable blocks (obsidian, bedrock, enchanting table, respawn anchor and the like), ores and ancient debris, amethyst clusters and budding amethyst, kelp, plus the waterlogged blocks on the list (coral, brick walls, copper and iron chains and bars, candles, glow lichen, rails, pointed dripstone and sulfur spikes, lanterns, copper grates/trapdoors/stairs and the rest). Each one gets a full block-sized (1x1x1) translucent ghost block seven blocks above it (coloured by category) with a thin line back to the real block, so you can see from above what is left. A waterlogged block also gets a translucent water shell wrapped around its model, so the water clearly encloses the inner block. Scanning follows the client's render distance (up to 16 chunks) and only walks loaded chunks, done a few chunks per tick, so anything inside the loaded range is drawn. A block is still marked when the machine or other blocks completely cover it (toggle 'show covered blocks' in the config); when more blocks qualify than the draw limit, the ones nearest the player win. Ctrl+E opens the block list config, Ctrl+O is the master switch, Ctrl+V opens the render settings.");
        rule(m, "explosionVisualizer", "Explosion visualizer",
                "Port of lucidity's explosion visualizer. While a TNT is primed or a creeper is swelling up to explode, a translucent light green sphere is drawn around the blast centre, with a radius equal to the furthest reach of the blast (power x 1.733: about 6.9 blocks for TNT, 5.2 for a creeper, 10.4 for a charged creeper). No blocks, rays or sample points. Ctrl+O is the master switch, Ctrl+V opens the render settings.");
        rule(m, "b36TargetPreview", "B36 destination preview",
                "Port of lucidity's b36 preview. While a piston pushes a block, a translucent ghost of that block is drawn in the cell it is heading for, using the block's own model and lighting with every vertex alpha fixed at 100, so you can see where it will land while the real block is still travelling. Ctrl+O is the master switch for all visualizations, Ctrl+V opens the render settings.");
        rule(m, "fakePlayerRenderSettings", "Fake player render settings",
                "Adds per-fake-player render distance and simulation distance sliders to the GCA fake player backpack screen. This is the only thing that follows the player: a newly summoned fake aligns to the player's settings once at summon time. There is no follow option any more (right-click no longer exists) - drag a slider to set that fake's own value, and each fake is stored separately. Turning the rule off freezes the settings at that moment, and turning it back on does not overwrite them.");
        rule(m, "flexiblePlacementCorners", "Tweakeroo flexible placement corner triangles",
                "Adds a triangle to each of the four corners of Tweakeroo's flexible block placement overlay. While the flexible placement offset hotkey is held, aiming at a corner triangle places the block diagonally above or below that spot: the block is offset by one block along both axes of the face at once (on a wall that means diagonally up or diagonally down). The overlay is redrawn by this mod so the aimed corner is highlighted like the other regions, and Tweakeroo's offset step itself is extended on the corner. Needs Tweakeroo on the client; without it the rule does nothing.");
        rule(m, "visualFeedback", "Rule change feedback",
                "Show a chat line, play a soft click and spawn a few themed particles at the player's feet whenever a SOG rule is changed.");
        return m;
    }

    private static Map<String, String> zhTranslations() {
        Map<String, String> m = new HashMap<>();
        m.put("carpet.category.SOG", "SOG 附属");
        m.put("sog_carpet.category.SOG", "SOG 附属");
        rule(m, "copperOxidationDisabled", "禁铜自然生锈",
                "未涂蜡的铜块、切制铜、铜台阶等不再因随机刻进入下一氧化阶段；涂蜡与斧头除锈保持原版。");
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
        rule(m, "beaconExtraEffects", "信标附加效果",
                "信标主效果新增饱和与幸运（与力量同排），辅助效果新增抗火（与再生同排）；等级、持续时间与刷新频率与原版一致。关闭规则后，已使用附加效果的信标会恢复为无附加效果的信标。");
        rule(m, "snowCraftable", "雪可合成",
                "加入无序合成配方：1 个雪块合成 8 个雪（雪层），正好铺满一整个雪块。");
        rule(m, "shearsNoDurability", "剪刀不消耗耐久",
                "剪刀做任何事都不掉耐久：剪羊毛、取蜂巢蜜、挖树叶、剪蘑菇等全部保留原版行为，只是工具不再损耗。");
        rule(m, "fakePlayerToolsOnly", "假人只拾取工具",
                "假人只捡起工具类物品（剑、斧、镐、铲、锄、弓、弩、矛、重锤、钓鱼竿、剪刀、三叉戟、打火石）；护甲与普通物品留在原地不被拾取。开启规则后，GCA 假人背包界面右侧会出现一列工具开关，可逐类决定这个假人捡哪几类工具，每个假人独立保存；没设置过的假人默认全部捡。");
        rule(m, "sculkRangeVisualizer", "幽匿检测范围可视化",
                "为每个幽匿感测体/校频感测体画出实心半透明检测球（默认淡蓝）与节点之间的传播连线，并在每个节点方块上罩一个半透明实心立方体（颜色比球浓一档）。感测体之间连绿线；连到可生成坚守者的尖啸体时线为红色、球淡红、立方体红，这条线被羊毛挡住则线转黄、球淡黄、立方体黄；连到不能生成坚守者的尖啸体时线为白色、球浅白、立方体白，被挡住则感测体与连线转灰、球浅灰（该尖啸体方块保持白色立方体）。同一个感测体同时连到两类尖啸体时，球与立方体按能生成坚守者的一侧渲染，但每条连线仍用自己该有的颜色（接到不能生成的那条不会跟着变红）。只有羊毛算阻挡。Ctrl+O 切换显示，按键可在控制设置里修改。");
        rule(m, "creakingHeartVisualizer", "嘎吱之心可视化",
                "攻击绑着嘎吱之心的嘎吱怪后，用红框框住它的嘎吱之心，并用黄线把嘎吱之心与嘎吱怪连起来；高亮约保留 5 秒，再打一次会刷新。");
        rule(m, "enderDragonPathVisualizer", "末影龙寻路可视化",
                "按 lucidity 的做法画出末影龙的 24 个寻路节点：每个节点是一个整块大小的半透明盒子，半径 40 与 20 的中内圈用寻路点本色（255,0,118），半径 60 的外圈用它的反色（0,255,137）；龙最近一段飞行轨迹连成同色折线、当前位置标成黄点。只在末地绘制，节点位置与原版布局逐格一致。Ctrl+O 是全部可视化的总开关，Ctrl+V 打开渲染设置。");
        rule(m, "witherBreakRangeVisualizer", "凋零破坏范围可视化",
                "按 lucidity 的做法用半透明红色实心立方体标出凋灵会打掉方块的范围：水平 ±floor(包围盒宽/2+1) 格、从脚下起 floor(包围盒高) 格，与原版循环完全一致；只画这一个范围盒子，不再逐格描边。Ctrl+O 是全部可视化的总开关，Ctrl+V 打开渲染设置。");
        rule(m, "enderDragonBreakRangeVisualizer", "末影龙破坏范围可视化",
                "按 lucidity 的做法，把原版真正用来拆方块的头、脖子、身体三个部位的包围盒对齐到整数方块后，用同一种紫色（137,101,255）半透明实心盒子画出来；不再画整体白框与逐格红边。Ctrl+O 是全部可视化的总开关，Ctrl+V 打开渲染设置。");
        rule(m, "mobSpawnVisualizer", "刷怪游走可视化",
                "把一只生物刷出来之后真实走过的路线画成轨迹，轨迹末端始终连着它，怪走到哪线就跟到哪。服务端只在自然刷怪真正把生物放进世界那一刻上报一条出生事件（生物实体号＋出生坐标），之后由客户端盯住这只生物逐 tick 采样，所以画出来的是它自己走的路，而不是刷怪器早先“挑位置”的游走，不会再出现“怪物没沿着轨迹走”；玩家附近 128 格内每一只活着的生物都会有轨迹，不再只有刚刷出来的那一只。轨迹起点是一个亮绿色粗点，行走过程线是近白折线、沿路每隔几格叠一个黄色箭头指出行进方向，终点是一个沿生物最后移动方向（站着不动时按朝向）指出去的红箭头；生物死亡或走远后轨迹约 2 秒淡出。繁重的采样与绘制全在客户端，服务端每次刷怪只发几字节，且只发给 192 格以内、明确说要收的玩家，没有装了本模组的玩家在线时完全不采集，对服务器压力几乎没有影响。Ctrl+V 的渲染清单按玩家各自保存；在服务器上还能用 /sogcarpet visualizer 从服务端直接设置（玩家客户端没装本模组也能配），玩家之间互不影响。Ctrl+O 切换显示，按键可在控制设置里修改。");
        rule(m, "worldEaterHelper", "世吞小助手",
                "高亮世界吞噬者炸不掉、需要手动补挖的方块：不可推动的方块（黑曜石、基岩、附魔台、重生锚等）、各类矿石与远古残骸、紫水晶晶簇与母岩、海带，以及清单里的含水方块（珊瑚、砖墙、铜与铁的链/栏杆、蜡烛、发光地衣、铁轨、滴水石锥与硫磺石锥、灯笼、铜格栅/活板门/楼梯等）。每个这样的方块上方 7 格画一个整块大小（1×1×1）的半透明幽灵方块（按类别配色），并用细线连回本体，方便从世吞上方俯视查看还剩什么；含水方块还会在模型外面再套一层半透明水壳，做出“外部的水包裹着内部的方块”的效果。扫描范围跟着客户端渲染距离走（最多 16 个区块）且只扫已加载的区块，每 tick 分几个区块进行，所以只要在加载范围内就会渲染出来。方块被机器或其他方块完全盖住时也照常显示（可在配置界面关掉“显示被覆盖方块”），幽灵原位如果还埋在实心方块里会自动抬到最近的空位、描边与连线始终置顶，保证埋住的方块也看得见；符合条件者超过绘制上限时优先保留离玩家最近的。开启规则后 Ctrl+E 打开方块清单配置，Ctrl+O 是全部可视化的总开关，Ctrl+V 打开渲染设置。");
        rule(m, "explosionVisualizer", "爆炸可视化",
                "移植自 lucidity 的爆炸可视化。TNT 被点燃、或苦力怕膨胀即将爆炸时，以爆炸中心为球心画一个半透明浅绿色圆球，半径取这次爆炸真正能够到的最大距离（威力×1.733：TNT 约 6.9 格、苦力怕约 5.2 格、闪电苦力怕约 10.4 格）；不再画方块、射线与采样点。Ctrl+O 是全部可视化的总开关，Ctrl+V 打开渲染设置。");
        rule(m, "b36TargetPreview", "b36 目的地预览",
                "移植自 lucidity 的 b36 预览。活塞推动方块时，用该方块自己的模型与光照，在它将要到达的那一格再画一份半透明重影（所有顶点的 alpha 固定为 100）；本体还由原版画在路上的插值位置，两者错开，于是提前就能看到它会落到哪一格。Ctrl+O 是全部可视化的总开关，Ctrl+V 打开渲染设置。");
        rule(m, "fakePlayerRenderSettings", "假人渲染设置可调",
                "在 GCA 假人背包界面左侧加入渲染距离与模拟距离滑条。“跟随玩家”只剩召唤那一次：新召唤的假人在召唤时对齐一次玩家设置。不再提供右键跟随玩家设置的选项，拖滑条就是给这个假人定自己的值，每个假人独立保存；关掉规则时设置停在关闭那一刻，重新开启不会被覆盖。");
        rule(m, "flexiblePlacementCorners", "Tweakeroo 灵活放置四角三角",
                "在 Tweakeroo“灵活方块放置”的“如何放置”界面四个角各加入一个三角形放置区。按住“灵活方块放置 - 偏移位置”快捷键瞄准某个角的三角形时，方块落在该位置的斜上方／斜下方：同时沿该面的两个方向各偏移一格（墙面上就是斜上、斜下）。开启后这个界面由本模组重画，被瞄准的三角形会像其他区域一样高亮；Tweakeroo 那边只改了偏移那一格的计算。只在客户端装了 Tweakeroo 时生效，没装则规则不产生任何行为。");
        rule(m, "visualFeedback", "规则切换反馈",
                "切换 SOG 规则时在聊天栏显示状态、播放轻柔点击音效，并在玩家脚边生成少量对应主题粒子。");
        return m;
    }
}
