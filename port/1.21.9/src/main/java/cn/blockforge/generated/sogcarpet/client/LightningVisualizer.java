package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.SogLightning;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import cn.blockforge.generated.sogcarpet.client.render.WorldRenderContext;
import cn.blockforge.generated.sogcarpet.client.render.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import cn.blockforge.generated.sogcarpet.client.gizmo.GizmoStyle;
import cn.blockforge.generated.sogcarpet.client.gizmo.Gizmos;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 「雷击范围可视化」的客户端实现。
 *
 * <p>两件事，全部在客户端完成（服务端不需要下发任何数据）：</p>
 * <ol>
 *   <li><b>落雷的雷击影响范围</b>：客户端能收到同步下来的 {@link LightningBolt} 实体，所以每 tick
 *       扫一遍附近的闪电，第一次看到某个闪电就把它的落雷点记下来，并渲染
 *       {@link SogLightning#impactBox}（水平 ±3、竖直 -3～+6 的半透明方块区域），
 *       {@value #SHOW_TICKS} tick（5 秒）后淡出消失；最后一小段时间整块区域一起变淡；</li>
 *   <li><b>避雷针</b>：扫描玩家附近的已加载区块（先用 {@code maybeHas} 跳过不含避雷针的 section，
 *       代价很低），对每个避雷针画三样东西：
 *       <ul>
 *         <li>避雷针本体用方框框住；</li>
 *         <li><b>引雷范围</b>：原版 {@code ServerLevel#findLightningRod} 在 128 格内找最近的避雷针
 *             把落雷引过去，所以画一个半径 {@value #ROD_ATTRACT_RADIUS} 格的水平圆环；</li>
 *         <li><b>雷击影响范围</b>：落在这个避雷针上的闪电会影响的区域，与落雷那段用同一份
 *             {@link SogLightning#impactBox}。</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>线程约定与其它可视化一致：tick 线程整体替换只读快照，渲染线程整体读取；闪电只在
 * {@value #SCAN_INTERVAL_TICKS} tick 节流下重建避雷针快照，闪电检测则每 tick 做一次（很便宜）。
 * Ctrl+O 是总开关，Ctrl+V 里改颜色。</p>
 */
public final class LightningVisualizer {
    /** 本规则在总控里的名字。 */
    private static final String RULE = "lightningRangeVisualizer";

    /** 落雷范围显示多久（tick）：5 秒。 */
    public static final int SHOW_TICKS = 100;
    /** 最后多少 tick 用来淡出。 */
    private static final int FADE_TICKS = 20;

    /** 避雷针的引雷半径（原版 {@code findLightningRod} 的 POI 搜索半径）。 */
    public static final float ROD_ATTRACT_RADIUS = 128.0F;

    /** 落雷记录范围（格）：超出这个距离的落雷不记录，避免远处雷暴刷屏。 */
    private static final double STRIKE_RANGE = 128.0D;
    /** 避雷针扫描半径（区块）。 */
    private static final int SCAN_RADIUS_CHUNKS = 4;
    /** 避雷针快照重建间隔（tick）。 */
    private static final int SCAN_INTERVAL_TICKS = 20;
    /** 同屏最多画多少个避雷针。 */
    private static final int MAX_RODS = 24;
    /** 同屏最多画多少个落雷范围。 */
    private static final int MAX_STRIKES = 24;

    /** 默认配色。 */
    private static final int DEFAULT_STRIKE_FILL = 0x40FFF27A;
    private static final int DEFAULT_STRIKE_STROKE = 0xE6FFF27A;
    private static final int DEFAULT_ROD_BOX = 0xFF8CD2FF;
    private static final int DEFAULT_ROD_ATTRACT = 0x99A0E8FF;
    private static final int DEFAULT_ROD_IMPACT_FILL = 0x2E7AD7FF;
    private static final int DEFAULT_ROD_IMPACT_STROKE = 0xB37AD7FF;

    private static final float STRIKE_STROKE_WIDTH = 1.5F;
    private static final float ROD_STROKE_WIDTH = 2.5F;
    /** 方块框往外膨胀一点，避免和方块面重合导致闪烁。 */
    private static final float BOX_INFLATE = 0.004F;

    /** 已记录的落雷：落雷方块 -&gt; 到期游戏刻。 */
    private static final Map<BlockPos, Integer> STRIKES = new HashMap<>();
    /**
     * 刚收到的落雷（来自实体加载事件，可能在网络线程）。
     *
     * <p>用实体加载事件而不是每 tick 扫实体：闪电只活几 tick，扫实体要么范围开得很大很费，
     * 要么会漏。事件在实体加入世界时给一次，正好是“落雷”那一刻。</p>
     */
    private static final java.util.concurrent.ConcurrentLinkedQueue<BlockPos> PENDING =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    private static volatile RodSnapshot rods = RodSnapshot.EMPTY;
    /**
     * 渲染线程只读这份落雷快照。
     *
     * <p>{@link #STRIKES} 是 tick 线程可变状态，渲染线程直接遍历会撞上
     * {@code ConcurrentModificationException}；所以每 tick 末尾把「方块 + 剩余 tick」
     * 复制成一份不可变列表整体替换，渲染只碰它（与其它可视化同一套约定）。</p>
     */
    private static volatile StrikeSnapshot strikes = StrikeSnapshot.EMPTY;
    private static int scanCountdown;

    private LightningVisualizer() {
    }

    /** 客户端入口调用一次：注册实体加载检测、tick 与渲染回调。 */
    public static void init() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_LOAD.register(
                (entity, level) -> {
                    if (entity instanceof LightningBolt bolt && active()) {
                        PENDING.add(SogLightning.strikePosition(
                                bolt.getX(), bolt.getY(), bolt.getZ()));
                    }
                });
        ClientTickEvents.END_CLIENT_TICK.register(LightningVisualizer::clientTick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(LightningVisualizer::renderGizmos);
    }

    // ------------------------------------------------------------------ tick

    /**
     * 这条可视化当前是否要运行。
     *
     * <p>除了一般的「总开关 + 规则开 + 渲染开」之外，这里额外允许<b>纯客户端</b>使用：
     * 对着没装本模组的服务器（收不到规则同步）时，落雷与避雷针的数据本来就在客户端，
     * 只要总开关与本地渲染开关都开着就画。装模组的服务器上仍然严格跟着 carpet 规则走，
     * 规则关掉就绝不渲染。</p>
     */
    private static boolean active() {
        if (VisualizerState.isActive(RULE)) {
            return true;
        }
        return !VisualizerState.serverHasSogRules()
                && VisualizerState.masterEnabled()
                && VisualizerState.isRenderEnabled(RULE);
    }

    private static void clientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (!active() || level == null || client.player == null) {
            STRIKES.clear();
            PENDING.clear();
            rods = RodSnapshot.EMPTY;
            strikes = StrikeSnapshot.EMPTY;
            return;
        }
        int now = (int) level.getGameTime();
        // 落雷：把实体加载事件收到的落雷并进显示表；超时或玩家走远后清掉。
        BlockPos pending;
        while ((pending = PENDING.poll()) != null) {
            if (client.player.blockPosition().distSqr(pending) <= STRIKE_RANGE * STRIKE_RANGE) {
                STRIKES.put(pending, now + SHOW_TICKS);
            }
        }
        Iterator<Map.Entry<BlockPos, Integer>> iterator = STRIKES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = iterator.next();
            boolean far = client.player.blockPosition().distSqr(entry.getKey())
                    > STRIKE_RANGE * STRIKE_RANGE;
            if (entry.getValue() <= now || far) {
                iterator.remove();
            }
        }
        if (STRIKES.size() > MAX_STRIKES) {
            // 太多了只留最新的那些（保留到期时间最大的一批）。
            List<Map.Entry<BlockPos, Integer>> sorted = new ArrayList<>(STRIKES.entrySet());
            sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            STRIKES.clear();
            for (int i = 0; i < MAX_STRIKES; i++) {
                STRIKES.put(sorted.get(i).getKey(), sorted.get(i).getValue());
            }
        }
        // 发布落雷的只读快照（渲染线程只读这份）。
        List<Strike> visible = new ArrayList<>(STRIKES.size());
        for (Map.Entry<BlockPos, Integer> entry : STRIKES.entrySet()) {
            visible.add(new Strike(entry.getKey(), entry.getValue() - now));
        }
        strikes = new StrikeSnapshot(visible);
        // 避雷针：节流重建快照（离玩家最近的优先）。
        if (VisualizerState.tickFrozen(client) || --scanCountdown > 0) {
            return;
        }
        scanCountdown = SCAN_INTERVAL_TICKS;
        rods = scanRods(level, client.player.blockPosition());
    }

    /** 扫描附近已加载区块里的避雷针；用 {@code maybeHas} 先跳过不含避雷针的 section。 */
    private static RodSnapshot scanRods(ClientLevel level, BlockPos center) {
        List<BlockPos> found = new ArrayList<>();
        int chunkX = center.getX() >> 4;
        int chunkZ = center.getZ() >> 4;
        for (int dx = -SCAN_RADIUS_CHUNKS; dx <= SCAN_RADIUS_CHUNKS; dx++) {
            for (int dz = -SCAN_RADIUS_CHUNKS; dz <= SCAN_RADIUS_CHUNKS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX + dx, chunkZ + dz, false);
                if (chunk == null || chunk.isEmpty()) {
                    continue;
                }
                collectRods(chunk, found);
                if (found.size() >= MAX_RODS * 4) {
                    // 够多了：按距离截断之前先粗略停下，避免极端场景扫全图。
                    return buildSnapshot(found, center);
                }
            }
        }
        return buildSnapshot(found, center);
    }

    private static void collectRods(LevelChunk chunk, List<BlockPos> out) {
        LevelChunkSection[] sections = chunk.getSections();
        int minY = chunk.getMinY();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            if (!section.maybeHas(state -> state.getBlock() instanceof LightningRodBlock)) {
                continue;
            }
            int baseY = minY + (index << 4);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        // 26.2 的避雷针是铜的氧化家族（8 种方块），所以按方块类判断而不是比对单个方块。
                        if (section.getBlockState(x, y, z).getBlock() instanceof LightningRodBlock) {
                            out.add(new BlockPos((chunk.getPos().x << 4) + x, baseY + y,
                                    (chunk.getPos().z << 4) + z));
                        }
                    }
                }
            }
        }
    }

    private static RodSnapshot buildSnapshot(List<BlockPos> found, BlockPos center) {
        List<BlockPos> sorted = new ArrayList<>(found);
        sorted.sort((a, b) -> Double.compare(center.distSqr(a), center.distSqr(b)));
        if (sorted.size() > MAX_RODS) {
            sorted = new ArrayList<>(sorted.subList(0, MAX_RODS));
        }
        return new RodSnapshot(sorted);
    }

    // ------------------------------------------------------------------ 渲染

    private static void renderGizmos(WorldRenderContext context) {
        if (!active()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) {
            return;
        }
        int strikeFill = VisualizerConfig.color(RULE, "strikeFill", DEFAULT_STRIKE_FILL);
        int strikeStroke = VisualizerConfig.color(RULE, "strikeStroke", DEFAULT_STRIKE_STROKE);
        int rodBoxColor = VisualizerConfig.color(RULE, "rodBox", DEFAULT_ROD_BOX);
        int attractColor = VisualizerConfig.color(RULE, "rodAttract", DEFAULT_ROD_ATTRACT);
        int rodImpactFill = VisualizerConfig.color(RULE, "rodImpactFill", DEFAULT_ROD_IMPACT_FILL);
        int rodImpactStroke = VisualizerConfig.color(RULE,
                "rodImpactStroke", DEFAULT_ROD_IMPACT_STROKE);
        boolean showStrikes = VisualizerConfig.option(RULE, "showStrikes", true);
        boolean showRods = VisualizerConfig.option(RULE, "showRods", true);
        boolean showAttract = VisualizerConfig.option(RULE, "showAttractRange", true);

        RodSnapshot current = rods;
        StrikeSnapshot currentStrikes = strikes;
        if (currentStrikes.strikes.isEmpty() && current.rods.isEmpty()) {
            return;
        }
        try (Gizmos.TemporaryCollection collection =
                     Gizmos.begin(context)) {
            if (showStrikes) {
                for (Strike strike : currentStrikes.strikes) {
                    float alpha = fade(strike.remainingTicks);
                    AABB area = SogLightning.impactBox(strike.pos);
                    Gizmos.addGizmo(new CuboidGizmo(area, GizmoStyle.strokeAndFill(
                            scaleAlpha(strikeStroke, alpha), STRIKE_STROKE_WIDTH,
                            scaleAlpha(strikeFill, alpha))));
                }
            }
            if (showRods) {
                GizmoStyle rodStyle = GizmoStyle.stroke(rodBoxColor, ROD_STROKE_WIDTH);
                GizmoStyle impactStyle = GizmoStyle.strokeAndFill(
                        rodImpactStroke, STRIKE_STROKE_WIDTH, rodImpactFill);
                GizmoStyle attractStyle = GizmoStyle.stroke(attractColor, 1.5F);
                for (BlockPos rod : current.rods) {
                    // 本体：方框框住（往外套一点点，避免与方块面重合）。
                    Gizmos.cuboid(rod, BOX_INFLATE, rodStyle);
                    // 雷击影响范围：落在这个避雷针上的闪电会影响的区域。
                    Gizmos.addGizmo(new CuboidGizmo(SogLightning.impactBox(rod), impactStyle));
                    // 引雷范围：原版在 128 格内找最近的避雷针，用水平圆环表示。
                    if (showAttract) {
                        Gizmos.circle(Vec3.atCenterOf(rod).add(0.0D, 0.5D, 0.0D),
                                ROD_ATTRACT_RADIUS, attractStyle);
                    }
                }
            }
        }
    }

    /** 剩余 tick → 透明度系数：最后 {@value #FADE_TICKS} tick 线性淡出。 */
    private static float fade(int remainingTicks) {
        if (remainingTicks <= 0) {
            return 0.0F;
        }
        if (remainingTicks >= FADE_TICKS) {
            return 1.0F;
        }
        return remainingTicks / (float) FADE_TICKS;
    }

    /** 按系数缩放颜色的 alpha 通道。 */
    private static int scaleAlpha(int argb, float factor) {
        int alpha = (int) (((argb >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, factor)));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** 一条待渲染的落雷：落雷方块 + 剩余显示 tick。 */
    private record Strike(BlockPos pos, int remainingTicks) {
    }

    /** 只读的落雷快照。 */
    private static final class StrikeSnapshot {
        private static final StrikeSnapshot EMPTY =
                new StrikeSnapshot(Collections.<Strike>emptyList());

        private final List<Strike> strikes;

        private StrikeSnapshot(List<Strike> strikes) {
            this.strikes = strikes;
        }
    }

    /** 只读渲染快照（避雷针位置表）。 */
    private static final class RodSnapshot {
        private static final RodSnapshot EMPTY = new RodSnapshot(Collections.<BlockPos>emptyList());

        private final List<BlockPos> rods;

        private RodSnapshot(List<BlockPos> rods) {
            this.rods = rods;
        }
    }
}
