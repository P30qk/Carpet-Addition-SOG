package cn.blockforge.generated.sogcarpet.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * “世吞小助手”的方块白名单配置。
 *
 * <p>世吞小助手默认标注四类补挖方块（矿石／不可推动方块／晶簇／水生植物，水生植物只留海带），
 * 以及一批<b>含水</b>方块（珊瑚、砖墙、铜/铁链与栏杆、蜡烛、发光地衣、铁轨、滴水石锥与
 * 硫磺石锥、灯笼、铜格栅/活板门/楼梯，以及沉船与废墟里可能含水的构件）。
 * 这个配置把“标注哪些方块”变成一份可编辑的白名单：</p>
 * <ul>
 *   <li>每条白名单记录一个具体方块的 id、它用的幽灵颜色分类，以及是否开启；</li>
 *   <li>“含水样式”是每条记录上的第二个开关——对可含水方块，可以单独决定它的含水状态
 *       要不要被标成蓝色；标成含水的方块渲染时会<b>在模型外面再套一层半透明水壳</b>，
 *       也就是“外部的水包裹着内部的方块”；</li>
 *   <li>另有全局开关 {@code autoWaterlogged}，默认开启：清单之外的可含水方块也会被标注
 *       （本轮删除的海泡菜、海草、高海草除外）；关掉后只有白名单里显式勾了“含水样式”的
 *       方块才标；</li>
 *   <li>全局开关 {@code markHidden}，默认开启：<b>被上方方块完全盖住</b>的补挖方块也会
 *       照常标出来（世吞的机器本身就压在待挖方块上面）；幽灵方块原位还埋在实心方块里时
 *       会自动抬到最近的空位并置顶显示。关掉后退回旧行为，只标正上方一路通气的
 *       “露头”方块。</li>
 * </ul>
 *
 * <p>配置只在客户端使用，保存在 {@code config/sog_carpet-world_eater.txt}（一行一条，
 * 纯文本，方便手改）。界面上的一切改动都会立刻落盘。</p>
 */
public final class WorldEaterConfig {
    /** 无分类：方块本体不标注，只有含水状态可能被标（用于“只加含水样式”的方块）。 */
    public static final int CAT_NONE = 0;
    /** 矿石 / 远古残骸（金）。 */
    public static final int CAT_ORE = 1;
    /** 不可推动方块（紫）。 */
    public static final int CAT_IMMOVABLE = 2;
    /** 紫水晶晶簇与母岩（青）。 */
    public static final int CAT_CLUSTER = 3;
    /** 海带（绿）。 */
    public static final int CAT_PLANT = 4;
    /** 含水方块（蓝），只在方块含水时生效。 */
    public static final int CAT_WATERLOGGED = 5;
    /** 手动添加、没有内置分类的方块（白）。 */
    public static final int CAT_CUSTOM = 6;

    /** 界面上点击色块时循环的顺序：无 → 矿 → 固 → 晶 → 植 → 自定义。 */
    private static final int[] CATEGORY_CYCLE = {
        CAT_NONE, CAT_ORE, CAT_IMMOVABLE, CAT_CLUSTER, CAT_PLANT, CAT_CUSTOM
    };

    /**
     * 幽灵方块的<b>描边</b>颜色表：一个分类可以给多种颜色，渲染时按方块位置交替使用，
     * 这样挨着的同类方块也能一眼数清。只在方块轮廓的最外围描一圈。
     */
    private static final int[] OUTLINE_ORE = {0xFFFFD24A, 0xFFFF9E2C};
    private static final int[] OUTLINE_IMMOVABLE = {0xFFC46BFF, 0xFFFF5AD8};
    private static final int[] OUTLINE_CLUSTER = {0xFF5AF0F5, 0xFFE8FFFF};
    private static final int[] OUTLINE_PLANT = {0xFF5CF05C, 0xFFB8FF5A};
    private static final int[] OUTLINE_WATERLOGGED = {0xFF5A9BFF, 0xFF6BE0FF};
    private static final int[] OUTLINE_CUSTOM = {0xFFFFFFFF, 0xFFB8C4D0};
    private static final int[] OUTLINE_NONE = {0xFFFFFFFF};

