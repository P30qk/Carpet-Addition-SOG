package cn.blockforge.generated.sogcarpet;

import carpet.api.settings.CarpetRule;
import carpet.api.settings.Rule;
import carpet.api.settings.RuleCategory;
import carpet.api.settings.Validator;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * carpet-SOG-addition 的全部可配置规则。
 *
 * <p>规则由 {@link SogCarpetExtension} 在 onGameStarted 时解析进 Carpet 核心
 * SettingsManager，因此全部通过 {@code /carpet <规则> <值>} 修改，分类统一挂在
 * {@code [SOG]} 下；不再创建附属自己的 SettingsManager（那会额外生成一条
 * {@code /sog_carpet} 指令）。</p>
 *
 * <p>按约定：所有规则（含以后新增的）默认值一律为“禁止/关闭”，即原版行为。</p>
 */
public final class SogSettings {
    /** 本附属专用的规则分类名。 */
    public static final String SOG = "SOG";

    /** 铜生锈控制：禁止铜系方块（未涂蜡）因随机刻进入下一氧化阶段。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean copperOxidationDisabled = false;

    /**
     * 紫水晶芽/簇生长限制：紫水晶母岩的随机刻生长被卡在选定阶段——处在选定阶段的芽不再
     * 继续升级，比它更小的芽可以正常长上来（例如选 medium，小芽仍会变成中芽），
     * 比它更大的芽（如果世界里已经存在）也仍按原版继续生长（大芽仍会长成晶簇）。
     * 默认 off＝原版，芽可以一直长到紫水晶簇。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static AmethystGrowthLimit amethystBudGrowthLimit = AmethystGrowthLimit.OFF;

    /** 冰雪融化控制：冰、雪层、霜冰不再随机融化。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean iceSnowMeltDisabled = false;

    /** 强制解冻：冰的方块亮度达到该值时其随机刻立即变水（0-15，12 即原版融冰亮度）。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE}, validators = {ThawLevelValidator.class})
    public static int forceThawLightLevel = 12;

    /** 冰即刻融化：达到融化亮度的冰在邻居变化/放置时立即融化，不再等随机刻。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean iceInstantMelt = false;

    /** 雪生成控制：寒冷群系与雷暴不再形成新的雪层（水结冰保持原版）。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean snowGenerationDisabled = false;

    /** 雪傀儡产雪控制：雪傀儡移动时不在脚下铺雪。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean snowGolemSnowDisabled = false;

    /** 天气控制：雷雨期间不再生成闪电与落雷。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean thunderDisabled = false;

    /** 鸡骑士控制：幼年僵尸、溺尸、尸壳、僵尸村民、僵尸猪灵都不再骑鸡形成鸡骑士，已存在的鸡骑士保留。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean chickenJockeyDisabled = false;

    /** 精准采集扩展：精准采集镐挖强化深板岩掉落本体。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean silkTouchReinforcedDeepslate = false;

    /** 精准采集扩展：精准采集锹挖可疑的沙子/可疑的沙砾掉落本体（一条规则管两个方块）。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean silkTouchSuspicious = false;

    /** 幽匿尖啸等级可调：0 为原版；1-4 时尖啸成功后的等级直接置为该值。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE}, validators = {ShriekerLevelValidator.class})
    public static int sculkShriekerLevel = 0;

    /** 幽匿尖啸等级锁定：把尖啸器的等级冻结在当前值，既不升也不随尖啸重置。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean sculkShriekerLevelLocked = false;

    /** 禁止坚守者生成：尖啸不再召唤坚守者（刷怪蛋与指令生成不受影响）。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean wardenSpawnDisabled = false;

    /** 禁止傻子村民生成：新诞生的村民不再以傻子（nitwit）职业出现，改为无业。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean nitwitSpawnDisabled = false;

    /**
     * 禁止傻子村民占用床：傻子根本认不到床（HOME 记忆被拦），已经躺在床上的会被立刻踢下床，
     * 床留给其他村民。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean nitwitBedDisabled = false;

    /** 简易合成遮光玻璃：解锁“4 黑染料围 1 玻璃”的十字合成。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean tintedGlassEasyCraft = false;

    /** 简易合成鞘翅：解锁“6 幻翼膜 + 3 线”的 3x3 合成。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean easyElytraCraft = false;

    /** 雪可合成：解锁“1 个雪块 → 8 个雪（雪层）”的无序合成，凑够一整个雪块。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean snowCraftable = false;

    /** 剪刀不消耗耐久：剪刀做任何事都不掉耐久（剪羊毛、剪蜂巢、挖树叶、剪蘑菇等）。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean shearsNoDurability = false;

    /** 假人只拾取工具：假人只捡起工具类物品（剑/斧/镐/铲/锄/弓/弩/矛/锤/竿/剪/打火石），护甲与普通物品一律不捡。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean fakePlayerToolsOnly = false;

    /**
     * 幽匿检测范围可视化：以感测体为中心画出实心半透明检测球（默认淡蓝），
     * 感测体之间/感测体与尖啸体之间画出传播连线，并在感测体与尖啸体所在方块上罩一个
     * 半透明实心立方体（颜色比球浓一档）；尖啸体按能否生成坚守者区分配色
     * （能生成：红，被羊毛挡住黄；不能生成：白，被羊毛挡住灰）。同一个感测体同时接上
     * 两类尖啸体时按能生成的一侧渲染，但各自那段连线仍用自己的颜色；
     * Ctrl+O 切换显示（按键可在控制里改）。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean sculkRangeVisualizer = false;

    /**
     * 嘎吱之心可视化：攻击绑着嘎吱之心的嘎吱怪后，用红框框住它的嘎吱之心，
     * 并用黄线把嘎吱之心与嘎吱怪连起来（约 5 秒后消失，再打一次会刷新）。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean creakingHeartVisualizer = false;

    /**
     * 末影龙寻路可视化：画出末影龙的 24 个寻路节点（外圈 12 个品红、中圈与内圈 12 个绿）
     * 以及它最近一段飞行轨迹（亮绿折线）与当前位置（黄点）；Ctrl+O 切换显示。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean enderDragonPathVisualizer = false;

    /**
     * 凋零破坏范围可视化：用红色半透明实心立方体标出凋灵会打掉的方块范围
     * （水平 ±floor(宽/2+1) 格、竖直 floor(高) 格），范围内会被破坏的方块再逐格描橙红边；
     * Ctrl+O 切换显示。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean witherBreakRangeVisualizer = false;

    /**
     * 末影龙破坏范围可视化：白框标出龙的整体包围盒，紫色半透明实心块标出头/脖子/身体
     * 三个部位的破坏体积，真正会被撞掉的方块再逐格描红边；Ctrl+O 切换显示。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean enderDragonBreakRangeVisualizer = false;

    /**
     * 刷怪游走可视化：把刷出来的生物<b>真实走过</b>的路线画成轨迹。服务端只在自然刷怪
     * 成功那一刻发一条出生事件（实体号 + 出生坐标），客户端顺着实体号盯住这只生物逐 tick
     * 采样，于是轨迹的末端始终连着它——怪物和轨迹永远对得上，而且玩家附近每一只活着的
     * 生物都有轨迹，不再只有“刚刷出来那一只”。轨迹起点是一个亮绿色粗点，行走过程线是
     * 近白折线并沿路叠黄色方向箭头，终点是一个沿生物行进方向指出的红箭头；生物死后轨迹
     * 再淡出约 2 秒。单机、局域网主机与专用服务器都可用；Ctrl+O 切换显示。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean mobSpawnVisualizer = false;

    /**
     * b36 目的地预览（移植自 lucidity）：活塞推动方块时，在方块<b>将要到达</b>的那一格
     * 再画一份半透明度固定为 100 的“重影”，本体还在路上时就能提前看到它会停在哪。
     * 渲染方式与原实现一致：仍走方块自己的模型与光照，只把顶点 alpha 压成 100；
     * 关键是不要在移动方块实体的姿态上再叠加原版位移，否则重影会与本体重合而看不见。
     * Ctrl+O 切换显示。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean b36TargetPreview = false;

    /**
     * 世吞小助手：把世界吞噬者（世吞）打不掉、需要手动补挖的方块（不可破坏方块、各类矿石、
     * 晶簇与紫水晶母岩、远古残骸、海带，以及清单里的含水方块——珊瑚、砖墙、铜/铁链与栏杆、
     * 蜡烛、发光地衣、铁轨、滴水石锥与硫磺石锥、灯笼、铜格栅/活板门/楼梯等）在方块上方约 7 格处
     * 画一个半透明幽灵方块，并用一条细线连回本体；只标注正上方一路通气到幽灵高度、
     * 真正能从上方看到的方块，避免把埋住的矿石全画出来。Ctrl+O 切换显示；
     * Ctrl+E 打开配置界面（规则开启后才响应），可逐块开关、增删方块并单独控制含水样式。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean worldEaterHelper = false;

    /**
     * 爆炸可视化（移植自 lucidity）：TNT 被点燃、或苦力怕膨胀即将爆炸时，按原版爆炸射线模型
     * 预览这次爆炸——会被炸掉的方块按概率渲染成橙色半透明方块（越靠近爆心越实），爆炸射线上
     * 的采样点画成蓝色小点（中心色反色），爆炸中心为黄绿色点，被波及实体身上的伤害采样点为
     * 绿色（安全）／红色（暴露），爆炸源本体用深蓝（TNT）／绿色（苦力怕）高亮，最后在爆炸
     * 范围最外围罩一圈淡绿色半透明圆。Ctrl+O 切换显示。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean explosionVisualizer = false;

    /**
     * 信标附加效果：主效果新增饱和、幸运，辅助效果新增抗火。
     * 等级、持续时间、刷新频率沿用原版机制；关闭时已使用附加效果的信标会恢复为
     * 无附加效果的信标。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean beaconExtraEffects = false;

    /** 假人渲染设置可调：在 GCA 假人背包界面左侧加入渲染距离/模拟距离滑条（每假人独立）。仅召唤时对齐一次玩家设置，无“跟随”选项。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean fakePlayerRenderSettings = false;

    /**
     * Tweakeroo 灵活放置·四角三角：在“如何放置”界面（Tweakeroo 的灵活方块放置覆盖层）的四个角
     * 各加入一个三角形放置区。按住“灵活方块放置 - 偏移位置”快捷键瞄准某个角的三角形时，
     * 方块落在该位置的斜上方／斜下方——也就是在两个面内方向上都偏移一格（墙面上表现为斜上、斜下）。
     * 规则只在装 Tweakeroo 的客户端生效；没装 Tweakeroo 时静默无操作。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean flexiblePlacementCorners = false;

    /** 视觉音效与配置反馈：切换规则时在聊天栏提示并播放轻音效、生成少量粒子。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean visualFeedback = false;

    /**
     * 全部“可视化”规则，顺序即 Ctrl+V 界面里的显示顺序。
     *
     * <p>这份清单同时被服务端（把规则开关同步给客户端）与客户端（渲染总控界面）使用，
     * 所以放在公共的 {@link SogSettings} 里。{@link #visualizerRuleValue(String)} 按名字
     * 读当前值，服务端据此打包同步。</p>
     */
    public static final java.util.List<String> VISUALIZER_RULES = java.util.List.of(
            "sculkRangeVisualizer", "creakingHeartVisualizer", "enderDragonPathVisualizer",
            "witherBreakRangeVisualizer", "enderDragonBreakRangeVisualizer",
            "mobSpawnVisualizer", "worldEaterHelper", "explosionVisualizer",
            "b36TargetPreview");

