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
     * 发射器可除锈/除蜡：发射器内装有斧头（任意材质，含 #minecraft:axes 标签的模组斧头）时，
     * 被触发后就会像玩家用斧头一样处理正前方对准的铜类方块——涂蜡的除蜡，未涂蜡但已氧化的
     * 降一级氧化（氧化→风化→斑驳→未氧化），音效与粒子与原版一致。每成功处理一次，斧头消耗
     * 1 点耐久（走与玩家用斧头相同的原版结算路径，耐久附魔照常生效，耐久耗尽时斧头照常损坏
     * 消失）；正前方没有可处理的铜类方块时不消耗耐久、不改变方块，斧头留在发射器里。
     * 规则由服务端生效，专用服务器同样可用。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean dispenserCopperScrape = false;

    /**
     * 发射器可耕地：发射器内装有任意材质的锄头（原版 {@code HoeItem} 及其子类，沿用原版
     * 耕作逻辑的模组锄头同样可用）时，被触发后就会像玩家用锄头一样处理正前方对准的
     * 方块——泥土、草方块与土径耕成耕地，粗泥耕成泥土，缠根泥土耕成泥土并掉落 1 个垂根，
     * 音效与游戏事件与原版锄头一致。每成功耕地一次，锄头消耗 1 点耐久（与原版锄头一致，
     * 耐久耗尽时锄头照常损坏消失）；正前方没有可耕的方块时不消耗耐久、不改变方块，
     * 锄头留在发射器里。规则由服务端生效，专用服务器同样可用。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean dispenserTilling = false;

    /**
     * 发射器可种植作物：发射器内装有作物物品（小麦种子、甜菜种子、胡萝卜、土豆、西瓜种子、
     * 南瓜种子、火把花种子、瓶子草荚果——即方块标签 #minecraft:crops 里的作物，模组作物
     * 加入该标签后同样可用）时，被触发后把它种到正前方：对着耕地，就种在耕地上方那一格；
     * 对着耕地正上方的空位，就直接种在该空位里。装有紫颂花时，对着末地石（或末地石正上方
     * 的空位）把紫颂花种到末地石上。种植走原版放置流程（位置可替换、光照、实体阻挡等判定
     * 与原版玩家放置一致），成功后消耗 1 个物品并播放原版放置音效；不满足条件时不消耗、
     * 不改变方块。规则由服务端生效，专用服务器同样可用。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean dispenserPlanting = false;

    /**
     * 发射器可采集浆果：发射器内装有剪刀（任意剪刀，含模组剪刀）时，被触发后会采集正前方
     * 对准的成熟甜浆果丛（age&gt;1）或长有发光浆果的洞穴藤蔓：甜浆果丛回到刚结果后的状态
     * （age=1）并按原版掉落甜浆果，洞穴藤蔓的发光浆果被摘下并按原版掉落发光浆果，音效、
     * 掉落表与游戏事件与原版手动采摘完全一致。每成功采集一次，剪刀消耗 1 点耐久（“剪刀不
     * 消耗耐久”规则开启时不损耗）；正前方没有可采集的浆果时不消耗耐久，并退回原版剪刀
     * 发射器行为（蜂巢取蜜、剪实体等照旧）。规则由服务端生效，专用服务器同样可用。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean dispenserBerryHarvest = false;

    /**
     * 紫颂花可催熟：骨粉可以催熟紫颂花与紫颂植株（玩家右键或用发射器都行）。每次成功使用让
     * 最近一朵还能生长的紫颂花执行一次与原版随机刻完全相同的生长判定：能向上就向上续一格
     * （原位置变紫颂植株、上方长出一朵年龄不变的新花）；不能向上就按原版在侧面随机尝试、
     * 分叉出一朵或多朵大一岁的新花；真的无处可长时按原版把花变成枯萎的死花（age=5）。
     * 因此催熟会像自然生长一样收束——花迟早枯萎，而枯萎的花不能再被催熟。唯一什么都不发生
     * 的情形是花的上方被占（原版随机刻同样不做任何事），此时骨粉无效、不消耗、不改变方块。
     * 规则关闭时与原版完全一致（骨粉对紫颂无效）。规则由服务端生效，专用服务器同样可用；
     * 客户端会同步开关用于骨粉右键预测。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean chorusFlowerBonemeal = false;

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

    /** 鞘翅不消耗耐久：滑翔、烟花推进等由鞘翅引起的耐久损耗一律不再发生。 */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean elytraNoDurability = false;

    /**
     * 木头去皮配方：解锁工作台里“原木/木头（含菌柄/菌核、竹块）+ 任意种类斧头”的合成，
     * 产出对应的去皮木头；斧头合成后保留在配方栏中（不消耗）。每种可去皮的木头各一条配方。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean woodStrippingRecipe = false;

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
     * 刷怪候选路径/随机游走/成功率可视化（移植自 lucidity 与原版刷怪算法）：服务端把刷怪器
     * 依次考察过的候选位置随出生事件一起下发，客户端把刷怪器真正的随机游走路径连成品红折线、
     * 命中的那个高亮成白色；纯客户端（对面没装本模组）则按同一套原版偏移规则在本地模拟一条
     * 随机游走，保证也能渲染。同时把每个刷怪分类的“尝试/成功次数”与各检查环节统计出来，
     * 在 Tab 列表页脚里显示成功率（长按 Tab 显示），并在最新出生点旁标出整体成功率。
     * 与 {@link #mobSpawnVisualizer}（生物真实走过的轨迹）分开，可各自独立开关；
     * 颜色与选项在 Ctrl+V 的“配置”界面里改。Ctrl+O 是总开关。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean spawnStatsVisualizer = false;

    /**
     * 可刷怪方块生成范围可视化：把会自己刷怪的方块（可刷怪方块）的生成范围画出来——刷怪笼与
     * 试炼刷怪笼（水平 ±刷怪范围、竖直 ±1 格，即 9×3×9；刷怪笼读取它真实的 SpawnRange NBT
     * 值、默认 4，试炼刷怪笼读取其配置的 spawn_range、自定义配置不同步到客户端时显示默认 4）、
     * 幽匿尖啸体召唤坚守者的范围（水平 ±5、竖直 ±6 格），每种范围用一个半透明红色长方体罩住，
     * 中心的刷怪方块
     * 再用一个黄色线框框住。数据全在客户端读方块实体得到，不需要服务端下发；Ctrl+O 切换显示。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean spawnRangeVisualizer = false;

    /**
     * 村民功能方块绑定可视化：用绿线把村民和它绑定的床连起来、用绿框框住床；用红线把村民和
     * 它绑定的工作方块连起来、用红框框住工作方块。绑定关系只存在于服务端的 {@code Brain}，
     * 所以由服务端采集并同步（单机 / 局域网 / 专用服务器一致）；纯客户端则退回本地近似
     * （连到最近的床与匹配职业的最近工作站）。Ctrl+O 切换显示，颜色在 Ctrl+V 里改。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean villagerBindingVisualizer = false;

    /**
     * 巨人旧版 AI（14w50a）：给巨人装上旧版那套“会主动追击并攻击”的 AI——近战攻击、
     * 随机游走、看向玩家、被打了还手，并主动锁定玩家 / 村民 / 铁傀儡；对已经存在和之后
     * 新召唤 / 新加载的巨人都生效。属性沿用原版（生命 100、速度 0.5、攻击 50）。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean giantOldAi = false;

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
     * 投掷物轨迹可视化：所有可投掷实体（箭、光灵箭、三叉戟、雪球、鸡蛋、末影珍珠、喷溅/滞留药水、
     * 附魔之瓶、火球与风弹、烟花火箭、末影之眼、鱼钩、羊驼唾沫、潜影贝导弹等）投出后，
     * 把其实体每 tick 的真实位置连成轨迹折线，末端标出当前位置；投掷物落地或消失后轨迹约 5 秒淡出、
     * 随后整条清除。
     *
     * <p>另外会画<b>轨迹预览</b>（虚线）：手持可投掷物时提前画出“会落到哪”；飞行中的投掷物画出
     * 还没走到的那一段；悬停的远传珍珠按服务端同步的待施加速度画出落点（与“简易珍珠远距传送”、
     * “速度/动量可视化”共用同一份冲量数据）。预测的物理参数与速度/动量可视化一致（同一套 g/f），
     * 碰撞只预测方块、不预测实体。只有悬停珍珠那一路需要服务端装本模组，手持与飞行预览纯客户端也能画。</p>
     *
     * <p>只跟踪玩家一定范围以内、客户端真正看得见的实体，采样、模拟与绘制全在客户端完成，
     * 单条轨迹的采样点数、预览条数与同屏图元数都有上限；Ctrl+O 切换显示，颜色与各路预览开关在
     * Ctrl+V 的“配置”里改。</p>
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean projectileTrajectoryVisualizer = false;

    /**
     * 速度/动量/加速度可视化：开启规则后，用<b>木剑右键</b>某个实体把它设为观察对象
     * （用 Tweakeroo 的灵魂出窍（freecam）时，鼠标朝哪就选哪，可以超出正常手长范围）。
     * 以该实体为中心建立 X/Y/Z 坐标轴，每个轴上画出该轴的矢量、并有一个从轴心指出的合矢量箭头；
     * 可选显示速度或动量（动量 = 速度 × 质量，质量按实体包围盒体积估算）、加速度（速度逐 tick 的差）、
     * 以及外部施加的冲量（来自 {@code addDeltaMovement}/推动/爆炸等）。全部在客户端计算与绘制，
     * 服务端只需同步规则开关。Ctrl+O 是总开关，Ctrl+V 打开渲染设置，点“配置”按钮调整显示项。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean motionVisualizer = false;

    /**
     * 仇恨可视化：被某个生物选为攻击目标的目标头顶画红色瞄准环，尝试去攻击的攻击者头顶画红色
     * 感叹号，并用红线把两者连起来；对所有生物生效（玩家只会作为被锁定的一方出现，其它生物可以
     * 同时拥有两个标签）。坚守者额外有一个黄色疑问标签：未锁定目标却被声音吸引时，头顶画黄色
     * 问号并用黄线连到声音源（引起它的生物／方块／投掷物）所在处。数据由服务端每几 tick 采集
     * 一次、只发给附近明确要收的玩家；Ctrl+O 切换显示。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean aggroVisualizer = false;

    /**
     * 敌对生物仇恨范围可视化：对玩家附近的每一只敌对生物，在它的仇恨范围最外围画一个红色半透明
     * 圆球。半径取该生物的 {@code follow_range} 属性值，再乘上本地玩家对它的“可见度系数”，
     * 于是潜行、戴上对应生物的头颅都会让圆球缩小。完全在客户端计算与绘制，服务端零负担；
     * Ctrl+O 切换显示。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean aggroRangeVisualizer = false;

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
     * 物品合并范围可视化（移植自 lucidity）：对玩家附近的每个掉落物，把它能被自动合并的
     * 范围（原版 {@code ItemEntity#mergeWithNeighbours} 用的包围盒水平外扩 0.5 格）画成一个
     * 半透明黄色盒子，于是物品一多就能一眼看出哪些马上会被吸到一起。完全在客户端计算与绘制，
     * 服务端零负担；Ctrl+O 切换显示。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean itemMergeRangeVisualizer = false;

    /**
     * 自定义信标效果：主效果那行右侧与副效果行下方各多出一个原版样式的「效果槽」（主效果从 1 层
     * 起可用，副效果仍是原版 4 层门槛），点开后能从<b>游戏内全部效果</b>里挑一个放进去；
     * 确认后回到信标界面显示出已选效果，再点原版确认就生效。等级、持续时间与刷新节奏全部沿用
     * 原版公式（4 层且主副同一效果时为原版 II 级）。
     *
     * <p>另外：信标支付格在规则开启时接受<b>下界之星</b>（图标排在钻石后面），用下界之星开启的
     * 信标，其主/副效果在原有等级上再 +1 级，标记存在信标 NBT 里、重启不丢；之后用普通支付物
     * 重开则清掉这个 +1。规则关闭后，用了自定义效果或下界之星的信标会失效（效果被清空），
     * 变回原版的“空信标”。</p>
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean customBeaconEffects = false;

    /**
     * 自定义信标效果用<b>黑名单</b>还是<b>白名单</b>：
     * {@link BeaconEffectFilterMode#BLACKLIST}（默认）用 {@link #beaconEffectBlacklist}，
     * {@link BeaconEffectFilterMode#WHITELIST} 用 {@link #beaconEffectWhitelist}。
     *
     * <p>两份名单完全独立、互不覆盖：切换模式只改变“哪一份生效”，不会清空或改写另一份。
     * 名单只作用于信标方块里的「自定义信标效果」槽位，<b>不影响随身信标</b>
     * （随身信标的可选范围只由 {@link #customBeaconEffects} 决定）。原版信标的六个效果按钮
     * 也不受名单影响。Op（权限等级 2）可以在这里、用 {@code /sogcarpet beaconfilter} 指令、
     * 或在效果选择界面里右键直接修改<b>当前模式</b>那一份名单。</p>
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static BeaconEffectFilterMode beaconEffectFilterMode = BeaconEffectFilterMode.BLACKLIST;

    /**
     * <b>黑名单</b>：逗号分隔的效果 id（例如 {@code minecraft:night_vision,minecraft:bad_omen}）。
     * 黑名单模式下，这里面的效果不能在信标方块的自定义槽位里被选中；空串表示不限制任何效果。
     * 与 {@link #beaconEffectWhitelist} 互相独立。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE}, validators = {BeaconFilterListValidator.class})
    public static String beaconEffectBlacklist = "";

    /**
     * <b>白名单</b>：逗号分隔的效果 id。白名单模式下，只有这里面的效果能在信标方块的自定义槽位里
     * 被选中；空串时回退到原版信标可用的六种效果（速度、急迫、抗性、跳跃、力量、再生）。
     * 与 {@link #beaconEffectBlacklist} 互相独立。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE}, validators = {BeaconFilterListValidator.class})
    public static String beaconEffectWhitelist = "";

    /**
     * 随身信标：玩家背包（含快捷栏、副手、装备槽）里带着信标时，手持信标按 <b>Ctrl + 右键</b>
     * 打开效果选择界面（Ctrl+右键在客户端就被拦下，不会触发方块放置或物品使用），选中效果后由
     * 服务端校验并直接给该玩家对应的药水效果。
     *
     * <p>最多可选<b>两个</b>效果：选两个时各 <b>I 级</b>；只选一个时该效果 <b>II 级</b>。
     * <b>时长</b>：不再是固定 90 秒——只要信标还在背包里，效果就一直续期（等于永久）；信标离开
     * 背包（丢弃、放进箱子、被拿走）后效果立刻消失。携带的信标不会被消耗。</p>
     *
     * <p>可选效果与「自定义信标效果」联动：规则开启时是全部效果，关闭时只给原版信标能给的六种。
     * <b>黑/白名单不影响随身信标</b>（名单只管信标方块里的自定义效果槽位）。规则由服务端生效
     * （服务端发放效果），专用服务器同样可用；没装客户端模组的玩家可以用
     * {@code /sogcarpet portablebeacon} 走同一条路径。</p>
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean portableBeacon = false;

    /**
     * 雷击粘土转化：自然落雷（雷暴、三叉戟引雷等一切真正的闪电）击中的位置，把雷击影响范围内的
     * <b>粘土</b>转成<b>陶瓦</b>。范围与原版闪电的判定半径一致：以落雷点为中心，水平 ±3 格、
     * 竖直 -3～+6 格（7×10×7 的方块区域，可视化的“雷击影响范围”画的就是它）。雷暴关闭、
     * 或闪电被设为只播放视觉效果（{@code visualOnly}）时不转化。规则由服务端生效。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean lightningTurnsClay = false;

    /**
     * 雷击范围可视化：落雷时在落雷点画出半透明的影响范围（水平 ±3、竖直 -3～+6，与原版闪电
     * 判定范围一致），100 tick（5 秒）后淡出消失。附近的避雷针会一直显示：本体用方框框住，并画出它的
     * <b>引雷范围</b>（原版在 128 格内找最近避雷针把落雷引过去，画成三个正交圆环）与
     * <b>雷击影响范围</b>（落在该避雷针上时会被影响的区域）。数据全部来自客户端已经同步的
     * 闪电实体与方块，服务端不需要下发任何东西。Ctrl+O 是总开关，Ctrl+V 打开渲染设置。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean lightningRangeVisualizer = false;

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
     * 简易珍珠远距传送（珍珠炮极简版）：把末影珍珠（或雪球）命名为
     * {@code 远传珍珠}（英文 {@code FarPearl}）后投出，它会悬停在原地不再下落。
     * 此时玩家副手拿一张名字是纯数字（可正可负）的纸、主手拿<b>任意材质的铲子</b>，
     * 看向想推的方向攻击悬停的珍珠，就以视线主轴（X/Y/Z 中绝对值最大的分量）叠加纸上的
     * 数值（单位取“格/tick”的速度，便于理解）；主手空手右键珍珠即让它以累积的速度飞出，
     * 之后按原版末影珍珠落地传送主人。雪球用同一套流程，只用于传送前的调试。
     * 1.21.10 没有长矛，因此用普通近战攻击；客户端也要装本模组才能选中投掷物。
     * 规则由服务端生效，专用服务器同样可用；空手右键与普通攻击都不会改变累积的速度。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean pearlTeleport = false;

    /**
     * 横扫攻击范围可视化（移植自 sweeping-view）：当玩家手持剑并瞄准某个实体时，
     * 把原版横扫攻击的两个判定范围画出来——被攻击目标碰撞箱水平外扩 1 格、竖直外扩
     * 0.25 格的方框（线框 + 半透明面），以及以攻击者脚底中心为球心、半径 3 格的球体。
     * 完全在客户端逐帧计算与绘制；Ctrl+O 是总开关，颜色/开关在 Ctrl+V 里改。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean sweepRangeVisualizer = false;

    /**
     * 掉落物数量标签可视化（移植自 drop-count）：在掉落物上方以名牌的方式画出物品名称
     * 与合并后的数量（同物品、包围盒水平外扩 0.5 格会挨在一起的掉落物合并成一个标签，
     * 重叠的标签自动上下错开）。完全在客户端根据同步下来的物品实体计算与绘制；
     * Ctrl+O 是总开关，颜色在 Ctrl+V 里改。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean dropCountVisualizer = false;

    /**
     * 物品留存时长可视化：在掉落物上方画出它距离自动消失（原版掉落物 {@code age >= 6000}
     * 即 5 分钟后清除）还剩多久——所有服务端同步下来的掉落物实体在客户端都会逐 tick 累加
     * {@link net.minecraft.world.entity.item.ItemEntity#getAge()}，因此纯客户端也能算出剩余时长。
     * 支持两种显示制式（在 Ctrl+V 的“配置”里切换）：六十制的 {@code x:xx}（分:秒）与游戏刻制的
     * {@code xgt}。默认与“掉落物数量标签”联动，剩余时长会直接接在“名称 x数量”后面；也允许单独
     * 作为一条规则渲染（此时每个掉落物上方只画剩余时长）。掉落物从不消失（如 {@code age == -32768}）
     * 时显示 {@code ∞}。完全在客户端计算与绘制，服务端零负担；Ctrl+O 是总开关。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean itemRetentionVisualizer = false;

    /**
     * 生命数值变化可视化：实体生命值变化时在它头顶浮出一个数字，受伤的是红色带负号的数（例如
     * {@code -6}）、回血的是绿色带正号的数（例如 {@code +4}），一路向上飘约一格并逐渐变淡，
     * 3 秒（60 tick）后消失。服务端装了本模组时用的是真实数值——受伤走 Fabric 的
     * {@code ServerLivingEntityEvents.AFTER_DAMAGE}（护甲、附魔、盾牌、吸收都算完的那一份），
     * 回血走 {@code LivingEntity#heal} 前后的血量差（瞬间治疗、再生、进食、金苹果等所有治疗
     * 路径的唯一出口）——每 tick 只发给附近明确要收的玩家；对面没装本模组（纯客户端）时，客户端
     * 按同步下来的血量变化估算受伤与回血，保证也能渲染。两种颜色与是否启用纯客户端估算在 Ctrl+V
     * 的“配置”里改；Ctrl+O 是总开关。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean healthChangeVisualizer = false;

    /**
     * 非法方块可视化：把屏障、结构空位、光明方块这类平时看不见的方块整块渲染出来，<b>无需手持</b>
     * 对应方块。观感与基岩版手持这些方块时相近：每个方块按它自己的颜色（方块地图色；没有地图色、
     * 或者地图色暗到看不见时用该方块的代表色——屏障红、结构空位灰绿、光明方块暖黄、其它技术方块白）
     * 画一个半透明实心立方体并描边，光明方块还会在方块中央标出它的光照等级（0-15）。可选的
     * “其它技术方块”包含移动的活塞、活塞头、末地传送门与末地折跃门。扫描范围跟着客户端渲染距离走、
     * 只扫已加载的区块，全部在客户端完成，服务端零负担；每一类都能在 Ctrl+V 的“配置”里单独开关。
     * Ctrl+O 是总开关。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean illegalBlockVisualizer = false;

    /**
     * 物品流向跟踪（移植自 ItemFlowTracker）：在 Ctrl+V 的“物品流向”面板里给手持物品打上颜色
     * 标记后，被标记的物品无论流进哪个容器（漏斗、箱子、玩家背包……）都会在服务端生成一个发光
     * 显示实体，把对应容器/掉落物高亮出来，于是连没装本模组的纯客户端也能看到。可选记录物品
     * 移动路径。规则由服务端统一生效，专用服务器同样可用；关闭时立即清除所有高亮。
     */
    @Rule(categories = {SOG, RuleCategory.FEATURE})
    public static boolean itemFlowTracker = false;

    /**
     * 全部“可视化”规则，顺序即 Ctrl+V 界面里的显示顺序。
     *
     * <p>这份清单同时被服务端（把规则开关同步给客户端）与客户端（渲染总控界面）使用，
     * 所以放在公共的 {@link SogSettings} 里。{@link #visualizerRuleValue(String)} 按名字
     * 读当前值，服务端据此打包同步。</p>
     */
    public static final java.util.List<String> VISUALIZER_RULES = java.util.List.of(
            "sculkRangeVisualizer", "enderDragonPathVisualizer",
            "witherBreakRangeVisualizer", "enderDragonBreakRangeVisualizer",
            "mobSpawnVisualizer", "spawnStatsVisualizer", "spawnRangeVisualizer",
            "villagerBindingVisualizer",
            "worldEaterHelper", "explosionVisualizer",
            "itemMergeRangeVisualizer",
            "b36TargetPreview", "projectileTrajectoryVisualizer",
            "motionVisualizer",
            "aggroVisualizer", "aggroRangeVisualizer",
            "sweepRangeVisualizer", "dropCountVisualizer", "itemRetentionVisualizer",
            "healthChangeVisualizer", "illegalBlockVisualizer",
            "lightningRangeVisualizer",
            "itemFlowTracker");

    /**
     * 不是“可视化”，但同样需要让客户端知道开关的规则。
     *
     * <p>{@code flexiblePlacementCorners} 的效果全部发生在客户端（改的是客户端 mod 的行为）；
     * {@code chorusFlowerBonemeal} 是服务端逻辑，但客户端在右键预测时要调用
     * {@code isValidBonemealTarget}，必须读到与服务器一致的开关。专用服务器上客户端读不到
     * 服务端的静态字段，所以 {@link SogRuleSyncService} 会把它们和可视化规则一起打包下发。</p>
     */
    public static final java.util.List<String> CLIENT_SYNC_RULES = java.util.List.of(
            "flexiblePlacementCorners", "chorusFlowerBonemeal", "customBeaconEffects",
            "portableBeacon", "beaconEffectFilterMode",
            "beaconEffectBlacklist", "beaconEffectWhitelist");

    /** 按规则名读某个可视化规则当前的开关值（名字不认识时返回 false）。 */
    public static boolean visualizerRuleValue(String name) {
        switch (name) {
            case "sculkRangeVisualizer":
                return sculkRangeVisualizer;
            case "enderDragonPathVisualizer":
                return enderDragonPathVisualizer;
            case "witherBreakRangeVisualizer":
                return witherBreakRangeVisualizer;
            case "enderDragonBreakRangeVisualizer":
                return enderDragonBreakRangeVisualizer;
            case "mobSpawnVisualizer":
                return mobSpawnVisualizer;
            case "spawnStatsVisualizer":
                return spawnStatsVisualizer;
            case "spawnRangeVisualizer":
                return spawnRangeVisualizer;
            case "villagerBindingVisualizer":
                return villagerBindingVisualizer;
            case "worldEaterHelper":
                return worldEaterHelper;
            case "explosionVisualizer":
                return explosionVisualizer;
            case "itemMergeRangeVisualizer":
                return itemMergeRangeVisualizer;
            case "b36TargetPreview":
                return b36TargetPreview;
            case "projectileTrajectoryVisualizer":
                return projectileTrajectoryVisualizer;
            case "motionVisualizer":
                return motionVisualizer;
            case "aggroVisualizer":
                return aggroVisualizer;
            case "aggroRangeVisualizer":
                return aggroRangeVisualizer;
            case "sweepRangeVisualizer":
                return sweepRangeVisualizer;
            case "dropCountVisualizer":
                return dropCountVisualizer;
            case "itemRetentionVisualizer":
                return itemRetentionVisualizer;
            case "healthChangeVisualizer":
                return healthChangeVisualizer;
            case "illegalBlockVisualizer":
                return illegalBlockVisualizer;
            case "lightningRangeVisualizer":
                return lightningRangeVisualizer;
            case "itemFlowTracker":
                return itemFlowTracker;
            default:
                return false;
        }
    }

    /** 本模组注册的全部规则名，用于在核心 manager 的观察器回调里过滤。 */
    public static final Set<String> RULE_NAMES = Set.of(
            "copperOxidationDisabled", "dispenserCopperScrape", "dispenserTilling",
            "dispenserPlanting", "dispenserBerryHarvest", "chorusFlowerBonemeal", "amethystBudGrowthLimit",
            "iceSnowMeltDisabled", "forceThawLightLevel", "iceInstantMelt",
            "snowGenerationDisabled", "snowGolemSnowDisabled", "thunderDisabled", "chickenJockeyDisabled",
            "silkTouchReinforcedDeepslate", "silkTouchSuspicious", "sculkShriekerLevel",
            "sculkShriekerLevelLocked", "wardenSpawnDisabled", "nitwitSpawnDisabled", "nitwitBedDisabled",
            "tintedGlassEasyCraft", "easyElytraCraft", "customBeaconEffects",
            "beaconEffectFilterMode", "beaconEffectBlacklist", "beaconEffectWhitelist",
            "portableBeacon",
            "lightningTurnsClay", "snowCraftable", "shearsNoDurability", "elytraNoDurability",
            "woodStrippingRecipe",
            "fakePlayerToolsOnly", "sculkRangeVisualizer",
            "enderDragonPathVisualizer", "witherBreakRangeVisualizer", "enderDragonBreakRangeVisualizer",
            "mobSpawnVisualizer", "spawnStatsVisualizer", "spawnRangeVisualizer",
            "villagerBindingVisualizer", "worldEaterHelper", "explosionVisualizer",
            "itemMergeRangeVisualizer",
            "b36TargetPreview", "projectileTrajectoryVisualizer",
            "motionVisualizer",
            "aggroVisualizer", "aggroRangeVisualizer",
            "sweepRangeVisualizer", "dropCountVisualizer", "itemRetentionVisualizer",
            "healthChangeVisualizer", "illegalBlockVisualizer", "lightningRangeVisualizer",
            "itemFlowTracker",
            "fakePlayerRenderSettings", "flexiblePlacementCorners", "visualFeedback",
            "pearlTeleport", "giantOldAi");

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

    /** 自定义信标效果的黑/白名单模式。 */
    public enum BeaconEffectFilterMode {
        /** 黑名单：名单里的效果不能选，其余全部可以选（默认）。 */
        BLACKLIST,
        /** 白名单：只有名单里的效果能选；名单为空时回退到原版信标可用的六种效果。 */
        WHITELIST
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

    /**
     * 信标效果黑白名单的校验器：把输入规范成「逗号分隔、去空白、全部小写、去重、按字典序」
     * 的效果 id 串，并丢掉不是合法 {@code ResourceLocation} 的项，于是无论从 {@code /carpet}、
     * {@code /sogcarpet beaconfilter} 还是选择界面进来，存下来的格式都一致。
     */
    public static final class BeaconFilterListValidator extends Validator<String> {
        @Override
        public String validate(CommandSourceStack source, CarpetRule<String> rule, String value, String input) {
            return cn.blockforge.generated.sogcarpet.SogBeaconFilter.normalize(value);
        }

        @Override
        public String description() {
            return "comma separated effect ids, e.g. minecraft:night_vision,minecraft:bad_omen";
        }
    }
}