    /** 沉船里可能含水的木制方块用的木种。 */
    private static final String[] SHIPWRECK_WOODS = {
        "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove"
    };

    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("sog_carpet-world_eater.txt");
    /**
     * 默认方块清单的版本：每次往默认清单里加新方块就 +1。
     * 老配置文件在版本落后时会重新补一遍默认方块（已有条目不动），
     * 这样升级后新加的“含水铜楼梯 / 铜格栅、1.13 海洋更新含水方块、末地传送门 / 宝库 /
     * 试炼刷怪笼、默认开启的钻石绿宝石矿与远古残骸、海底废墟 / 传送门废墟的含水方块、
     * 默认开启的珊瑚、含水的墙 / 链 / 栏杆 / 蜡烛 / 发光地衣 / 铁轨 / 硫磺石锥 / 灯笼”
     * 才会生效；同时把本版删除的海泡菜 / 海草 / 高海草从老清单里清掉。
     */
    private static final int SEED_VERSION = 6;
    private static final Map<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();

    private static boolean autoWaterlogged = true;
    /**
     * 是否标注被上方方块完全盖住的补挖方块。默认开启：世吞的机器就压在待挖方块上面，
     * 只标“露头”方块会导致机器一过、标记全消失。关掉后退回旧的“只标露天方块”。
     */
    private static boolean markHidden = true;
    private static boolean loaded;

    private WorldEaterConfig() {
    }

    /** 白名单里的一条记录。 */
    public static final class Entry {
        private final ResourceLocation id;
        private int category;
        private boolean enabled;
        private boolean waterlog;

        private Entry(ResourceLocation id, int category, boolean enabled, boolean waterlog) {
            this.id = id;
            this.category = category;
            this.enabled = enabled;
            this.waterlog = waterlog;
        }

        public ResourceLocation id() {
            return id;
        }

        public String idString() {
            return id.toString();
        }

        /** 不带命名空间的路径，用于显示与过滤。 */
        public String path() {
            return id.getPath();
        }

        public int category() {
            return category;
        }

        public boolean enabled() {
            return enabled;
        }

        /** 是否把该方块的含水状态也标出来。 */
        public boolean waterlog() {
            return waterlog;
        }
    }

    // ------------------------------------------------------------------ 读取 / 保存