    /**
     * 不是“可视化”，但同样需要让客户端知道开关的规则。
     *
     * <p>这些规则的效果全部发生在客户端（改的是客户端 mod 的行为），专用服务器上客户端读不到
     * 服务端的静态字段，所以 {@link SogRuleSyncService} 会把它们和可视化规则一起打包下发。</p>
     */
    public static final java.util.List<String> CLIENT_SYNC_RULES = java.util.List.of(
            "flexiblePlacementCorners");

    /** 按规则名读某个可视化规则当前的开关值（名字不认识时返回 false）。 */
    public static boolean visualizerRuleValue(String name) {
        switch (name) {
            case "sculkRangeVisualizer":
                return sculkRangeVisualizer;
            case "creakingHeartVisualizer":
                return creakingHeartVisualizer;
            case "enderDragonPathVisualizer":
                return enderDragonPathVisualizer;
            case "witherBreakRangeVisualizer":
                return witherBreakRangeVisualizer;
            case "enderDragonBreakRangeVisualizer":
                return enderDragonBreakRangeVisualizer;
            case "mobSpawnVisualizer":
                return mobSpawnVisualizer;
            case "worldEaterHelper":
                return worldEaterHelper;
            case "explosionVisualizer":
                return explosionVisualizer;
            case "b36TargetPreview":
                return b36TargetPreview;
            default:
                return false;
        }
    }

