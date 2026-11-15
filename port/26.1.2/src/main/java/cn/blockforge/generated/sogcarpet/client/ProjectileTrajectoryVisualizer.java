package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.SogPearlTeleport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * “投掷物轨迹可视化”的客户端实现（真实轨迹 + 轨迹预览）。
 *
 * <p>所有可投掷实体——箭、光灵箭、三叉戟、雪球、鸡蛋、末影珍珠、喷溅/滞留药水、附魔之瓶、
 * 火球与风弹、烟花火箭、末影之眼、鱼钩、羊驼唾沫、潜影贝导弹等（凡是
 * {@link Projectile} 的子类）——投出后，客户端每 tick 采集它真实的位置，把轨迹连成折线，
 * 末端标出它现在的位置与前进方向；投掷物落地停住或消失后，整条轨迹约
 * {@value #EXPIRE_TICKS} tick（5 秒）内淡出并清除。</p>
 *
 * <p><b>轨迹预览</b>（{@link ProjectileBallistics}）：把<b>还没发生/还没走到</b>的那一段用虚线
 * 提前画出来，共三路：</p>
 * <ol>
 *   <li><b>未投之前</b>：手里拿着可投掷物（雪球、鸡蛋、末影珍珠、附魔之瓶、喷溅/滞留药水、
 *       风弹、弓、弩、三叉戟）时，从眼睛沿视线按该物品的原版初速模拟一条弹道，标出落点与
 *       距离；弓按当前拉弓蓄力换算（没在拉弓时按满弓预览）。</li>
 *   <li><b>飞行中未经过的那段</b>：对附近最近的几枚飞行中投掷物，用它们当前速度加上
 *       {@link MotionPhysics} 的 g/f 外推剩余弹道——和“速度/动量可视化”画外力箭用的是同一套
 *       物理参数，因此两处对同一枚投掷物给出的预测一致。</li>
 *   <li><b>悬停的远传珍珠</b>（“简易珍珠远距传送”联动）：悬停珍珠速度是 0，它下一步往哪飞
 *       只由服务端累积的待施加速度决定；这份速度已经通过“速度/动量可视化”的冲量通道
 *       （{@code MotionInfoPayload}）同步到客户端，这里直接拿它作为初速外推落地位置，
 *       于是叠加每一矛之后都能立刻看到“会落到哪”。拿不到服务端数据（纯客户端）时跳过这一路，
 *       前两路预览照常工作。</li>
 * </ol>
 *
 * <p><b>服务器适配 / 渲染压力</b>：采样、模拟与绘制全部在客户端完成；服务端只通过既有的规则
 * 同步把开关告诉客户端，并为“悬停珍珠的待施加速度”下发那条很小的冲量快照。客户端这边：只跟踪
 * 玩家 {@value #TRACK_RANGE} 格以内、本地真正看得见的实体；单条轨迹的采样点、同屏同时跟踪的
 * 条数、一帧提交的图元数都有上限；预览只给最近的 {@value #MAX_FLIGHT_PREVIEWS} 枚飞行中投掷物
 * （每 tick 重算，单条最多 {@value #FLIGHT_PREVIEW_TICKS} tick），悬停珍珠只在服务端下发的累积
 * 速度变化时重算，因此不会把客户端拖垮。所以它在专用服务器、单机、局域网下
 * 表现一致。渲染开关与“规则开关”走既有的可视化总控（Ctrl+O / Ctrl+V，逐玩家清单，服务端也能用
 * {@code /sogcarpet visualizer} 配置）。</p>
 */
public final class ProjectileTrajectoryVisualizer {
    /** 本规则在总控里的名字。 */
    private static final String RULE = "projectileTrajectoryVisualizer";

    /** 玩家周围多少格以内的投掷物才画轨迹（收紧到 96，远处的小点轨迹没有观看价值）。 */
    private static final double TRACK_RANGE = 96.0D;
    private static final double TRACK_RANGE_SQR = TRACK_RANGE * TRACK_RANGE;
    /**
     * 相邻两个采样点至少移动这么多格才记一个新点。
     *
     * <p>以前是 0.25 格：飞得慢的投掷物（药水、到达顶点前后的雪球）会连续好几个 tick 不记点，
     * 末端的直线段于是跨过多个 tick、把中间的真实位置“抄近路”跳过去；等累积到 0.25 格突然
     * 记一个新点，整段再从直线变成曲线——玩家看到的就是“先折线、后曲线”的跳变。
     * 现在只要真的在动（超过 {@link #STILL_EPSILON}）就每 tick 记一个点，轨迹始终有当前的
     * 采样点，末端那一段由 {@link Tail} 每帧平滑计算，不再靠“攒够 0.25 格”决定形状。</p>
     */
    private static final double MIN_STEP = 0.01D;
    private static final double MIN_STEP_SQR = MIN_STEP * MIN_STEP;
    /** 位置变化小于这个平方长度就算“停住”（用于判定落地/卡住）。 */
    private static final double STILL_EPSILON = 1.0E-5D;
    /** 单条轨迹最多保留多少个采样点，超出的从最旧的开始丢。 */
    private static final int MAX_POINTS = 96;
    /** 同时最多跟踪多少枚投掷物（就近优先）。 */
    private static final int MAX_TRAILS = 48;
    /** 投掷物停住或消失后，轨迹再保留多少 tick（5 秒 = 100 tick）后彻底移除。 */
    private static final int EXPIRE_TICKS = 100;
    /** 轨迹最后多少 tick 用来淡出。 */
    private static final int FADE_TICKS = 20;
    /**
     * 一帧最多画多少图元。
     *
     * <p>头部圆点、方向箭头与末端动态曲线现在不占这个预算（它们固定每帧绘制，见 {@link Tail}），
     * 这里只统计“已经走过去”的静态曲线和预览虚线；上限提高是为了让一条几千格的远传珍珠预览
     * 也能把落点标记画出来。</p>
     */
    private static final int MAX_PRIMITIVES = 6000;
    /** 单条静态轨迹最多画多少段线（超长轨迹按弧长合并相邻弦，形状不变）。 */
    private static final int MAX_TRAIL_SEGMENTS = 192;
    /**
     * 单条轨迹至少保留多少段（图元预算被分掉时也不会整条消失）。
     *
     * <p>以前条目一多就把 {@link #MAX_PRIMITIVES} 用完，后面的轨迹一条线都不画——
     * 只剩末端十字箭头，也就是“投掷轨迹不显示”。现在按“还剩多少预算 / 还剩几条轨迹”分配，
     * 每条轨迹至少有这么多段。</p>
     */
    private static final int MIN_TRAIL_SEGMENTS = 48;
    /**
     * 相邻两条画出来的弦最长多少格。
     *
     * <p>曲线点本来就很密（目标每小段 ≤0.5 格），这里只是兜住“远传珍珠一秒飞几千格”那种曲线：
     * 曲率极小时弦再长也贴着曲线，合并相邻弦带来的形状差远小于一个像素（实测量级 0.001 格）。</p>
     */
    private static final double MAX_CHORD_LENGTH = 1.0D;
    /** 一条预览最多画多少段虚线；弧很长时自动加长“画一段/空一段”的长度，仍然画到落点。 */
    private static final int MAX_PREVIEW_DASHES = 96;
    /** 关闭虚线时一条预览最多画多少段实线（等距抽样，不会只画到一半）。 */
    private static final int MAX_PREVIEW_SEGMENTS = 192;

    /** 预览只给最近的这几枚飞行中投掷物、只在这个距离内绘制。 */
    private static final int MAX_FLIGHT_PREVIEWS = 4;
    private static final double PREVIEW_RANGE = 48.0D;
    private static final double PREVIEW_RANGE_SQR = PREVIEW_RANGE * PREVIEW_RANGE;
    /** 飞行中投掷物最多外推多少 tick。 */
    private static final int FLIGHT_PREVIEW_TICKS = 80;
    /** 悬停远传珍珠最多外推多少 tick（按累积速度它能飞很远）。 */
    private static final int HOVERED_PREVIEW_TICKS = ProjectileBallistics.MAX_PREVIEW_TICKS;
    /** 同时给几颗悬停珍珠画落点预览。 */
    private static final int MAX_HOVERED_PREVIEWS = 4;
    /** 速度小于这个平方长度时不画飞行预览（已经停住了）。 */
    private static final double MIN_FLIGHT_SPEED_SQR = 0.01D;

    /** 轨迹线宽。 */
    private static final float LINE_WIDTH = 3.0F;
    /** 起点粗点的屏幕像素尺寸。 */
    private static final float START_POINT_SIZE = 7.0F;
    /** 当前位置点与箭头线宽。 */
    private static final float HEAD_POINT_SIZE = 5.0F;
    private static final float ARROW_WIDTH = 3.5F;
    /**
     * 末端箭头：从当前位置稍微往前一点开始画，长度也比以前长。
     *
     * <p>以前箭头从实体中心起、只有 1.2 格长，箭头尖（{@code Gizmos.arrow} 的三角锥）只有
     * 0.12 格，远处不足一个像素；而且箭杆起点埋在投掷物模型里，深度测试会时隐时现，看起来
     * 就是“箭头不停闪烁抽搐”。现在箭杆离开实体 0.35 格、总长 2.2 格，箭头尖约 0.19 格，
     * 既不会被模型遮住，也不会因为太小而忽隐忽现。</p>
     */
    private static final double HEAD_ARROW_GAP = 0.35D;
    private static final double HEAD_ARROW_LENGTH = 1.85D;
    /**
     * 末端标记的尺寸按“到相机的距离”缩放，让它在屏幕上的大小基本恒定。
     *
     * <p>固定世界尺寸的箭头顶端（{@code Gizmos.arrow} 的锥体只有线长的 10%）在远处不足两个像素，
     * 每帧朝向/距离稍微一变就在“看得见/看不见”之间跳，这是“箭头闪烁”的主要来源。改成自己用
     * 线段画箭头（线宽是屏幕像素，不会缩没），并按距离放大，远处也稳定。</p>
     */
    private static final double MARKER_SCALE = 0.022D;
    private static final double MARKER_MIN_SIZE = 0.35D;
    private static final double MARKER_MAX_SIZE = 1.80D;
    /** 末端标记沿“朝相机”方向前移多少格：沿视线偏移，画面上看不出位移，但能脱离投掷物模型。 */
    private static final double MARKER_CAMERA_PUSH = 0.45D;
    /**
     * 末端这一小截（格）改成 always-on-top 绘制。
     *
     * <p>曲线的最后半格正好埋在投掷物本体里，深度测试会随视角每帧把它切掉一部分——画面上就是
     * “末端一直在抽搐/闪烁”。整条轨迹仍然照常做深度测试（钻进山里就该看不见），只有贴着头端的
     * 这一小截抬到最上层，和末端十字箭头保持一致。</p>
     */
    private static final double TAIL_ON_TOP_LENGTH = 0.6D;
    /**
     * 一次位移相对上一步转过的角度超过这个度数、且位移本身够长时，判定为“位置被纠正/瞬移”，
     * 这一段不画线（而是断开），避免出现一条横跨半屏的假轨迹。
     */
    private static final double BREAK_TURN_DEGREES = 62.0D;
    private static final double BREAK_MIN_STEP = 1.2D;
    /**
     * 位移比“最近几步的典型长度”大这么多倍、且本身超过 {@link #BREAK_MIN_JUMP} 格时，
     * 同样判定为位置纠正（方向未必变化，但一下子窜出去很远）。
     *
     * <p>两个判据都要：只看方向会漏掉“沿着飞行方向被推远”，只看长度会误伤悬停珍珠刚放飞那一下
     * （那一步本来就该有几十格），所以长度判据要求“之前已经有一段正常的匀速飞行”。</p>
     */
    private static final double BREAK_JUMP_RATIO = 4.0D;
    private static final double BREAK_MIN_JUMP = 3.0D;
    /** “典型步长”指数平滑的保留比例。 */
    private static final double TYPICAL_STEP_KEEP = 0.7D;
    /** 尖端方向开始/全部采用“几何方向”的步长（格）。 */
    private static final double DIRECTION_MIN_STEP = 0.08D;
    private static final double DIRECTION_FULL_STEP = 0.45D;
    /** 轨迹抬高一点，避免压在方块顶面上看不清。 */
    private static final double Y_OFFSET = 0.08D;
    /**
     * 末端动态曲线（{@link Tail}）的分段参数。
     *
     * <p><b>只画一段</b>：静态曲线烘到 {@code S[n-1]}（四个控制点 {@code S[n-3..n]} 全部已知，
     * 烘完永不改变），{@link Tail} 只负责正在生长的最后一段 {@code S[n-1] -> head(partialTick)}。
     * 尖端在 60fps 下连续前进，而且<b>已经走过的部分一个顶点都不会动</b>——这是消除
     * “末端抽搐 / 先折线后曲线”的关键：以前末端那段用移动的头端当第四控制点，
     * {@code partialTick = 0} 时第四控制点正好等于第三控制点，三次样条退化成直线，
     * 于是每个 tick 边界上刚走过的一段先被拉直、再慢慢弯回来（20 次/秒）。</p>
     */
    private static final double TAIL_TARGET_STEP = 0.5D;
    /**
     * 方向平滑系数：每 tick 保留上一 tick 方向的比例。
     *
     * <p>0.45 相当于约 2 tick 的时间常数。弹道本身是连续的，物理方向的变化远慢于 20Hz，
     * 所以这点延迟看不出来，却能滤掉客户端两次位置同步之间的抖动。</p>
     */
    private static final double HEADING_SMOOTH_KEEP = 0.45D;
    /** 预览线的线宽与落点标记尺寸（都比真实轨迹细，一眼能分开）。 */
    private static final float PREVIEW_WIDTH = 2.0F;
    private static final float PREVIEW_IMPACT_SIZE = 6.0F;
    /** 预览文字缩放。 */
    private static final float PREVIEW_TEXT_SCALE = 1.0F;
    /**
     * 平滑曲线：短段至少再插几段，长段按长度自适应加密。
     *
     * <p>弹道本来只有每 tick 一个采样点（初速 3 格/tick 的弩箭每三格才有一个点），直接连起来
     * 就是折线。这里按向心 Catmull-Rom 加密：曲线严格穿过原来的每一个采样点，落点与命中点都
     * 不变，只是把中间的折角抹平。</p>
     */
    private static final int CURVE_SUBDIVISIONS = 6;
    /**
     * 离相机近于这个距离（沿视线方向，格）的线段直接不画。
     *
     * <p>原版 gizmo 的线渲染器会把跨越近裁剪面的线段“夹”到裁剪面上
     * （{@code GizmoFeatureRenderer#buildLines} 里对 {@code z > -0.05} 的端点做 lerp），
     * 夹完的端点在做透视除法时会飞到屏幕外，画出来就是一条横贯半屏的直线——也就是玩家看到的
     * “曲线突然凸起一块/中间横着一道”。投掷物从身边擦过、以及手持预览从眼睛出发时都会命中这种情况。
     * 这里直接把这一段丢掉：既不会出现毛刺，观感上也看不出少了什么。</p>
     */
    private static final double NEAR_PLANE_SKIP = 0.75D;
    /** 线段到相机的最近距离小于这个值也不画（贴着相机几乎垂直于视线的段会闪）。 */
    private static final double NEAR_CAMERA_SKIP = 0.6D;
    private static final double NEAR_CAMERA_SKIP_SQR = NEAR_CAMERA_SKIP * NEAR_CAMERA_SKIP;
    /**
     * 加密后的目标子段长度（格）。
     *
     * <p>“远传珍珠”累积速度的单位就是「格/tick」，叠几次之后每 tick 的采样点可能相隔几十格。
     * 以前用「线段长于 4 格就当瞬移、不平滑」的保护，于是这一类轨迹（以及任何高速投掷物）
     * 直接退回折线。现在改成按段长自适应细分：段越长插得越密，每一小段都落在这个长度附近。</p>
     */
    private static final double SMOOTH_TARGET_STEP = 0.5D;
    /**
     * 单个采样段最多细分几段。
     *
     * <p>与 {@link #MAX_POINTS} 一起决定单条轨迹的点数上限（96 × 8 = 768），所以不再需要
     * “总点数超预算就整体按比例降细分”——那种降法依赖总点数，点数每 tick 变化，
     * 于是已经画过的曲线每 tick 都被重新采样一遍。</p>
     */
    private static final int MAX_SEGMENT_STEPS = 8;
    /** 段短于这个长度就不再细分：采样点本身已经比一个像素还密，直连就是曲线。 */
    private static final double MIN_SMOOTH_LENGTH = 0.05D;
    /**
     * “瞬移”判定的绝对下限（格）：短于这个长度的段永远不算瞬移。
     *
     * <p>真正的判据在 {@link #looksLikeTeleport}：还要比相邻段长 {@value #TELEPORT_RATIO} 倍以上。
     * 速度不设上限之后，整条轨迹每段都可能是几千格（远传珍珠正常飞行），那种情况是连续高速、
     * 不是瞬移，必须照样画成曲线。</p>
     */
    private static final double MAX_SMOOTH_SPAN = 128.0D;
    /** 一段比相邻段长这么多倍、且超过 {@link #MAX_SMOOTH_SPAN} 时，才当作真瞬移保留折线。 */
    private static final double TELEPORT_RATIO = 8.0D;
    /**
     * 不再按“尖角”退回折线。
     *
     * <p>以前转角超过 50° 就把这一段画成直线。但“这一段要不要平滑”依赖后面一个采样点，
     * 末端总是在“暂时还没有下一个点 → 画直线”和“下一个点到了 → 改画曲线”之间来回切换，
     * 正是“投掷轨迹先由折线变为曲线”的跳变来源。向心 Catmull-Rom（{@code alpha = 0.5}）
     * 本身保证同一段内不出现尖点与自交，砸地弹开的关节画成圆弧同样成立，因此这里统一走曲线，
     * 只有真瞬移（{@link #looksLikeTeleport}）才保留直线。</p>
     */
    /** 预览虚线的“画一段 / 空一段”长度（格）。按弧长算，点加密后虚线段长度才不变。 */
    private static final double PREVIEW_DASH_LENGTH = 2.0D;

    /** 起点：绿。 */
    private static int COLOR_START = 0xFF5CFF9D;
    /** 轨迹线：青。 */
    private static int COLOR_PATH = 0xFF43D6FF;
    /** 当前位置与末端箭头：琥珀。 */
    private static int COLOR_HEAD = 0xFFFFC83D;
    /** 预览虚线：淡紫。 */
    private static int COLOR_PREVIEW = 0xB0B47BFF;
    /** 预览落点标记与文字：粉。 */
    private static int COLOR_PREVIEW_IMPACT = 0xFFFF6BD5;

    /** 预览相关的选项（每次 tick 从配置刷新）。 */
    private static boolean previewHeld = true;
    private static boolean previewFlight = true;
    private static boolean previewHovered = true;
    private static boolean previewDashed = true;
    private static boolean previewText = true;

    /** 从用户配置刷新颜色（没有配置时回落到默认值）。 */
    private static void refreshColors() {
        COLOR_START = VisualizerConfig.color(RULE, "start", 0xFF5CFF9D);
        COLOR_PATH = VisualizerConfig.color(RULE, "path", 0xFF43D6FF);
        COLOR_HEAD = VisualizerConfig.color(RULE, "head", 0xFFFFC83D);
        COLOR_PREVIEW = VisualizerConfig.color(RULE, "preview", 0xB0B47BFF);
        COLOR_PREVIEW_IMPACT = VisualizerConfig.color(RULE, "previewImpact", 0xFFFF6BD5);
    }

    /** 从用户配置刷新预览开关。 */
    private static void refreshOptions() {
        previewHeld = VisualizerConfig.option(RULE, "previewHeld", true);
        previewFlight = VisualizerConfig.option(RULE, "previewFlight", true);
        previewHovered = VisualizerConfig.option(RULE, "previewHovered", true);
        previewDashed = VisualizerConfig.option(RULE, "previewDashed", true);
        previewText = VisualizerConfig.option(RULE, "previewText", true);
    }

    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    /** 每 tick 复用的“可见投掷物”缓冲，避免每 tick 新建 ArrayList。 */
    private static final List<Projectile> VISIBLE_SCRATCH = new ArrayList<>();
    /** 与 {@link #VISIBLE_SCRATCH} 对应的平方距离（就地部分选择时一起换位）。 */
    private static double[] DISTANCE_SCRATCH = new double[16];

    private static final Map<Integer, Trail> TRAILS = new HashMap<>();
    private static ClientLevel lastLevel;
    /**
     * 每 tick 复用的“按距离排序”缓冲：最近的那几条先画，预算用完时先丢远的。
     *
     * <p>以前直接按 {@link HashMap} 的迭代顺序画：同一个 pearl 的箭头会随着其它实体
     * 增删导致顺序变化，在“画 / 不画”之间来回跳——也就是玩家看到的“箭头不停闪烁”。</p>
     */
    private static final List<Trail> ORDER_SCRATCH = new ArrayList<>();
    private static final Comparator<Trail> BY_DISTANCE =
            Comparator.comparingDouble(trail -> trail.distanceSqr);
    /** 渲染线程复用的末端曲线点缓冲。 */
    private static final List<Vec3> TAIL_SCRATCH = new ArrayList<>();

    /** 手持预览的缓存（渲染帧按当前准星算，输入没变就复用）。 */
    private static volatile HeldPreview heldCache;

    private ProjectileTrajectoryVisualizer() {
    }

    /** 客户端入口调用一次：注册 tick 采样与渲染回调。 */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ProjectileTrajectoryVisualizer::clientTick);
        LevelRenderEvents.BEFORE_GIZMOS.register(ProjectileTrajectoryVisualizer::renderGizmos);
    }

    private static void clientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (!VisualizerState.isActive(RULE) || level == null) {
            reset();
            return;
        }
        refreshOptions();
        if (level != lastLevel) {
            resetTracking();
            lastLevel = level;
        }
        Vec3 eye = client.player == null ? null : client.player.position();
        updateTrails(level, eye);
        snapshot = buildSnapshot();
    }

    private static void reset() {
        resetTracking();
        lastLevel = null;
        clearHeldPreview();
        snapshot = Snapshot.EMPTY;
    }

    private static void resetTracking() {
        TRAILS.clear();
        clearHeldPreview();
    }

    private static void clearHeldPreview() {
        heldCache = null;
    }

    /** 这一 tick 认领/更新看得见的投掷物轨迹，并让停住或消失的轨迹倒计时。 */
    private static void updateTrails(ClientLevel level, Vec3 eye) {
        for (Trail trail : TRAILS.values()) {
            trail.seen = false;
        }
        // 复用缓冲：这两个集合以前每 tick 都新建一次（60 秒就是上千次分配）。
        List<Projectile> visible = VISIBLE_SCRATCH;
        visible.clear();
        double[] distances = DISTANCE_SCRATCH;
        int count = 0;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof Projectile projectile) || projectile.isRemoved()) {
                continue;
            }
            double distanceSqr = eye == null ? 0.0D
                    : projectile.position().distanceToSqr(eye);
            if (eye != null && distanceSqr > TRACK_RANGE_SQR) {
                // 超出追踪范围：不参与本 tick 的处理，也不进“就近”候选。
                Trail far = TRAILS.get(projectile.getId());
                if (far != null) {
                    far.clearPreview();
                }
                continue;
            }
            if (count == distances.length) {
                distances = java.util.Arrays.copyOf(distances, Math.max(16, count * 2));
            }
            distances[count] = distanceSqr;
            visible.add(projectile);
            count++;
        }
        DISTANCE_SCRATCH = distances;
        // 就近优先：只有“预览预算”真的关心顺序（前 MAX_FLIGHT_PREVIEWS + MAX_HOVERED_PREVIEWS 个），
        // 所以这里用部分选择把最近的那几个挑到最前面，而不是对整个列表排序（n 大时差一个量级）。
        int nearest = Math.min(count, MAX_FLIGHT_PREVIEWS + MAX_HOVERED_PREVIEWS);
        for (int slot = 0; slot < nearest; slot++) {
            int best = slot;
            for (int i = slot + 1; i < count; i++) {
                if (distances[i] < distances[best]) {
                    best = i;
                }
            }
            if (best != slot) {
                double tmpDistance = distances[slot];
                distances[slot] = distances[best];
                distances[best] = tmpDistance;
                Projectile tmpProjectile = visible.get(slot);
                visible.set(slot, visible.get(best));
                visible.set(best, tmpProjectile);
            }
        }
        int flightBudget = MAX_FLIGHT_PREVIEWS;
        int hoveredBudget = MAX_HOVERED_PREVIEWS;
        for (int index = 0; index < count; index++) {
            Projectile projectile = visible.get(index);
            Vec3 pos = projectile.position();
            double distanceSqr = index < nearest ? distances[index]
                    : (eye == null ? 0.0D : pos.distanceToSqr(eye));
            Trail trail = TRAILS.get(projectile.getId());
            if (trail != null && !trail.belongsTo(projectile)) {
                // 实体 id 被回收：这条轨迹属于上一只已经消失的投掷物，不能继续用，否则画面上会
                // 同时出现好几条互相不相干的虚线。
                trail = null;
            }
            if (trail == null) {
                if (TRAILS.size() >= MAX_TRAILS && !evictTrail()) {
                    // 实在腾不出位置（全部都在飞且都不远）：这一只跳过，下个 tick 再试。
                    continue;
                }
                trail = new Trail(projectile.getId(), projectile.getUUID(), pos);
                TRAILS.put(projectile.getId(), trail);
            }
            trail.seen = true;
            trail.distanceSqr = distanceSqr;
            boolean hovered = SogPearlTeleport.matchesName(projectile.getCustomName());
            trail.update(pos, hovered);
            updatePreview(level, projectile, trail, distanceSqr, hovered,
                    hoveredBudget > 0, flightBudget > 0);
            if (hovered && hoveredBudget > 0 && trail.hasPreview()) {
                hoveredBudget--;
            } else if (!hovered && flightBudget > 0 && distanceSqr <= PREVIEW_RANGE_SQR
                    && trail.hasPreview()) {
                flightBudget--;
            }
        }
        Iterator<Trail> iterator = TRAILS.values().iterator();
        while (iterator.hasNext()) {
            Trail trail = iterator.next();
            if (!trail.seen) {
                trail.missingTicks++;
                // 投掷物已经不在可见列表里（出视野 / 已移除）：虚线预览立即清掉，真实轨迹仍按
                // EXPIRE_TICKS 淡出，这样快速转头时不会看到多条重叠的预览。
                trail.clearPreview();
            }
            if (trail.missingTicks > EXPIRE_TICKS) {
                iterator.remove();
            }
        }
    }

    /**
     * 轨迹数达到上限时腾一个位置出来。
     *
     * <p>以前是直接 {@code continue}（拒绝给新投掷物建轨迹）：同屏已经攒了
     * {@value #MAX_TRAILS} 条正在淡出的旧轨迹时，新投出的那一枚<b>连箭头都没有</b>。
     * 现在优先淘汰“已经看不见且最远”的那条，全都还看得见时淘汰最远的。</p>
     *
     * @return 是否成功腾出位置
     */
    private static boolean evictTrail() {
        Trail victim = null;
        for (Trail candidate : TRAILS.values()) {
            if (candidate.seen) {
                continue;
            }
            if (victim == null || candidate.distanceSqr > victim.distanceSqr) {
                victim = candidate;
            }
        }
        if (victim == null) {
            for (Trail candidate : TRAILS.values()) {
                if (victim == null || candidate.distanceSqr > victim.distanceSqr) {
                    victim = candidate;
                }
            }
        }
        if (victim == null) {
            return false;
        }
        TRAILS.remove(victim.id);
        return true;
    }

    /**
     * 三路预览里“每个投掷物”的那两路：悬停远传珍珠按服务端累积速度外推，其余飞行中的按当前速度外推。
     *
     * <p>飞行中的每 tick 重算（不重算的话虚线会落后真实位置一大截，看着一顿一顿）；悬停珍珠原地不动，
     * 只有服务端下发的累积速度变了才重算，所以“叠一矛就立刻更新、平时零开销”。预算用完或选项
     * 关掉就把旧的预览清掉，不留残影。</p>
     */
    private static void updatePreview(ClientLevel level, Projectile projectile, Trail trail,
            double distanceSqr, boolean hovered, boolean hoveredBudget, boolean flightBudget) {
        if (hovered) {
            if (!previewHovered || !hoveredBudget) {
                trail.clearPreview();
                return;
            }
            Vec3 velocity = MotionImpulseState.get(projectile);
            if (velocity == null || velocity.lengthSqr() < MIN_FLIGHT_SPEED_SQR) {
                // 还没有叠加任何轴速度（或服务端数据过期）：画不出来，等下一次冲量。
                trail.clearPreview();
                return;
            }
            if (trail.hasPreview() && trail.matchesPreviewVelocity(velocity)) {
                // 累积速度没变，落点也不会变，直接复用上一条预览。
                return;
            }
            trail.setPreview(ProjectileBallistics.simulate(level, projectile.position(),
                    velocity, ProjectileBallistics.THROWABLE_GRAVITY,
                    ProjectileBallistics.AIR_DRAG, HOVERED_PREVIEW_TICKS, projectile), true,
                    velocity);
            return;
        }
        Vec3 speed = projectile.getDeltaMovement();
        if (!previewFlight || !flightBudget || distanceSqr > PREVIEW_RANGE_SQR
                || speed.lengthSqr() < MIN_FLIGHT_SPEED_SQR) {
            trail.clearPreview();
            return;
        }
        ProjectileBallistics.Profile profile = ProjectileBallistics.entityProfile(projectile);
        if (profile == null) {
            trail.clearPreview();
            return;
        }
        trail.setPreview(ProjectileBallistics.simulate(level, projectile.position(), speed,
                profile.gravity(), profile.drag(), FLIGHT_PREVIEW_TICKS, projectile), false,
                Vec3.ZERO);
    }

    /**
     * 手持投掷物的预览：从眼睛沿视线飞出，标出落点与距离。
     *
     * <p><b>时间基（为什么不再抖）</b>：模拟用的起点 / 朝向 / 速度全部取<b>本 tick 末</b>的值
     * （{@code getEyePosition(1.0F)} / {@code getViewVector(1.0F)} / {@code getKnownMovement()}），
     * 和原版真正发射时用的是同一时刻，所以落点与真实轨迹一致；缓存键也只用这些 tick 对齐的输入，
     * 一个 tick 内最多重算一次。以前起点取 {@code getEyePosition(partialTick)}（上一 tick 到本 tick
     * 的插值点）却配本 tick 的速度，两套时间基差着一整个 tick——飞行时一格能飞两三格，整条预览
     * 就每 tick 前后错开一整 tick 的位移、20Hz 来回跳，也就是“预测飘忽不定”。
     * 帧间的平滑改由 {@link #heldShift} 在渲染时整体平移完成。</p>
     */
    private static HeldPreview computeHeldPreview(Minecraft client) {
        if (!previewHeld || client == null || client.level == null) {
            return null;
        }
        LocalPlayer player = client.player;
        if (player == null) {
            return null;
        }
        ItemStack held = player.getMainHandItem();
        ProjectileBallistics.Profile profile = ProjectileBallistics.heldProfile(
                held, player.getTicksUsingItem(), player.isUsingItem());
        if (profile == null) {
            return null;
        }
        // 弓/弩没有弹药时不画，免得指一个打不出来的落点。
        if (("bow".equals(profile.kind()) || "crossbow".equals(profile.kind()))
                && player.getProjectile(held).isEmpty()) {
            return null;
        }
        // 弩没装填（也没在拉）时松手不会发射，不画预览。
        if ("crossbow".equals(profile.kind()) && !CrossbowItem.isCharged(held)
                && !player.isUsingItem()) {
            return null;
        }
        // 模拟统一用「tick 对齐」的输入：本 tick 的眼睛位置 / 朝向 / 速度。
        // 以前起点取 getEyePosition(partialTick)（上一 tick → 本 tick 的插值点），速度却用本 tick 的
        // getKnownMovement()，两套时间基差着一整个 tick：飞行时一格能飞两三格，于是整条预览每 tick
        // 前后错开一整 tick 的位移、20Hz 来回跳，玩家看到的就是“预测飘忽不定”。
        // 现在形状在 tick 内固定，位移只在渲染时按 partialTick 平移（见 heldShift）。
        Vec3 eyeTick = player.getEyePosition(1.0F);
        // 真实出生点：眼睛高度 - 0.1（原版 ThrowableItemProjectile / Projectile 共用的规则），
        // 不再往前推 0.3 格——那会让预览整体偏高、落点偏远。
        Vec3 start = eyeTick.subtract(0.0D, ProjectileBallistics.SPAWN_EYE_DROP, 0.0D);
        Vec3 look = player.getViewVector(1.0F);
        // 原版 shootFromRotation 会把投掷者自身的位移加进初速（站定不跳时只加水平分量），
        // 跑动中投掷的预览必须同样处理，否则与实际轨迹差一整段。
        Vec3 inherit = player.getKnownMovement();
        Vec3 initial = look.scale(profile.speed())
                .add(inherit.x, player.onGround() ? 0.0D : inherit.y, inherit.z);
        // 缓存键只用 tick 对齐的输入：一个 tick 内不再变化，所以飞行时也是每 tick 最多重算一次，
        // 而不是每帧重算（以前每帧 key 都在变）。
        long key = keyOf(start, look, initial, profile);
        if (heldCache != null && heldCache.key == key) {
            return heldCache;
        }
        ProjectileBallistics.Result result = ProjectileBallistics.simulate(client.level, start,
                initial, profile.gravity(), profile.drag(),
                ProjectileBallistics.MAX_PREVIEW_TICKS, player);
        if (result.points().size() < 2) {
            heldCache = null;
            return null;
        }
        Vec3 end = result.hit() != null
                ? result.hit()
                : result.points().get(result.points().size() - 1);
        HeldPreview computed = new HeldPreview(key, result.points(), result.hit(),
                result.blocked(), start.distanceTo(end), eyeTick);
        heldCache = computed;
        return computed;
    }

    /** 把浮点位置量化成一个 64 位键（量化误差远小于 1 像素，避免每帧抖动重建）。 */
    private static long keyOf(Vec3 start, Vec3 look, Vec3 initial, ProjectileBallistics.Profile profile) {
        long h = 1125899906842597L;
        h = h * 31 + quantize(start.x);
        h = h * 31 + quantize(start.y);
        h = h * 31 + quantize(start.z);
        h = h * 31 + quantize(look.x);
        h = h * 31 + quantize(look.y);
        h = h * 31 + quantize(look.z);
        h = h * 31 + quantize(initial.x);
        h = h * 31 + quantize(initial.y);
        h = h * 31 + quantize(initial.z);
        h = h * 31 + Double.hashCode(profile.gravity());
        h = h * 31 + Double.hashCode(profile.drag());
        h = h * 31 + Double.hashCode(profile.speed());
        return h * 31 + profile.kind().hashCode();
    }

    private static long quantize(double value) {
        // 1/2048 格 ≈ 0.0005，比一个像素还小得多。
        return Math.round(value * 2048.0D);
    }

    private static Snapshot buildSnapshot() {
        // 先按距离排序：预算不够时先丢远的，近处正在盯着的那一枚不会因为别的实体增删而忽隐忽现。
        List<Trail> ordered = ORDER_SCRATCH;
        ordered.clear();
        for (Trail trail : TRAILS.values()) {
            ordered.add(trail);
        }
        if (ordered.size() > 1) {
            ordered.sort(BY_DISTANCE);
        }
        List<Primitive> out = new ArrayList<>();
        List<Interp> interps = new ArrayList<>();
        List<Tail> tails = new ArrayList<>(ordered.size());
        // 第 1 轮：起点圆点 + 末端动态曲线。这两样是“每帧都要看到”的，不参与后面的图元预算。
        for (Trail trail : ordered) {
            appendStart(out, trail);
            tails.add(trail.createTail());
            if (trail.hasMovement()) {
                // 渲染时按 partialTick 在这两点之间插值，预览才不会每 tick 跳一下。
                interps.add(new Interp(trail.id, trail.prevTickPos, trail.tickPos));
            }
        }
        // 第 2 轮：已经走过的静态曲线（整条平滑曲线，末端一段留给动态 Tail）。
        // 预算按“每条轨迹至少 MIN_TRAIL_SEGMENTS 段”平均分配，条目再多也不会有轨迹一条线都画不出来。
        for (int index = 0; index < ordered.size(); index++) {
            if (out.size() >= MAX_PRIMITIVES) {
                break;
            }
            int remaining = ordered.size() - index;
            int fairShare = Math.max(MIN_TRAIL_SEGMENTS,
                    (MAX_PRIMITIVES - out.size()) / Math.max(1, remaining));
            appendCurve(out, ordered.get(index), Math.min(MAX_PRIMITIVES, out.size() + fairShare));
        }
        // 第 3 轮：还没走到的预览虚线（落点标记与文字在 appendPreview 里先加，预算再紧也保留）。
        for (Trail trail : ordered) {
            if (out.size() >= MAX_PRIMITIVES) {
                break;
            }
            appendTrailPreview(out, trail);
        }
        return new Snapshot(out, interps, tails);
    }
    /** 轨迹起点的粗点（世界固定，不做 partialTick 插值）。 */
    private static void appendStart(List<Primitive> out, Trail trail) {
        List<Vec3> points = trail.curvePoints();
        if (points.isEmpty()) {
            return;
        }
        float alpha = trail.alpha();
        out.add(Primitive.point(points.get(0), withAlpha(COLOR_START, alpha),
                Math.max(1.0F, START_POINT_SIZE * alpha)));
    }

    /**
     * 已经走过的静态轨迹，<b>不包含末端那一段</b>。
     *
     * <p>末端那一段由 {@link Tail} 每帧按 partialTick 重新平滑：如果把它也烘进静态曲线，那么
     * 每加一个新采样点，最后一段的形状就会从“直线头段”突然变成“曲线”，也就是玩家看到的
     * “投掷轨迹先由折线变为曲线”。</p>
     *
     * <p>只连<b>相邻</b>曲线点（不做按下标抽样）：抽样会把曲率大的地方拉直成弦，看起来就是
     * 一段凭空出现的“凸起”。段数超上限时按弧长合并相邻弦，跳过的长度受 {@link #MAX_CHORD_LENGTH}
     * 约束，所以永远不会切角。</p>
     *
     * @param limit 这条轨迹最多写多少个图元（由调用方按剩余预算分配）
     */
    private static void appendCurve(List<Primitive> out, Trail trail, int limit) {
        float alpha = trail.alpha();
        int pathColor = withAlpha(COLOR_PATH, alpha);
        float lineWidth = Math.max(1.0F, LINE_WIDTH * alpha);
        List<List<Vec3>> runs = trail.curveRuns();
        for (int runIndex = 0; runIndex < runs.size(); runIndex++) {
            List<Vec3> points = runs.get(runIndex);
            int size = points.size();
            if (size < 2) {
                continue;
            }
            // 顶多画 MAX_TRAIL_SEGMENTS 段：弦长按弧长自适应（曲率极小时长弦也贴着曲线）。
            double totalArc = 0.0D;
            for (int i = 1; i < size; i++) {
                totalArc += points.get(i - 1).distanceTo(points.get(i));
            }
            double chordCap = chordCapFor(totalArc);
            Vec3 last = points.get(0);
            double skipped = 0.0D;
            for (int i = 1; i < size; i++) {
                Vec3 next = points.get(i);
                skipped += last.distanceTo(next);
                if (i != size - 1 && skipped < chordCap && out.size() < limit - 1) {
                    continue;
                }
                out.add(Primitive.line(last, next, pathColor, lineWidth));
                last = next;
                skipped = 0.0D;
                if (out.size() >= limit) {
                    return;
                }
            }
        }
    }

    /**
     * 画弦时每段至少多长（格）。
     *
     * <p>轨迹很长时按弧长放大，保证总段数不超过 {@link #MAX_TRAIL_SEGMENTS}；曲率极小时弦再长
     * 也贴着曲线，所以放大只减少顶点数，不改变形状。</p>
     *
     * <p><b>取 2 的幂</b>：弧长每 tick 都在增长，如果直接用 {@code 弧长 / 段数上限}，这个值会每
     * tick 抖一点点，于是**已经画过的那一段弦的端点每 tick 都在挪**——画面上就是“一卡一卡”。
     * 量化到 2 的幂之后，弦只在弧长翻倍时才整体重排一次。</p>
     */
    private static double chordCapFor(double totalArc) {
        double raw = totalArc / MAX_TRAIL_SEGMENTS;
        if (!(raw > MAX_CHORD_LENGTH)) {
            return MAX_CHORD_LENGTH;
        }
        return Math.pow(2.0D, Math.ceil(Math.log(raw) / Math.log(2.0D)));
    }

    /** 一枚投掷物的“还没走到的那段”预览：虚线 + 落点标记 + 落点距离（悬停珍珠与飞行中都标）。 */
    private static void appendTrailPreview(List<Primitive> out, Trail trail) {
        if (!trail.hasPreview()) {
            return;
        }
        String label = null;
        if (previewText) {
            Vec3 end = trail.previewHit != null
                    ? trail.previewHit
                    : trail.previewPoints.get(trail.previewPoints.size() - 1);
            label = previewLabel(trail.previewHit != null,
                    trail.previewOrigin.distanceTo(end));
        }
        appendPreview(out, trail.previewCurve, trail.previewHit, label, trail.id);
    }

    /**
     * 把一条预览折线加成图元：默认隔段绘制（虚线），落点画一个标记，可选一行距离文字。
     *
     * <p>用虚线而不是换颜色，是为了让“预测”和“已经走过”在同一个画面里也能一眼分清；线宽也更细。</p>
     */
    private static void appendPreview(List<Primitive> out, List<Vec3> points, Vec3 hit,
            String label, int group) {
        appendPreview(out, points, hit, label, group, null);
    }

    /**
     * 同上，但整条预览再平移 {@code shift}。
     *
     * <p>手持预览的曲线是「tick 对齐」算好的，渲染时按 {@code partialTick} 整体平移即可；
     * 直接把位移传进来，省掉以前“先建一份再复制一份”的每帧两次分配。</p>
     */
    private static void appendPreview(List<Primitive> out, List<Vec3> points, Vec3 hit,
            String label, int group, Vec3 shift) {
        if (points.size() < 2) {
            return;
        }
        Vec3 delta = shift == null ? Vec3.ZERO : shift;
        // 先把落点标记与文字加上：曲线再长，最关键的“会落到哪”也不会因为图元预算用完而消失。
        if (hit != null) {
            out.add(Primitive.point(raise(hit).add(delta), COLOR_PREVIEW_IMPACT,
                    PREVIEW_IMPACT_SIZE, group));
        }
        if (label != null) {
            Vec3 at = hit != null ? raise(hit).add(delta).add(0.0D, 0.45D, 0.0D)
                    : raise(points.get(points.size() - 1)).add(delta).add(0.0D, 0.45D, 0.0D);
            out.add(Primitive.text(at, label, COLOR_PREVIEW_IMPACT, group));
        }
        // 虚线的“画一段/空一段”按弧长算：采样点被加密之后，固定按点隔段会把虚线切得细到看不见。
        // 整条预览可能长达几千格（远传珍珠），这里按总弧长自适应加长虚线单元，保证既画到落点，
        // 又不会产生上万个图元。
        double totalLength = 0.0D;
        for (int i = 1; i < points.size(); i++) {
            totalLength += points.get(i - 1).distanceTo(points.get(i));
        }
        if (totalLength < 1.0E-9D) {
            return;
        }
        if (!previewDashed) {
            // 实线：按段数等距抽样，始终连到最后一个点，不会只画到一半。
            int stride = Math.max(1,
                    (points.size() - 1 + MAX_PREVIEW_SEGMENTS - 1) / MAX_PREVIEW_SEGMENTS);
            int last = 0;
            for (int i = stride; i < points.size(); i += stride) {
                addPreviewLine(out, points, last, i, delta, group);
                last = i;
                if (out.size() >= MAX_PRIMITIVES) {
                    return;
                }
            }
            if (last != points.size() - 1) {
                addPreviewLine(out, points, last, points.size() - 1, delta, group);
            }
            return;
        }
        // 虚线单元长度：正常情况保持 2 格；整条弧太长时放大到“最多画 MAX_PREVIEW_DASHES 段”。
        double dashLength = Math.max(PREVIEW_DASH_LENGTH,
                totalLength / (MAX_PREVIEW_DASHES * 2.0D));
        double arc = 0.0D;
        int drawn = 0;
        for (int i = 1; i < points.size(); i++) {
            double segmentLength = points.get(i - 1).distanceTo(points.get(i));
            long dashCell = (long) Math.floor((arc + segmentLength * 0.5D) / dashLength);
            arc += segmentLength;
            if ((dashCell & 1L) == 1L) {
                continue;
            }
            addPreviewLine(out, points, i - 1, i, delta, group);
            if (++drawn >= MAX_PREVIEW_DASHES || out.size() >= MAX_PRIMITIVES) {
                return;
            }
        }
    }

    /** 预览线：统一抬高一点，避免和方块顶面 z-fighting。 */
    private static void addPreviewLine(List<Primitive> out, List<Vec3> points, int from, int to,
            Vec3 delta, int group) {
        out.add(Primitive.line(raise(points.get(from)).add(delta),
                raise(points.get(to)).add(delta), COLOR_PREVIEW, PREVIEW_WIDTH, group));
    }

    /** 落点文字：命中方块显示距离，落空（超出模拟上限）单独提示。 */
    private static String previewLabel(boolean blocked, double distance) {
        return blocked
                ? Component.translatable("sog_carpet.ui.projectile.preview",
                        String.format(Locale.ROOT, "%.1f", distance)).getString()
                : Component.translatable("sog_carpet.ui.projectile.miss").getString();
    }

    /** 把满不透明的基础色换成指定 alpha 的 ARGB。 */
    private static int withAlpha(int opaqueColor, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(255.0F * alpha)));
        return (opaqueColor & 0x00FFFFFF) | (a << 24);
    }

    private static void renderGizmos(LevelRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        refreshColors();
        // 渲染时按 partialTick 把“跟着实体走”的部分插值到两 tick 之间：这是消除轨迹震动的关键。
        float partial = partialTick();
        Snapshot current = snapshot;
        // 手持预览每 tick 算一次（输入 tick 对齐），渲染时只按 partialTick 平移，形状不会抖。
        Minecraft client = Minecraft.getInstance();
        HeldPreview held = computeHeldPreview(client);

        if (current.primitives.isEmpty() && current.tails.isEmpty() && held == null) {
            return;
        }
        Map<Integer, Vec3> shifts = new HashMap<>();
        if (!current.interps.isEmpty()) {
            for (Interp interp : current.interps) {
                Vec3 shift = interp.prev().subtract(interp.cur()).scale(1.0D - partial);
                shifts.put(interp.group(), shift);
            }
        }
        // 相机位置/朝向：用来丢掉“贴着近裁剪面”的线段——原版会把这种段夹到裁剪面上，
        // 端点飞出去就是一条横贯屏幕的直线，也就是“曲线中间突然凸起一道”。
        Vec3 camera = cameraPosition(client);
        Vec3 look = cameraLook(client, partial);
        try (Gizmos.TemporaryCollection collection =
                context.levelRenderer().collectPerFrameGizmos()) {
            for (int i = 0; i < current.primitives.size(); i++) {
                Primitive primitive = current.primitives.get(i);
                Vec3 shift = primitive.group == 0 ? null : shifts.get(primitive.group);
                Vec3 from = shift == null ? primitive.from : primitive.from.add(shift);
                Vec3 to = primitive.to == null || shift == null ? primitive.to
                        : primitive.to.add(shift);
                if (primitive.kind == Primitive.KIND_LINE || primitive.kind == Primitive.KIND_ARROW) {
                    if (tooCloseToCamera(from, to, camera, look)) {
                        continue;
                    }
                } else if (tooCloseToCamera(from, from, camera, look)) {
                    continue;
                }
                switch (primitive.kind) {
                    case Primitive.KIND_POINT ->
                            Gizmos.point(from, primitive.color, primitive.width);
                    case Primitive.KIND_ARROW ->
                            Gizmos.arrow(from, to, primitive.color, primitive.width);
                    case Primitive.KIND_TEXT ->
                            Gizmos.billboardText(primitive.text, from,
                                    TextGizmo.Style.forColor(primitive.color)
                                            .withScale(PREVIEW_TEXT_SCALE));
                    default ->
                            Gizmos.line(from, to, primitive.color, primitive.width);
                }
            }
            // 末端动态曲线：最后两段采样 + 真实头端每帧重新平滑，尖端连续生长；
            // 末端标记与方向箭头也在这里画（不占图元预算，不会因为别的轨迹多了就消失）。
            for (int i = 0; i < current.tails.size(); i++) {
                renderTail(current.tails.get(i), partial);
            }
            // 手持预览：形状是本 tick 对齐算好的，这里只按 partialTick 整体平移，不参与实体插值。
            if (held != null) {
                String label = previewText
                        ? previewLabel(held.blocked, held.distance) : null;
                Vec3 shift = heldShift(client, held, partial);
                for (Primitive primitive : heldPrimitives(held, label, shift)) {
                    Vec3 from = primitive.from;
                    Vec3 to = primitive.to;
                    if (primitive.kind == Primitive.KIND_LINE && tooCloseToCamera(from, to, camera, look)) {
                        continue;
                    }
                    if (primitive.kind != Primitive.KIND_LINE && tooCloseToCamera(from, from, camera, look)) {
                        continue;
                    }
                    switch (primitive.kind) {
                        case Primitive.KIND_POINT ->
                                Gizmos.point(from, primitive.color, primitive.width);
                        case Primitive.KIND_TEXT ->
                                Gizmos.billboardText(primitive.text, from,
                                        TextGizmo.Style.forColor(primitive.color)
                                                .withScale(PREVIEW_TEXT_SCALE));
                        default ->
                                Gizmos.line(from, to, primitive.color,
                                        primitive.width);
                    }
                }
            }
        }
    }

    /** 把一份手持预览结果转成待绘制图元（复用 {@link #appendPreview} 的虚线/落点样式）。 */
    private static List<Primitive> heldPrimitives(HeldPreview preview, String label, Vec3 shift) {
        List<Primitive> out = new ArrayList<>();
        appendPreview(out, preview.points, preview.hit, label, 0, shift);
        return out;
    }

    /** 当前帧的插值系数（0..1）；拿不到时按 1（等于不插值）。 */
    private static float partialTick() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.getDeltaTracker() == null) {
                return 1.0F;
            }
            float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            return partial < 0.0F ? 0.0F : Math.min(partial, 1.0F);
        } catch (RuntimeException ignored) {
            return 1.0F;
        }
    }

    /** 一枚投掷物的轨迹（只在客户端 tick 线程改动）。 */
    private static final class Trail {
        /** 实体 id，同时作为「渲染插值分组」的键。 */
        private final int id;
        /** 这条轨迹属于哪一只投掷物（防止实体 id 回收后接错轨迹）。 */
        private final java.util.UUID ownerUuid;
        /** 采样点。 */
        private final List<Vec3> points = new ArrayList<>();
        /**
         * 断开的下标：{@code points[i]} 与 {@code points[i+1]} 之间<b>不连线</b>。
         *
         * <p>投掷物被服务端纠正位置、被传送、或者客户端刚收到一次大位移时，如果照常连起来就会
         * 得到一条横跨半屏的假轨迹——玩家看到的就是“曲线中间突然凸起/横着一道”。这里把它
         * 记成断点：两段各自成线，中间留空。</p>
         */
        private final List<Integer> breakAfter = new ArrayList<>();
        /** 采样点被增删的次数；渲染时用它判断平滑缓存要不要重算。 */
        private int pointsRevision;
        /** 平滑后的采样点，按 {@link #breakAfter} 切成若干段。 */
        private List<List<Vec3>> runs = List.of();
        private int curveRevision = -1;
        /** 投掷物当前位置（原始坐标，不抬高——末端圆点/箭头要精确落在实体上）。 */
        private Vec3 current;
        /** 上一次采样到的原始位置。 */
        private Vec3 lastPos;
        /** 上一 tick 与本 tick 的原始位置（渲染时按 partialTick 插值用）。 */
        private Vec3 prevTickPos;
        private Vec3 tickPos;
        /** 最近一次有意义的移动方向（单位向量）。 */
        private Vec3 heading;
        /** 本 tick 是否还看得见。 */
        private boolean seen;
        /** 连续没有前进或没看见的 tick 数；移动时清零。 */
        private int missingTicks;
        /** 到玩家的平方距离（每 tick 更新，用于“近的先画”）。 */
        private double distanceSqr;
        /** 最近几步的典型长度（指数平滑）；只用来判断“这一步是不是长得出奇”。 */
        private double typicalStep;

        /** 预览折线（还没走到的那段）。 */
        private List<Vec3> previewPoints = List.of();
        /** 预览折线的平滑版本（生成预览时算一次，渲染时直接用）。 */
        private List<Vec3> previewCurve = List.of();
        private Vec3 previewHit;
        /** 预览的起点（算落点距离用）。 */
        private Vec3 previewOrigin;
        /** 这条预览是不是“悬停远传珍珠的落点”。 */
        private boolean previewHovered;
        /** 生成这条预览时用的发射速度（悬停珍珠用它判断“累积速度变没变”）。 */
        private Vec3 previewVelocity = Vec3.ZERO;

        private Trail(int id, java.util.UUID ownerUuid, Vec3 start) {
            this.id = id;
            this.ownerUuid = ownerUuid;
            Vec3 raised = raise(start);
            points.add(raised);
            this.pointsRevision++;
            current = start;
            lastPos = start;
            tickPos = start;
            prevTickPos = start;
        }

        /**
         * 静态折线：最后一段 run 只烘到倒数第二个采样点（{@code S[n-1]}），点集变过才重算。
         *
         * <p>最后 {@code S[n-1] -> S[n]} 这一段留给 {@link Tail} 每帧按 partialTick 重新平滑。
         * 烘到 {@code S[n-1]} 意味着这一段的四个控制点 {@code S[n-3], S[n-2], S[n-1], S[n]}
         * <b>全部已经拿到</b>，所以它烘完就再也不变——每加一个新采样点只是往后延长一条已经定好的曲线，
         * 不会回头改形状。</p>
         */
        private List<List<Vec3>> curveRuns() {
            if (this.curveRevision != this.pointsRevision) {
                this.runs = splitAndSmooth();
                this.curveRevision = this.pointsRevision;
            }
            return this.runs;
        }

        /** 第一段曲线的第一个点（起点圆点用）。 */
        private List<Vec3> curvePoints() {
            List<List<Vec3>> all = curveRuns();
            return all.isEmpty() ? List.of() : all.get(0);
        }

        /** 按断点切成若干段，各自做平滑；最后一段少烘两段留给 {@link Tail}。 */
        private List<List<Vec3>> splitAndSmooth() {
            int size = this.points.size();
            if (size <= 1) {
                return List.of(List.of(this.points.get(0)));
            }
            List<List<Vec3>> out = new ArrayList<>();
            List<Integer> cuts = new ArrayList<>(this.breakAfter.size() + 2);
            cuts.add(-1);
            for (int index : this.breakAfter) {
                if (index > cuts.get(cuts.size() - 1) && index < size - 1) {
                    cuts.add(index);
                }
            }
            cuts.add(size - 1);
            for (int cut = 1; cut < cuts.size(); cut++) {
                int from = cuts.get(cut - 1) + 1;
                int to = cuts.get(cut);
                if (to - from < 1) {
                    continue;
                }
                // 复制一份：points 之后还会增删，不能把 subList 视图留给渲染线程用。
                List<Vec3> run = new ArrayList<>(to - from + 1);
                for (int i = from; i <= to; i++) {
                    run.add(this.points.get(i));
                }
                boolean lastRun = cut == cuts.size() - 1;
                if (lastRun) {
                    // 最后一段：留最后一段给动态 Tail（控制点仍能用后面的真实采样点）。
                    out.add(smoothCurve(run, run.size() - 2));
                } else {
                    out.add(smoothCurve(run, run.size() - 1));
                }
            }
            if (out.isEmpty()) {
                out.add(List.of(this.points.get(0)));
            }
            return out;
        }

        /** 最后一个 run 的起始采样下标（断点之后）。 */
        private int lastRunStart() {
            int start = 0;
            for (int index : this.breakAfter) {
                start = Math.max(start, index + 1);
            }
            return Math.min(start, Math.max(0, this.points.size() - 1));
        }

        /** 最后一个断点的下标；没有断点时为 -1。 */
        private int lastBreakIndex() {
            return this.breakAfter.isEmpty()
                    ? -1 : this.breakAfter.get(this.breakAfter.size() - 1);
        }

        /**
         * 这一步是不是“位置被纠正”（不是正常的飞行）。
         *
         * @param delta           这一步的位移
         * @param previousHeading 上一步的方向；上一步几乎没动（悬停珍珠刚被放飞）时为 {@code null}
         */
        private static boolean isOutlierStep(Vec3 delta, Vec3 previousHeading) {
            if (previousHeading == null) {
                // 之前是悬停/静止：放飞的那一下位移本来就很大，属于正常情况，不能断。
                return false;
            }
            double length = delta.length();
            if (length < BREAK_MIN_STEP) {
                return false;
            }
            double cos = delta.scale(1.0D / length).dot(previousHeading);
            // 转角超过阈值 → 认为是被纠正/传送；正常弹道的每 tick 转角远小于这个值。
            return cos < Math.cos(Math.toRadians(BREAK_TURN_DEGREES));
        }

        /** 轨迹淡出系数（1 = 完全不透明，0 = 已消失）。 */
        private float alpha() {
            return this.missingTicks <= EXPIRE_TICKS - FADE_TICKS
                    ? 1.0F
                    : Math.max(0.0F, 1.0F - (this.missingTicks - (EXPIRE_TICKS - FADE_TICKS))
                            / (float) FADE_TICKS);
        }

        /**
         * 末端动态曲线：从静态曲线末点 {@code S[n-1]} 长到当前帧头端 {@code head} 的那一段。
         *
         * <p>头端用原始坐标（不抬高），所以圆点/箭头严格落在实体上；起点 {@code S[n-1]} 是抬高过的
         * 采样点，0.08 格的落差分摊在这一段里，看不出来，却不会再出现“轨迹末端整体比实体高一点点”
         * 的错觉。</p>
         *
         * <p><b>控制点</b>：{@code p0 = S[n-2]}、{@code p1 = S[n-1]}、{@code p2 = head(partial)}、
         * {@code p3 = 2·head − S[n-1]}。{@code p3} 用线性外推（不是“下一个采样点”那个还不知道的值），
         * 所以切线严格沿运动方向、尖端不会翘起来；等下一个 tick 真正的 {@code S[n+1]} 到手，
         * 这一段就按 {@code (S[n-2], S[n-1], S[n], S[n+1])} 烘进静态曲线——两者的差别只有一步
         * 加速度（约 0.03 格），落在这一小段上是亚像素级的。</p>
         *
         * <p>跨断点时（最新一步被判定为瞬移/纠正）{@code S[n-2]} 属于上一段，不能当控制点，
         * 于是退化成 {@code S[n-1]} 自己——这一段变成直线，但**照样画出来**。以前这种情况会
         * 直接返回空 Tail，整条轨迹只剩一个琥珀十字箭头，也就是“投掷轨迹不显示”。</p>
         */
        private Tail createTail() {
            int size = this.points.size();
            float alpha = alpha();
            int start = lastRunStart();
            Vec3 from;
            Vec3 control;
            if (size >= 3 && size - 1 > start) {
                // 正常情况：静态曲线已经烘到 S[n-1]，末端从它长到头端。
                from = this.points.get(size - 2);
                control = size - 3 >= start ? this.points.get(size - 3) : from;
            } else {
                // 最新一步是断点（新一段只有 1 个点）：这一段没有可信的控制点，
                // 跨过断点去连就会画出一条横跨半屏的假线，所以本 tick 只画末端标记。
                // 注意这里只是“这一小段”不画——静态曲线里之前那些段（≥2 个点）照常烘好、
                // 照常绘制，不会出现“整条轨迹只剩箭头”的情况。
                from = null;
                control = null;
            }
            Vec3 curRaw = this.tickPos == null ? this.lastPos : this.tickPos;
            Vec3 prevRaw = this.prevTickPos == null ? curRaw : this.prevTickPos;
            return new Tail(this.id, control, from, prevRaw, curRaw, this.heading,
                    withAlpha(COLOR_PATH, alpha), Math.max(1.0F, LINE_WIDTH * alpha),
                    withAlpha(COLOR_HEAD, alpha), Math.max(1.0F, ARROW_WIDTH * alpha),
                    Math.max(1.0F, HEAD_POINT_SIZE * alpha));
        }

        /** 这一 tick 有没有真的移动过（没动就不需要插值，省掉每帧的算术）。 */
        private boolean hasMovement() {
            return this.tickPos != null && this.prevTickPos != null
                    && this.prevTickPos.distanceToSqr(this.tickPos) > 1.0E-10D;
        }

        /** 这个实体还是创建轨迹时的那一只吗。 */
        private boolean belongsTo(Entity entity) {
            return entity != null && this.ownerUuid.equals(entity.getUUID());
        }

        /**
         * 更新这一 tick 的采样。
         *
         * <p>{@code heading} 做了轻量平滑：客户端位置是 20Hz 同步的，单个 tick 的位移方向会带
         * 一点抖动，直接拿它当箭头朝向就是“箭头不停抽搐”的原因之一。这里按
         * {@value #HEADING_SMOOTH_KEEP} 的比例保留上一次方向，箭头的旋转于是连续、不抖。</p>
         */
        private void update(Vec3 pos, boolean hovered) {
            Vec3 delta = pos.subtract(lastPos);
            if (delta.lengthSqr() > STILL_EPSILON) {
                Vec3 unit = delta.normalize();
                Vec3 previousHeading = this.heading;
                if (previousHeading == null) {
                    this.heading = unit;
                } else {
                    Vec3 blended = previousHeading.scale(HEADING_SMOOTH_KEEP)
                            .add(unit.scale(1.0D - HEADING_SMOOTH_KEEP));
                    // 反向推走（悬停珍珠被反向叠加）时 blended 可能退化，直接用本次方向。
                    this.heading = blended.lengthSqr() < 1.0E-6D ? unit : blended.normalize();
                }
                Vec3 raised = raise(pos);
                if (raised.distanceToSqr(points.get(points.size() - 1)) >= MIN_STEP_SQR) {
                    // 位置被纠正 / 被传送：这一步的方向或长度明显异常时就断开，
                    // 否则会画出一条横跨半屏的假轨迹（“曲线凸起”）。
                    int previousIndex = points.size() - 1;
                    double step = delta.length();
                    boolean jump = this.typicalStep > 0.5D
                            && step > Math.max(BREAK_JUMP_RATIO * this.typicalStep, BREAK_MIN_JUMP);
                    boolean outlier = jump || isOutlierStep(delta, previousHeading);
                    // 两次断点之间至少留 2 个采样：否则最新那一段 run 只剩 1 个点，
                    // 静态曲线与末端曲线同时为空 —— 整条轨迹就“只剩箭头没有线”。
                    // 真瞬移只会断一次，这个保护不影响观感。
                    if (outlier && previousIndex - lastBreakIndex() >= 2) {
                        breakAfter.add(previousIndex);
                    } else {
                        // 只拿“正常的一步”去更新典型长度，免得被一次纠正带偏；
                        // 擦边（断点太密、这次不断）的异常步照常吸收，避免一直卡在“异常”状态。
                        this.typicalStep = this.typicalStep <= 0.0D ? step
                                : this.typicalStep * TYPICAL_STEP_KEEP
                                        + step * (1.0D - TYPICAL_STEP_KEEP);
                    }
                    points.add(raised);
                    if (points.size() > MAX_POINTS) {
                        points.remove(0);
                        // 下标整体前移一位；被删掉的那些断点一并丢掉。
                        for (int i = breakAfter.size() - 1; i >= 0; i--) {
                            int index = breakAfter.get(i) - 1;
                            if (index < 0) {
                                breakAfter.remove(i);
                            } else {
                                breakAfter.set(i, index);
                            }
                        }
                    }
                    this.pointsRevision++;
                }
                this.missingTicks = 0;
            } else if (hovered) {
                // 悬停的远传珍珠原地不动是正常状态，不能按“落地/卡住”算，否则 5 秒后轨迹就没了。
                this.missingTicks = 0;
            } else {
                this.missingTicks++;
            }
            this.current = pos;
            this.lastPos = pos;
            this.prevTickPos = this.tickPos == null ? pos : this.tickPos;
            this.tickPos = pos;
        }

        private void setPreview(ProjectileBallistics.Result result, boolean hovered,
                Vec3 velocity) {
            if (result == null || result.points().size() < 2) {
                clearPreview();
                return;
            }
            this.previewPoints = result.points();
            this.previewCurve = smoothCurve(this.previewPoints);
            this.previewHit = result.hit();
            this.previewOrigin = result.points().get(0);
            this.previewHovered = hovered;
            this.previewVelocity = velocity == null ? Vec3.ZERO : velocity;
        }

        /** 悬停珍珠：服务端下发的累积速度没变就不用重算落点。 */
        private boolean matchesPreviewVelocity(Vec3 velocity) {
            return velocity != null && velocity.distanceToSqr(this.previewVelocity) < 1.0E-9D;
        }

        private void clearPreview() {
            this.previewPoints = List.of();
            this.previewCurve = List.of();
            this.previewHit = null;
            this.previewOrigin = null;
            this.previewHovered = false;
        }

        private boolean hasPreview() {
            return this.previewPoints.size() >= 2;
        }
    }

    /**
     * 把逐 tick 的折线加密成平滑曲线（向心 Catmull-Rom，{@code alpha = 0.5}）。
     *
     * <p>曲线严格经过每个原始采样点，所以落点、命中点、追踪到的真实位置都不会被改变；插值只发生在
     * 两个采样点之间，用来把“每 tick 一个点”的折角抹平。每段的细分次数按<b>段长自适应</b>：短段
     * 用 {@value #CURVE_SUBDIVISIONS} 打底，长段加密到每小段约 {@value #SMOOTH_TARGET_STEP} 格，
     * 所以高速投掷物（尤其是叠了速度的远传珍珠）也是平滑曲线而不是折线。</p>
     *
     * <p>只有真正的瞬移（换维度 / 被插件传送，见 {@link #looksLikeTeleport}）才保留折线，
     * 平滑会把那条线画到半空的方块里。</p>
     */
    private static List<Vec3> smoothCurve(List<Vec3> points) {
        return smoothCurve(points, points.size() - 1);
    }

    /**
     * 同上，但只输出到 {@code endIndex} 这个采样点为止。
     *
     * <p>切线仍然使用完整的 {@code points}（包括 {@code endIndex} 之后的点），所以截断处
     * 的方向不是“硬折”，而是和后面真正接上的动态 Tail 保持同一根切线。轨迹末端的最后一段
     * 就是这样留给 {@link Tail} 每帧重算的。</p>
     */
    private static List<Vec3> smoothCurve(List<Vec3> points, int endIndex) {
        int count = points.size();
        int last = Math.max(0, Math.min(endIndex, count - 1));
        if (count < 2 || last < 1) {
            List<Vec3> single = new ArrayList<>(1);
            single.add(points.get(0));
            return single;
        }
        if (count < 3) {
            // 不能把 live list 的视图留给渲染线程；复制到 endIndex 为止。
            List<Vec3> copy = new ArrayList<>(last + 1);
            for (int i = 0; i <= last; i++) {
                copy.add(points.get(i));
            }
            return copy;
        }
        // 先把每段长度算出来：判断“瞬移”要看它跟邻居比是不是突兀，而不是看绝对值。
        double[] lengths = new double[count - 1];
        for (int i = 0; i < count - 1; i++) {
            lengths[i] = points.get(i).distanceTo(points.get(i + 1));
        }
        int[] steps = new int[count - 1];
        int total = 1;
        for (int i = 0; i < last; i++) {
            double length = lengths[i];
            int subdivision = looksLikeTeleport(lengths, i) ? 1 : subdivisionFor(length);
            steps[i] = subdivision;
            total += subdivision;
        }
        List<Vec3> out = new ArrayList<>(total + 2);
        out.add(points.get(0));
        for (int i = 0; i < last; i++) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);
            int subdivision = steps[i];
            if (subdivision <= 1) {
                out.add(b);
                continue;
            }
            Vec3 p0 = points.get(Math.max(i - 1, 0));
            Vec3 p3 = points.get(Math.min(i + 2, count - 1));
            for (int step = 1; step <= subdivision; step++) {
                double t = step / (double) subdivision;
                out.add(catmullRom(p0, a, b, p3, t));
            }
        }
        return out;
    }

    /**
     * 这一段是不是“真的瞬移”。
     *
     * <p>判据不是绝对长度，而是<b>跟相邻段比是否突兀</b>：远传珍珠的速度不设上限，整条轨迹
     * 可能每段都是几万格，那是正常的高速飞行，照样应该画成曲线；而换维度 / 被插件传送的那一下
     * 会表现为“一段极长、两边极短”，那种才必须保留折线（平滑会把线画到半空的方块里）。</p>
     */
    private static boolean looksLikeTeleport(double[] lengths, int index) {
        double length = lengths[index];
        if (length <= MAX_SMOOTH_SPAN) {
            return false;
        }
        double neighbour = 1.0D;
        if (index > 0) {
            neighbour = Math.max(neighbour, lengths[index - 1]);
        }
        if (index + 1 < lengths.length) {
            neighbour = Math.max(neighbour, lengths[index + 1]);
        }
        return length > neighbour * TELEPORT_RATIO;
    }

    /**
     * 一个采样段要细分几份。
     *
     * <p><b>只看这一段自己的长度</b>（不看轨迹总长、也不看点数）：于是同一段在“末端动态曲线”
     * 和“烘进静态曲线之后”份数完全一致，而且轨迹变长时已经画过的部分不会被重新采样。
     * 以前点总数超预算时按比例整体降细分，比例随点数每 tick 变化，整条曲线于是每帧重采样一次。</p>
     */
    private static int subdivisionFor(double length) {
        if (!(length > MIN_SMOOTH_LENGTH)) {
            // 段短到这个程度，采样点本身已经比一个像素还密，直连就是曲线。
            return 1;
        }
        int adaptive = (int) Math.round(length / SMOOTH_TARGET_STEP);
        return Math.max(CURVE_SUBDIVISIONS, Math.min(MAX_SEGMENT_STEPS, adaptive));
    }

    /**
     * 向心 Catmull-Rom：{@code t} 取 0..1，返回这一段上的插值点。
     *
     * <p><b>退化控制点用反射补，绝不退回直线</b>：{@code p0 == p1}（轨迹起点 / 刚断线）时把
     * {@code p0} 反射到 {@code p1} 另一侧；{@code p2 == p3}（头端还没长出来）时把 {@code p3}
     * 反射出去。以前这两种情况直接 {@code lerp} 成直线，于是“刚走过的那一段”在
     * {@code partialTick = 0} 时是直线、之后才慢慢变弯——20 次/秒的“折线变曲线”。</p>
     */
    private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        if (p0.distanceToSqr(p1) < 1.0E-12D) {
            p0 = p1.add(p1.subtract(p2));
        }
        if (p2.distanceToSqr(p3) < 1.0E-12D) {
            p3 = p2.add(p2.subtract(p1));
        }
        double t0 = 0.0D;
        double t1 = t0 + Math.sqrt(p0.distanceTo(p1));
        double t2 = t1 + Math.sqrt(p1.distanceTo(p2));
        double t3 = t2 + Math.sqrt(p2.distanceTo(p3));
        if (t1 - t0 < 1.0E-9D || t2 - t1 < 1.0E-9D || t3 - t2 < 1.0E-9D) {
            return p1.lerp(p2, t);
        }
        double time = t1 + (t2 - t1) * t;
        Vec3 a1 = lerpAt(p0, p1, t0, t1, time);
        Vec3 a2 = lerpAt(p1, p2, t1, t2, time);
        Vec3 a3 = lerpAt(p2, p3, t2, t3, time);
        Vec3 b1 = lerpAt(a1, a2, t0, t2, time);
        Vec3 b2 = lerpAt(a2, a3, t1, t3, time);
        return lerpAt(b1, b2, t1, t2, time);
    }

    /** 按参数化的时间点做线性插值（Catmull-Rom 的标准写法）。 */
    private static Vec3 lerpAt(Vec3 from, Vec3 to, double fromTime, double toTime, double time) {
        double span = toTime - fromTime;
        if (span < 1.0E-9D) {
            return from;
        }
        double f = (time - fromTime) / span;
        return from.scale(1.0D - f).add(to.scale(f));
    }

    /** 把位置抬高一丁点，避免和方块顶面 z-fighting。 */
    private static Vec3 raise(Vec3 pos) {
        return pos.add(0.0D, Y_OFFSET, 0.0D);
    }

    /** 本帧相机位置；拿不到时返回 {@code null}（那就完全不做近裁剪剔除，退回老行为）。 */
    private static Vec3 cameraPosition(Minecraft client) {
        try {
            if (client == null || client.gameRenderer == null) {
                return null;
            }
            return client.gameRenderer.getMainCamera().position();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** 本帧相机的视线方向；拿不到时返回 {@code null}。 */
    private static Vec3 cameraLook(Minecraft client, float partial) {
        try {
            if (client == null) {
                return null;
            }
            Entity camera = client.getCameraEntity();
            if (camera == null) {
                camera = client.player;
            }
            return camera == null ? null : camera.getViewVector(partial);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 这一段要不要丢掉。
     *
     * <p>两个判据：① 有一端离近裁剪面太近（沿视线方向），这种段会被原版夹到裁剪面上，
     * 端点飞出去变成横贯屏幕的直线；② 线段贴得离相机太近，投影时会剧烈抖动。</p>
     */
    private static boolean tooCloseToCamera(Vec3 from, Vec3 to, Vec3 camera, Vec3 look) {
        if (camera == null || from == null || to == null) {
            return false;
        }
        if (look != null) {
            double nearFrom = from.subtract(camera).dot(look);
            double nearTo = to.subtract(camera).dot(look);
            if (nearFrom < NEAR_PLANE_SKIP || nearTo < NEAR_PLANE_SKIP) {
                return true;
            }
        }
        // 线段到相机的最近点距离
        Vec3 ab = to.subtract(from);
        double lengthSqr = ab.lengthSqr();
        double t = lengthSqr < 1.0E-9D ? 0.0D
                : Math.max(0.0D, Math.min(1.0D, camera.subtract(from).dot(ab) / lengthSqr));
        return from.add(ab.scale(t)).distanceToSqr(camera) < NEAR_CAMERA_SKIP_SQR;
    }

    /**
     * 轨迹末端的<b>动态曲线</b>：静态曲线末点 {@code S[n-1]} 到当前帧插值出的真实头端。
     *
     * <p><b>为什么只画一段</b>：三段式（{@code S[n-3] → S[n-2] → S[n-1] → head}）里，
     * {@code S[n-2] → S[n-1]} 那一段的第四控制点用移动的 {@code head(partialTick)}，
     * 于是 {@code partialTick = 0} 时 {@code p2 == p3}、三次样条退化成直线，
     * {@code partialTick → 1} 时又变回曲线 —— <b>已经走过的倒数第二段每个 tick 都被重新弯一次</b>，
     * 这就是“末端抽搐 / 先折线后曲线”。现在这一段由静态曲线烘死（四控制点全已知），
     * {@link Tail} 只负责正在生长的那一段，画过的顶点一个都不会再动。</p>
     *
     * <pre>
     *   已烘好的静态曲线 ──► S[n-2] ──► S[n-1] ──┐
     *                                            ├─ 本类负责：S[n-1] → H(partial)
     *                              H = lerp(S[n-1](tick n-1), S[n](tick n), partial)
     * </pre>
     */
    private static final class Tail {
        private final int group;
        /** 控制点 {@code S[n-2]}（跨断点时等于 {@code S[n-1]}，这一段于是退化成直线）。 */
        private final Vec3 control;
        /** 静态曲线末点 {@code S[n-1]}，也就是这一段的起点。 */
        private final Vec3 from;
        /** 上一 tick 与本 tick 的原始位置（不抬高），用来插值真实头端。 */
        private final Vec3 prevRaw;
        private final Vec3 curRaw;
        /** 平滑后的运动方向（速度接近 0 时给箭头兜底）。 */
        private final Vec3 heading;
        private final int pathColor;
        private final float lineWidth;
        private final int headColor;
        private final float arrowWidth;
        private final float headSize;

        private Tail(int group, Vec3 control, Vec3 from, Vec3 prevRaw, Vec3 curRaw, Vec3 heading,
                int pathColor, float lineWidth, int headColor, float arrowWidth, float headSize) {
            this.group = group;
            this.control = control;
            this.from = from;
            this.prevRaw = prevRaw;
            this.curRaw = curRaw;
            this.heading = heading;
            this.pathColor = pathColor;
            this.lineWidth = lineWidth;
            this.headColor = headColor;
            this.arrowWidth = arrowWidth;
            this.headSize = headSize;
        }
    }

    /**
     * 画一条末端动态曲线：曲线本体 + 末端位置十字 + 方向箭头（全部用线段）。
     *
     * <p>为什么不用 {@code Gizmos.point} / {@code Gizmos.arrow}：</p>
     * <ul>
     *   <li>{@code point} 是屏幕空间的小方块，画在投掷物中心会被投掷物模型挡住，于是随视角
     *       “时隐时现”——就是玩家说的箭头闪烁；</li>
     *   <li>{@code arrow} 的箭头是一个四棱锥（两个 quad），顶端长度只有线长的 10%，远处不足
     *       两个像素，朝向稍微一变就在“有/无”之间跳；而且它的箭杆从实体中心起画，埋在模型里
     *       也会被深度测试切掉。</li>
     * </ul>
     *
     * <p>现在改成：一小段十字（标位置）+ 离开实体 0.35 格起的箭杆 + 四根倒刺，全部是线段，
     * 线宽是屏幕像素、不会缩没；尺寸按距离缩放，远近看起来一样大。并且只要 API 支持就
     * {@code setAlwaysOnTop()}，彻底不受投掷物模型和地形遮挡。</p>
     */
    private static void renderTail(Tail tail, float partial) {
        List<Vec3> points = TAIL_SCRATCH;
        points.clear();
        // 尖端：本 tick 的真实位置按 partialTick 插值，再统一抬高到轨迹高度。
        Vec3 head = raise(tail.prevRaw.lerp(tail.curRaw, partial));
        buildTail(tail, head, points);
        Vec3 camera = cameraPosition(Minecraft.getInstance());
        Vec3 look = cameraLook(Minecraft.getInstance(), partial);
        // 从头端往回数 TAIL_ON_TOP_LENGTH 格以内的一段抬到最上层：这一截埋在投掷物本体里，
        // 深度测试会随视角每帧切掉一部分，看起来就是“末端抽搐”。剩下的部分照常做深度测试。
        double onTopBudget = TAIL_ON_TOP_LENGTH;
        for (int i = points.size() - 1; i >= 1; i--) {
            Vec3 from = points.get(i - 1);
            Vec3 to = points.get(i);
            boolean onTop = onTopBudget > 0.0D;
            onTopBudget -= from.distanceTo(to);
            // 轨迹本身照常做深度测试（钻进地里就该看不见），只把贴近近裁剪面的段丢掉。
            if (tooCloseToCamera(from, to, camera, look)) {
                continue;
            }
            if (onTop) {
                Gizmos.line(from, to, tail.pathColor, tail.lineWidth).setAlwaysOnTop();
            } else {
                Gizmos.line(from, to, tail.pathColor, tail.lineWidth);
            }
        }
        // 圆点/箭头落在实体身上，所以用原始坐标（抬高 0.08 会让它们浮在投掷物上方）。
        Vec3 marker = head.subtract(0.0D, Y_OFFSET, 0.0D);
        if (tooCloseToCamera(marker, marker, camera, look)) {
            // 投掷物已经贴到镜头上：什么都不画，免得整屏都是线。
            return;
        }
        // 沿“朝相机”的方向推出去一点：偏移沿着视线，所以画面上看不出位移，但能离开投掷物模型，
        // 不再被自己的模型/旁边的方块挡住——这是“标记时隐时现”的根因。
        if (camera != null) {
            Vec3 toCamera = camera.subtract(marker);
            double distance = toCamera.length();
            if (distance > 1.0E-4D && distance > MARKER_CAMERA_PUSH) {
                marker = marker.add(toCamera.scale(MARKER_CAMERA_PUSH / distance));
            }
        }
        // 标记尺寸按距离缩放，让它在屏幕上的大小基本恒定。
        double size = camera == null ? HEAD_POINT_SIZE * 0.05D
                : Math.max(MARKER_MIN_SIZE,
                        Math.min(MARKER_MAX_SIZE, marker.distanceTo(camera) * MARKER_SCALE));
        drawMarkerCross(marker, size, tail.headColor, tail.arrowWidth);
        Vec3 direction = tailDirection(tail, head);
        if (direction == null) {
            return;
        }
        // 箭头离开实体一小段再开始画：起点埋在投掷物模型里会被深度测试切掉，看上去就是闪烁。
        drawArrowHead(marker.add(direction.scale(HEAD_ARROW_GAP)), direction,
                Math.min(HEAD_ARROW_LENGTH, size * 1.6D), tail.headColor, tail.arrowWidth,
                camera, look);
    }

    /** 画一段线；离相机太近（会被近裁剪面夹出毛刺）就跳过。 */
    private static void drawLine(Vec3 from, Vec3 to, int color, float width, Vec3 camera,
            Vec3 look) {
        if (from == null || to == null || tooCloseToCamera(from, to, camera, look)) {
            return;
        }
        // 标记/箭头是“读数”而不是世界几何，挂在 always-on-top 上最稳：
        // 不会因为投掷物模型或方块边缘擦过而一帧有一帧无。
        Gizmos.line(from, to, color, width).setAlwaysOnTop();
    }

    /** 末端位置标记：一个小的三轴十字（只用线段，稳定、不会被模型完全遮住）。 */
    private static void drawMarkerCross(Vec3 at, double size, int color, float width) {
        if (size < 1.0E-4D) {
            return;
        }
        Gizmos.line(at.add(-size, 0.0D, 0.0D), at.add(size, 0.0D, 0.0D), color, width)
                .setAlwaysOnTop();
        Gizmos.line(at.add(0.0D, -size, 0.0D), at.add(0.0D, size, 0.0D), color, width)
                .setAlwaysOnTop();
        Gizmos.line(at.add(0.0D, 0.0D, -size), at.add(0.0D, 0.0D, size), color, width)
                .setAlwaysOnTop();
    }

    /**
     * 用线段画一个箭头（箭杆 + 四根倒刺），不会像原来的四棱锥那样在远处缩成看不见。
     *
     * @param tip   箭杆起点（已经离开实体）
     * @param dir   方向（单位向量）
     * @param length 箭杆长度
     */
    private static void drawArrowHead(Vec3 tip, Vec3 dir, double length, int color, float width,
            Vec3 camera, Vec3 look) {
        Vec3 end = tip.add(dir.scale(length));
        drawLine(tip, end, color, width, camera, look);
        Vec3 helper = Math.abs(dir.y) > 0.9D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = dir.cross(helper);
        if (side.lengthSqr() < 1.0E-8D) {
            return;
        }
        side = side.normalize();
        Vec3 up = side.cross(dir).normalize();
        double barb = length * 0.32D;
        Vec3 base = end.subtract(dir.scale(barb));
        for (int axis = 0; axis < 2; axis++) {
            Vec3 spread = (axis == 0 ? side : up).scale(barb * 0.62D);
            drawLine(end, base.add(spread), color, width, camera, look);
            drawLine(end, base.subtract(spread), color, width, camera, look);
        }
    }

    /** 末端的平滑曲线：一段向心 Catmull-Rom，起点严格落在 {@code S[n-1]}、终点严格落在头端。 */
    private static void buildTail(Tail tail, Vec3 head, List<Vec3> out) {
        if (tail.from == null) {
            // 只有一个采样点（刚生成）：没有可画的曲线，只保留末端标记。
            return;
        }
        // 这一段的“最终长度”（头端走到本 tick 位置时）。细分份数用它算，于是 partialTick 0→1
        // 期间份数不变，尖端生长时网格不会抖；而且与烘进静态曲线时用的份数完全一致。
        double finalLength = tail.from.distanceTo(raise(tail.curRaw));
        // 第二段：S[n-1] -> head，控制点 (S[n-2], S[n-1], head, head+(head-S[n-1]))。
        // 末尾用线性外推，切线严格沿运动方向，所以尖端不会“翘起来一小段”。
        Vec3 beyond = head.add(head.subtract(tail.from));
        addTailSegment(out, tail.control == null ? tail.from : tail.control, tail.from, head,
                beyond, finalLength);
    }

    /**
     * 追加一段平滑曲线（不含起点、含终点）。
     *
     * @param stepLength 用来决定细分份数的长度（传“这一段的最终长度”，保持份数在 tick 内稳定）
     */
    private static void addTailSegment(List<Vec3> out, Vec3 p0, Vec3 a, Vec3 b, Vec3 p3,
            double stepLength) {
        double length = a.distanceTo(b);
        if (length < 1.0E-6D) {
            // 还没开始移动（partial=0）或原地悬停：这一段长度为 0，不画。
            return;
        }
        if (out.isEmpty() || out.get(out.size() - 1).distanceToSqr(a) > 1.0E-12D) {
            out.add(a);
        }
        int steps = subdivisionFor(stepLength);
        for (int step = 1; step <= steps; step++) {
            out.add(catmullRom(p0, a, b, p3, step / (double) steps));
        }
    }

    /**
     * 尖端的方向。
     *
     * <p>只用“曲线尖端这一小段”的方向会让慢速/悬停的投掷物把 20Hz 位置抖动的方向直接画出来，
     * 箭头就会原地抽搐；只用平滑方向又会在急转弯时慢半拍。所以按<b>这一步走得多远</b>加权：
     * 走得远（> {@link #DIRECTION_FULL_STEP} 格）就一半一半，走得近就完全用平滑方向。</p>
     */
    private static Vec3 tailDirection(Tail tail, Vec3 head) {
        Vec3 damped = tail.heading != null && tail.heading.lengthSqr() > 1.0E-6D
                ? tail.heading.normalize() : null;
        Vec3 segment = tail.from == null ? Vec3.ZERO : head.subtract(tail.from);
        double step = segment.length();
        Vec3 geometric = step > DIRECTION_MIN_STEP ? segment.scale(1.0D / step) : null;
        if (damped == null) {
            if (geometric != null) {
                return geometric;
            }
            Vec3 fallback = tail.control == null || tail.from == null
                    ? null : tail.from.subtract(tail.control);
            return fallback == null || fallback.lengthSqr() < 1.0E-8D
                    ? null : fallback.normalize();
        }
        if (geometric == null || geometric.dot(damped) < 0.0D) {
            return geometric == null ? damped : geometric;
        }
        double weight = Math.min(1.0D,
                (step - DIRECTION_MIN_STEP) / (DIRECTION_FULL_STEP - DIRECTION_MIN_STEP));
        Vec3 blended = damped.scale(1.0D - weight * 0.5D).add(geometric.scale(weight * 0.5D));
        return blended.lengthSqr() < 1.0E-6D ? damped : blended.normalize();
    }

    /** 待绘制图元：线段、箭头、点或世界空间文字。 */
    private static final class Primitive {
        private static final int KIND_LINE = 0;
        private static final int KIND_ARROW = 1;
        private static final int KIND_POINT = 2;
        private static final int KIND_TEXT = 3;

        private final int kind;
        private final Vec3 from;
        private final Vec3 to;
        private final int color;
        private final float width;
        private final String text;
        /**
         * 渲染插值分组：0 = 世界固定（不动），非 0 = 属于哪一条投掷物轨迹。
         *
         * <p>预览虚线是“跟着实体走”的那部分，渲染时按 partialTick 在两个 tick 位置之间
         * 插值，才不会每 tick 跳一下（也就是玩家看到的“轨迹震动”）。已经采样下来的折线是世界固定
         * 的，不需要插值。</p>
         */
        private final int group;

        private Primitive(int kind, Vec3 from, Vec3 to, int color, float width, String text,
                int group) {
            this.kind = kind;
            this.from = from;
            this.to = to;
            this.color = color;
            this.width = width;
            this.text = text;
            this.group = group;
        }

        private static Primitive line(Vec3 from, Vec3 to, int color, float width) {
            return new Primitive(KIND_LINE, from, to, color, width, null, 0);
        }

        private static Primitive line(Vec3 from, Vec3 to, int color, float width, int group) {
            return new Primitive(KIND_LINE, from, to, color, width, null, group);
        }

        private static Primitive arrow(Vec3 from, Vec3 to, int color, float width, int group) {
            return new Primitive(KIND_ARROW, from, to, color, width, null, group);
        }

        private static Primitive point(Vec3 at, int color, float size) {
            return new Primitive(KIND_POINT, at, null, color, size, null, 0);
        }

        private static Primitive point(Vec3 at, int color, float size, int group) {
            return new Primitive(KIND_POINT, at, null, color, size, null, group);
        }

        private static Primitive text(Vec3 at, String text, int color, int group) {
            return new Primitive(KIND_TEXT, at, null, color, 0.0F, text, group);
        }
    }

    /**
     * 一条轨迹在两个 tick 之间的位移。
     *
     * <p>渲染时 {@code shift = (prev - cur) * (1 - partialTick)}：{@code partialTick=0} 时图元
     * 退回上一 tick 的位置，{@code 1} 时停在当前位置，中间线性过渡，于是头部与虚线预览在
     * 60 fps 下是连续移动而不是每秒 20 次跳变。</p>
     */
    /**
     * 一次手持预览的结果（渲染帧内计算）。
     *
     * @param key      输入指纹，位置/朝向/初速/弹道参数没变就复用
     * @param points   从出生点到落点的折线（真实坐标）
     * @param hit      命中方块的位置，落空时为 {@code null}
     * @param blocked  是否被方块挡住
     * @param distance 出生点到落点的直线距离（格）
     * @param eyeTick  模拟时用的 tick 对齐眼睛位置；渲染时按它把整条曲线平移到当前帧
     */
    private record HeldPreview(long key, java.util.List<Vec3> points, Vec3 hit, boolean blocked,
            double distance, Vec3 eyeTick) {
    }

    /**
     * 手持预览的渲染位移：把 tick 对齐的曲线平滑地搬到当前帧的眼睛位置。
     *
     * <p>只平移、不重新模拟，所以曲线形状在一 tick 内完全稳定，不会随帧抖动。</p>
     */
    private static Vec3 heldShift(Minecraft client, HeldPreview preview, float partialTick) {
        if (client == null || client.player == null || preview.eyeTick == null) {
            return Vec3.ZERO;
        }
        Vec3 shift = client.player.getEyePosition(partialTick).subtract(preview.eyeTick);
        return shift.lengthSqr() < 1.0E-10D ? Vec3.ZERO : shift;
    }

    private record Interp(int group, Vec3 prev, Vec3 cur) {
    }

    /** 只读渲染快照，tick 线程整体替换、渲染线程整体读取。 */
    private static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(Collections.<Primitive>emptyList(),
                Collections.<Interp>emptyList(), Collections.<Tail>emptyList());

        private final List<Primitive> primitives;
        private final List<Interp> interps;
        /** 每条轨迹的末端动态曲线（不占图元预算，每帧按 partialTick 重算）。 */
        private final List<Tail> tails;

        private Snapshot(List<Primitive> primitives, List<Interp> interps, List<Tail> tails) {
            this.primitives = primitives;
            this.interps = interps;
            this.tails = tails;
        }
    }
}