    /** 首次访问时从磁盘载入；此后直接返回内存里的状态。 */
    public static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        load();
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) {
            seedDefaults();
            save();
            return;
        }
        boolean seeded = false;
        int seedVersion = 0;
        try {
            for (String raw : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("seeded=")) {
                    seeded = Boolean.parseBoolean(line.substring("seeded=".length()).trim());
                } else if (line.startsWith("seedVersion=")) {
                    seedVersion = parseInt(line.substring("seedVersion=".length()), 0);
                } else if (line.startsWith("autoWaterlogged=")) {
                    autoWaterlogged = Boolean.parseBoolean(
                            line.substring("autoWaterlogged=".length()).trim());
                } else if (line.startsWith("markHidden=")) {
                    // 老配置文件没有这一行：保持默认的“显示被遮挡方块”。
                    markHidden = Boolean.parseBoolean(
                            line.substring("markHidden=".length()).trim());
                } else if (line.startsWith("entry=")) {
                    parseEntry(line.substring("entry=".length()));
                }
            }
        } catch (IOException e) {
            // 文件读不出来时退回默认白名单，至少保证可视化还能用。
            ENTRIES.clear();
            seedDefaults();
            return;
        }
        if (!seeded || seedVersion < SEED_VERSION) {
            // 老版本文件或手工新建的文件：把内置默认方块（含本轮新增的含水方块）补进来。
            seedDefaults();
            save();
        }
    }

    private static void parseEntry(String body) {
        String[] parts = body.split("\\|");
        if (parts.length < 4) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return;
        }
        int category = parseInt(parts[1], CAT_NONE);
        boolean enabled = Boolean.parseBoolean(parts[2].trim());
        boolean waterlog = Boolean.parseBoolean(parts[3].trim());
        ENTRIES.put(id, new Entry(id, category, enabled, waterlog));
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 把内置的默认方块补进白名单；已有条目保留玩家改过的分类/含水，但矿石与珊瑚按新默认校正。 */
    private static void seedDefaults() {
        // 本版删除的方块（海泡菜 / 海草 / 高海草）先从老清单里清掉，避免升级后还留着旧行。
        ENTRIES.values().removeIf(entry -> isRemovedByDefault(entry.path()));
        for (Object element : BuiltInRegistries.BLOCK) {
            Block block = (Block) element;
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) {
                continue;
            }
            String path = id.getPath();
            int category = builtinCategory(path);
            boolean waterloggable = isWaterloggable(block);
            if (category == CAT_NONE && !isDefaultWaterlogBlock(path)) {
                continue;
            }
            // 只收真正带 WATERLOGGED 属性的候选方块，铜块 / 切制铜 / 雕纹铜这类整块方块不会混进来。
            if (category == CAT_NONE && !waterloggable) {
                continue;
            }
            if (!ENTRIES.containsKey(id)) {
                // 普通矿石默认关闭，钻石 / 绿宝石 / 远古残骸默认打开；其余（含珊瑚）默认打开。
                ENTRIES.put(id, new Entry(id, category, defaultEnabled(path, category), waterloggable));
            } else if (category == CAT_ORE) {
                // 老配置升级上来时让矿石回到各自的默认开关。
                ENTRIES.get(id).enabled = defaultEnabled(path, category);
            } else if (isCoral(path)) {
                // 老配置里的珊瑚本轮改为“默认开启”。
                ENTRIES.get(id).enabled = true;
            }
        }
        // 老版本把不含水的铜方块（铜块 / 切制铜 / 雕纹铜）也塞进了清单：
        // 它们没有 WATERLOGGED 属性，本体又用 CAT_NONE 不标色，留着只会碍眼，直接清掉。
        ENTRIES.values().removeIf(entry -> entry.category == CAT_NONE
                && entry.path().contains("copper") && !isWaterloggable(entry.id));
    }

    /**
     * 这些方块默认就列进清单，只为了它们的“含水样式”（本体不标颜色）：
     * 含水的幽匿感测体 / 尖啸体；含水的墙、链（铜 / 铁及其氧化变种）、栏杆（铜 / 铁）、
     * 蜡烛（含染色）、发光地衣、铁轨、硫磺石锥 / 滴水石锥、灯笼（含铜灯笼）；
     * 铜的格栅 / 活板门 / 楼梯；1.13 海洋更新里可能含水的方块（珊瑚植株与珊瑚扇、
     * 海晶石楼梯与台阶、沉船里的箱子）、海底废墟与传送门废墟里可能含水的台阶 / 半砖，
     * 以及沉船里可能含水的木制方块。真正有没有 WATERLOGGED 属性仍由
     * {@link #isWaterloggable(Block)} 兜底过滤，不含水的铜方块（铜块 / 切制铜 / 雕纹铜 /
     * 铜门 / 铜灯 / 铜箱子）因此不会被收进来。
     */
    public static boolean isDefaultWaterlogBlock(String path) {
        if (path.equals("sculk_sensor") || path.equals("sculk_shrieker")) {
            return true;
        }
        if (isWaterloggedCopper(path) || isIronWaterlogBlock(path)) {
            return true;
        }
        if (isWall(path) || isCandle(path) || isRail(path) || isSpeleothem(path)) {
            return true;
        }
        if (path.equals("glow_lichen") || path.contains("lantern")) {
            return true;
        }
        if (isOceanWaterlogBlock(path) || isOceanRuinWaterlogBlock(path)
                || isRuinedPortalWaterlogBlock(path)) {
            return true;
        }
        for (String wood : SHIPWRECK_WOODS) {
            if (path.equals(wood + "_stairs") || path.equals(wood + "_slab")
                    || path.equals(wood + "_fence") || path.equals(wood + "_trapdoor")) {
                return true;
            }
        }
        return false;
    }

    /** 珊瑚植株 / 珊瑚扇（含失活变种）：本轮改为默认开启。 */
    private static boolean isCoral(String path) {
        return path.contains("coral");
    }

    /** 本轮从默认清单删除、也不再自动标注的方块：海泡菜与海草 / 高海草。 */
    private static boolean isRemovedByDefault(String path) {
        return path.equals("sea_pickle") || path.equals("seagrass") || path.equals("tall_seagrass");
    }

    /** 墙类方块（26.2 的 WallBlock 都实现了 SimpleWaterloggedBlock）。 */
    private static boolean isWall(String path) {
        return path.endsWith("_wall");
    }

    /** 蜡烛类方块：普通蜡烛与 16 种染色蜡烛（{@code *_candle_cake} 是蛋糕，不含水，不收）。 */
    private static boolean isCandle(String path) {
        return path.equals("candle") || path.endsWith("_candle");
    }

    /** 铁轨类方块：普通铁轨、充能 / 探测 / 激活铁轨（都实现了 SimpleWaterloggedBlock）。 */
    private static boolean isRail(String path) {
        return path.equals("rail") || path.endsWith("_rail");
    }

    /** 硫磺石锥与滴水石锥（都继承带 WATERLOGGED 的 SpeleothemBlock）。 */
    private static boolean isSpeleothem(String path) {
        return path.equals("pointed_dripstone") || path.endsWith("_spike");
    }

    /** 铁栏杆与铁链（铜版本走 {@link #isWaterloggedCopper(String)}）。 */
    private static boolean isIronWaterlogBlock(String path) {
        return path.equals("iron_bars") || path.equals("iron_chain");
    }

    /**
     * 海底废墟里会出现的含水方块。暖海底废墟用砂岩系（砂岩台阶 / 半砖），冷海底废墟用
     * 石砖 / 石头 / 圆石系（台阶 / 半砖）；结构生成时还有“苔藓变种”，会把原方块换成
     * 苔石砖、苔圆石与对应的台阶 / 半砖，所以一并收录。
     *
     * <p>实心的砂岩 / 石砖 / 圆石本身没有 WATERLOGGED 属性，会被
     * {@link #isWaterloggable(Block)} 过滤掉，不会混进来。</p>
     */
    private static boolean isOceanRuinWaterlogBlock(String path) {
        switch (path) {
            // 暖海底废墟：砂岩系
            case "sandstone_stairs":
            case "sandstone_slab":
            case "cut_sandstone_slab":
            case "smooth_sandstone_stairs":
            case "smooth_sandstone_slab":
            // 冷海底废墟：石砖系（含苔藓变种）
            case "stone_brick_stairs":
            case "stone_brick_slab":
            case "mossy_stone_brick_stairs":
            case "mossy_stone_brick_slab":
            // 冷海底废墟：石头 / 圆石系（含苔圆石）
            case "stone_stairs":
            case "stone_slab":
            case "cobblestone_stairs":
            case "cobblestone_slab":
            case "mossy_cobblestone_stairs":
            case "mossy_cobblestone_slab":
                return true;
            default:
                return false;
        }
    }

    /**
     * 传送门废墟在水下生成时会含水的方块。原版废墟是把“石头”替换成石砖、苔石砖，
     * 把“任意半砖”换成苔石砖半砖，把“任意台阶”换成石头半砖 / 石砖半砖（苔藓时换成
     * 苔石砖台阶 / 半砖）；下界变种则用黑石砖系，一并收录。
     *
     * <p>黑曜石 / 哭泣的黑曜石 / 下界岩 / 岩浆块 / 金块这些实心方块没有 WATERLOGGED
     * 属性，所以都不收——这也正好符合“黑曜石不含水”的事实；废墟里的苔石砖墙等墙类方块
     * 由 {@link #isWall(String)} 统一收录。</p>
     */
    private static boolean isRuinedPortalWaterlogBlock(String path) {
        switch (path) {
            case "stone_brick_stairs":
            case "stone_brick_slab":
            case "mossy_stone_brick_stairs":
            case "mossy_stone_brick_slab":
            case "stone_slab":
            case "smooth_stone_slab":
            case "polished_blackstone_stairs":
            case "polished_blackstone_slab":
            case "polished_blackstone_brick_stairs":
            case "polished_blackstone_brick_slab":
                return true;
            default:
                return false;
        }
    }

    /**
     * 铜类里真正可含水的构件：格栅、活板门、楼梯、栏杆、链与灯笼，氧化变种
     * （{@code oxidized_copper_*} 等）与涂蜡变种都带 {@code copper} 路径、一并收录。
     * 26.2 里整块的铜块 / 切制铜 / 雕纹铜、铜门、铜灯与铜箱子都没有 WATERLOGGED 属性，
     * 这里刻意不收；{@link #seedDefaults()} 还会在升级时清掉老配置里遗留的死条目。
     */
    private static boolean isWaterloggedCopper(String path) {
        if (!path.contains("copper")) {
            return false;
        }
        return path.contains("grate") || path.contains("trapdoor") || path.contains("stairs")
                || path.contains("bars") || path.contains("chain") || path.contains("lantern");
    }

    /**
     * 1.13 海洋更新相关的可含水方块：珊瑚植株与珊瑚扇（含失活变种）、
     * 海晶石楼梯 / 台阶，以及沉船里的（陷阱）箱子。海泡菜本轮已从默认清单删除。
     * 实心珊瑚块没有 WATERLOGGED 属性，会被 {@link #isWaterloggable(Block)} 过滤掉。
     */
    private static boolean isOceanWaterlogBlock(String path) {
        if (path.equals("chest") || path.equals("trapped_chest")) {
            return true;
        }
        if (path.contains("coral")) {
            return true;
        }
        return path.equals("prismarine_stairs") || path.equals("prismarine_brick_stairs")
                || path.equals("dark_prismarine_stairs") || path.equals("prismarine_slab")
                || path.equals("prismarine_brick_slab") || path.equals("dark_prismarine_slab");
    }

    /** 钻石 / 绿宝石（含深层变种）与远古残骸：这几样矿石默认就打开。 */
    private static boolean isDefaultOnOre(String path) {
        return path.equals("diamond_ore") || path.equals("deepslate_diamond_ore")
                || path.equals("emerald_ore") || path.equals("deepslate_emerald_ore")
                || path.equals("ancient_debris");
    }

    /** 新方块进入默认清单时是否默认勾选：只有非钻石 / 绿宝石 / 远古残骸的普通矿石默认关闭。 */
    private static boolean defaultEnabled(String path, int category) {
        if (category == CAT_ORE) {
            return isDefaultOnOre(path);
        }
        return true;
    }

    /** 把当前白名单写回磁盘。 */
    public static synchronized void save() {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("# sog_carpet 世吞小助手方块白名单（可手工编辑）\n");
        sb.append("seeded=true\n");
        sb.append("seedVersion=").append(SEED_VERSION).append('\n');
        sb.append("autoWaterlogged=").append(autoWaterlogged).append('\n');
        sb.append("markHidden=").append(markHidden).append('\n');
        List<Entry> sorted = sortedEntries();
        for (Entry entry : sorted) {
            sb.append("entry=").append(entry.idString())
                    .append('|').append(entry.category)
                    .append('|').append(entry.enabled)
                    .append('|').append(entry.waterlog)
                    .append('\n');
        }
        try {
            Path parent = FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(FILE, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // 落盘失败只影响下次启动，当前会话照常工作。
        }
    }

    private static List<Entry> sortedEntries() {
        List<Entry> list = new ArrayList<>(ENTRIES.values());
        list.sort(Comparator.comparingInt((Entry entry) -> entry.category)
                .thenComparing(entry -> entry.path()));
        return list;
    }

    // ------------------------------------------------------------------ 查询

    /** 当前白名单（按分类、方块名排序的只读快照）。 */
    public static synchronized List<Entry> entries() {
        ensureLoaded();
        return sortedEntries();
    }

    public static synchronized boolean isAutoWaterlogged() {
        ensureLoaded();
        return autoWaterlogged;
    }

    /** 是否把被上方方块完全盖住的补挖方块也标出来（默认开）。 */
    public static synchronized boolean isMarkHidden() {
        ensureLoaded();
        return markHidden;
    }

    /** 方块本体要不要标注（不含“仅含水”的情况）。 */
    public static synchronized boolean isEnabled(ResourceLocation id, boolean waterloggable) {
        ensureLoaded();
        Entry entry = ENTRIES.get(id);
        if (entry != null) {
            return entry.enabled;
        }
        // 本轮删除的方块（海泡菜 / 海草 / 高海草）即使还有全局自动含水开关也不标。
        if (isRemovedByDefault(id.getPath())) {
            return false;
        }
        // 不在白名单里的可含水方块：沿用原版行为，由全局开关决定。
        return autoWaterlogged && waterloggable;
    }

    /** 方块的含水状态要不要标成蓝色。 */
    public static synchronized boolean isWaterlogMarked(ResourceLocation id, boolean waterloggable) {
        ensureLoaded();
        Entry entry = ENTRIES.get(id);
        if (entry != null) {
            return entry.waterlog;
        }
        if (isRemovedByDefault(id.getPath())) {
            return false;
        }
        return autoWaterlogged && waterloggable;
    }

    /** 方块本体不带含水状态时用哪个颜色分类；0 表示不标注本体。 */
    public static synchronized int effectiveCategory(ResourceLocation id) {
        ensureLoaded();
        Entry entry = ENTRIES.get(id);
        if (entry != null) {
            return entry.category;
        }
        return builtinCategory(id.getPath());
    }

    /** 这个方块在游戏里是否可含水（有没有 WATERLOGGED 属性）。 */
    public static boolean isWaterloggable(ResourceLocation id) {
        Block block = blockOf(id);
        return block != null && isWaterloggable(block);
    }

    private static boolean isWaterloggable(Block block) {
        return block.defaultBlockState().hasProperty(BlockStateProperties.WATERLOGGED);
    }

    /** 方块的中文/本地化名字，取不到时退回 id。 */
    public static String displayName(ResourceLocation id) {
        Block block = blockOf(id);
        return block == null ? id.toString() : block.getName().getString();
    }

    public static Block blockOf(ResourceLocation id) {
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return null;
        }
        return (Block) BuiltInRegistries.BLOCK.getValue(id);
    }

    /** 解析文本框里的方块 id，支持省略 {@code minecraft:} 前缀；非法时返回 null。 */
    public static ResourceLocation parseId(String text) {
        String trimmed = text.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(trimmed);
        if (id == null) {
            id = ResourceLocation.tryParse(ResourceLocation.DEFAULT_NAMESPACE + ":" + trimmed);
        }
        return id;
    }

    // ------------------------------------------------------------------ 修改

    public static synchronized void setAutoWaterlogged(boolean value) {
        ensureLoaded();
        autoWaterlogged = value;
        save();
    }

    /** 切换“显示被遮挡方块”。 */
    public static synchronized void setMarkHidden(boolean value) {
        ensureLoaded();
        markHidden = value;
        save();
    }

    /** 把方块加进白名单（已存在则开启它），返回它的记录。 */
    public static synchronized Entry addEntry(ResourceLocation id) {
        ensureLoaded();
        Block block = blockOf(id);
        if (block == null) {
            return null;
        }
        Entry entry = ENTRIES.get(id);
        if (entry == null) {
            entry = new Entry(id, defaultCategoryFor(id, block), true, isWaterloggable(block));
            ENTRIES.put(id, entry);
        } else {
            entry.enabled = true;
        }
        save();
        return entry;
    }

    public static synchronized void setEnabled(ResourceLocation id, boolean value) {
        ensureLoaded();
        Entry entry = ENTRIES.get(id);
        if (entry == null) {
            if (value) {
                addEntry(id);
            }
            return;
        }
        entry.enabled = value;
        save();
    }

    public static synchronized void setWaterlog(ResourceLocation id, boolean value) {
        ensureLoaded();
        Entry entry = ENTRIES.get(id);
        if (entry == null) {
            entry = addEntry(id);
            if (entry == null) {
                return;
            }
        }
        entry.waterlog = value;
        save();
    }

    public static synchronized void setCategory(ResourceLocation id, int category) {
        ensureLoaded();
        Entry entry = ENTRIES.get(id);
        if (entry == null) {
            entry = addEntry(id);
            if (entry == null) {
                return;
            }
        }
        entry.category = isValidCategory(category) ? category : CAT_NONE;
        save();
    }

    /** 把方块从白名单移除。内置默认方块移除后也不再标注，直到被重新添加。 */
    public static synchronized void removeEntry(ResourceLocation id) {
        ensureLoaded();
        ENTRIES.remove(id);
        save();
    }

    /** 恢复成出厂白名单。 */
    public static synchronized void resetDefaults() {
        ensureLoaded();
        ENTRIES.clear();
        autoWaterlogged = true;
        markHidden = true;
        seedDefaults();
        save();
    }

    public static int nextCategory(int current) {
        for (int i = 0; i < CATEGORY_CYCLE.length; i++) {
            if (CATEGORY_CYCLE[i] == current) {
                return CATEGORY_CYCLE[(i + 1) % CATEGORY_CYCLE.length];
            }
        }
        return CAT_ORE;
    }

    private static boolean isValidCategory(int category) {
        for (int candidate : CATEGORY_CYCLE) {
            if (candidate == category) {
                return true;
            }
        }
        return false;
    }

    /** 手动添加方块时猜一个默认分类。 */
    private static int defaultCategoryFor(ResourceLocation id, Block block) {
        int builtin = builtinCategory(id.getPath());
        if (builtin != CAT_NONE) {
            return builtin;
        }
        if (isWaterloggable(block)) {
            // 只有含水状态值得看，本体默认不标（颜色留空，含水时自动用蓝）。
            return CAT_NONE;
        }
        return CAT_CUSTOM;
    }

    // ------------------------------------------------------------------ 分类判定

    /** 按方块路径判断内置分类；0 表示不是补挖方块。 */
    public static int builtinCategory(String path) {
        if (isOre(path)) {
            return CAT_ORE;
        }
        if (isImmovable(path)) {
            return CAT_IMMOVABLE;
        }
        if (isCluster(path)) {
            return CAT_CLUSTER;
        }
        if (isWaterPlant(path)) {
            return CAT_PLANT;
        }
        return CAT_NONE;
    }

    private static boolean isOre(String path) {
        return path.endsWith("_ore") || path.equals("ancient_debris") || path.equals("gilded_blackstone")
                || path.equals("raw_iron_block") || path.equals("raw_copper_block")
                || path.equals("raw_gold_block");
    }

    private static boolean isImmovable(String path) {
        return path.equals("obsidian") || path.equals("crying_obsidian") || path.equals("bedrock")
                || path.equals("reinforced_deepslate") || path.equals("respawn_anchor")
                || path.equals("enchanting_table") || path.equals("end_portal_frame")
                || path.equals("end_portal") || path.equals("vault") || path.equals("trial_spawner")
                || path.equals("beacon") || path.equals("conduit");
    }

    private static boolean isCluster(String path) {
        return path.equals("budding_amethyst") || path.equals("amethyst_cluster")
                || path.endsWith("_amethyst_bud");
    }

    /** 水生植物只保留海带；海草 / 高海草本轮已从默认清单删除。 */
    private static boolean isWaterPlant(String path) {
        return path.equals("kelp") || path.equals("kelp_plant");
    }

    /** 幽灵方块的填充基色（不含透明度）。 */
    public static int categoryColor(int category) {
        switch (category) {
            case CAT_ORE:
                return 0xFFD9A227;
            case CAT_IMMOVABLE:
                return 0xFFB44DFF;
            case CAT_CLUSTER:
                return 0xFF46D9E0;
            case CAT_PLANT:
                return 0xFF3CE04A;
            case CAT_WATERLOGGED:
                return 0xFF3D7BFF;
            case CAT_CUSTOM:
                return 0xFFFFFFFF;
            default:
                return 0xFF9AA0A6;
        }
    }

    /** 幽灵方块的描边颜色表：同分类多色时由调用方按位置交替取用。 */
    public static int[] outlinePalette(int category) {
        switch (category) {
            case CAT_ORE:
                return OUTLINE_ORE;
            case CAT_IMMOVABLE:
                return OUTLINE_IMMOVABLE;
            case CAT_CLUSTER:
                return OUTLINE_CLUSTER;
            case CAT_PLANT:
                return OUTLINE_PLANT;
            case CAT_WATERLOGGED:
                return OUTLINE_WATERLOGGED;
            case CAT_CUSTOM:
                return OUTLINE_CUSTOM;
            default:
                return OUTLINE_NONE;
        }
    }

    /** 分类的界面名字。 */
    public static Component categoryName(int category) {
        switch (category) {
            case CAT_ORE:
                return Component.translatable("sog_carpet.ui.world_eater.cat.ore");
            case CAT_IMMOVABLE:
                return Component.translatable("sog_carpet.ui.world_eater.cat.immovable");
            case CAT_CLUSTER:
                return Component.translatable("sog_carpet.ui.world_eater.cat.cluster");
            case CAT_PLANT:
                return Component.translatable("sog_carpet.ui.world_eater.cat.plant");
            case CAT_WATERLOGGED:
                return Component.translatable("sog_carpet.ui.world_eater.cat.waterlogged");
            case CAT_CUSTOM:
                return Component.translatable("sog_carpet.ui.world_eater.cat.custom");
            default:
                return Component.translatable("sog_carpet.ui.world_eater.cat.none");
        }
    }
}