    /** 本模组注册的全部规则名，用于在核心 manager 的观察器回调里过滤。 */
    public static final Set<String> RULE_NAMES = Set.of(
            "copperOxidationDisabled", "amethystBudGrowthLimit",
            "iceSnowMeltDisabled", "forceThawLightLevel", "iceInstantMelt",
            "snowGenerationDisabled", "snowGolemSnowDisabled", "thunderDisabled", "chickenJockeyDisabled",
            "silkTouchReinforcedDeepslate", "silkTouchSuspicious", "sculkShriekerLevel",
            "sculkShriekerLevelLocked", "wardenSpawnDisabled", "nitwitSpawnDisabled", "nitwitBedDisabled",
            "tintedGlassEasyCraft", "easyElytraCraft", "beaconExtraEffects",
            "snowCraftable", "shearsNoDurability",
            "fakePlayerToolsOnly", "sculkRangeVisualizer", "creakingHeartVisualizer",
            "enderDragonPathVisualizer", "witherBreakRangeVisualizer", "enderDragonBreakRangeVisualizer",
            "mobSpawnVisualizer", "worldEaterHelper", "explosionVisualizer",
            "b36TargetPreview",
            "fakePlayerRenderSettings", "flexiblePlacementCorners", "visualFeedback");

    private SogSettings() {
    }

    /** 紫水晶的生长阶段，同时是 {@link #amethystBudGrowthLimit} 的取值。 */
    public enum AmethystGrowthLimit {
        /** 原版：芽可以一直长到紫水晶簇。 */
        OFF,
        /** 小芽停止生长：小芽不再变成中芽。 */
        SMALL,
        /** 中芽停止生长：中芽不再变成大芽；小芽仍可长成中芽，更大的芽仍可继续生长。 */
        MEDIUM,
        /** 大芽停止生长：大芽不再长成紫水晶簇；小芽、中芽仍可继续升级。 */
        LARGE,
        /** 紫水晶簇停止生长：簇本身已是生长终点，效果等同于原版。 */
        CLUSTER
    }

    /** 读一个方块状态处在紫水晶的哪个生长阶段；不是芽/簇时返回 {@link AmethystGrowthLimit#OFF}。 */
    public static AmethystGrowthLimit amethystStage(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.SMALL_AMETHYST_BUD) {
            return AmethystGrowthLimit.SMALL;
        }
        if (block == Blocks.MEDIUM_AMETHYST_BUD) {
            return AmethystGrowthLimit.MEDIUM;
        }
        if (block == Blocks.LARGE_AMETHYST_BUD) {
            return AmethystGrowthLimit.LARGE;
        }
        if (block == Blocks.AMETHYST_CLUSTER) {
            return AmethystGrowthLimit.CLUSTER;
        }
        return AmethystGrowthLimit.OFF;
    }

    /** 把强制解冻的亮度阈值夹在 0-15，避免写入越界值。 */
    public static final class ThawLevelValidator extends Validator<Integer> {
        @Override
        public Integer validate(CommandSourceStack source, CarpetRule<Integer> rule, Integer value, String input) {
            if (value == null) {
                return 12;
            }
            if (value < 0) {
                return 0;
            }
            return value > 15 ? 15 : value;
        }

        @Override
        public String description() {
            return "0-15";
        }
    }

    /** 幽匿尖啸等级只能取 0（原版）到 4（尖啸即召唤坚守者的等级）。 */
    public static final class ShriekerLevelValidator extends Validator<Integer> {
        @Override
        public Integer validate(CommandSourceStack source, CarpetRule<Integer> rule, Integer value, String input) {
            if (value == null) {
                return 0;
            }
            if (value < 0) {
                return 0;
            }
            return value > 4 ? 4 : value;
        }

        @Override
        public String description() {
            return "0-4, 0 = vanilla";
        }
    }
}
