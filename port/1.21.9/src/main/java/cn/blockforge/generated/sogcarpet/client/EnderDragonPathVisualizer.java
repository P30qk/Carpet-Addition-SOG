package cn.blockforge.generated.sogcarpet.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * “末影龙寻路可视化”规则的客户端实现。
 *
 * <p><b>显示内容</b></p>
 * <ul>
 *   <li><b>寻路节点</b>：末影龙的寻路算法固定使用 24 个节点（原版
 *       {@code EnderDragon#findClosestNode()} 里现场生成），这里按同一套公式在客户端还原：
 *       12 个半径 60、离地 +5 的<b>外圈</b>节点，8 个半径 40、离地 +15 的<b>中圈</b>节点，
 *       4 个半径 20、离地 +5 的<b>内圈</b>节点；x/z 是绝对世界坐标（末地以原点为中心），
 *       y 取 {@code max(73, 地形高度 + 偏移)}，与原版逐格一致。
 *       外圈 12 个画成品红色小方块，中圈 + 内圈 12 个画成绿色小方块（和参考图一致）；</li>
 *   <li><b>飞行轨迹</b>：每 {@value #TRAIL_SAMPLE_INTERVAL} tick 记一次龙的位置，
 *       把最近约 {@value #TRAIL_LENGTH} 个采样点连成一条亮绿折线——龙在两个节点之间
 *       是直飞，所以折线会在节点处拐弯，正是图里那条绿线；</li>
 *   <li><b>当前位置</b>：龙的身体中心画一个黄色小圆点。</li>
 * </ul>
 *
 * <p>原版的寻路路径本身只在服务端计算、不同步给客户端，所以轨迹由客户端自己记录；
 * 节点位置则是可完全还原的常量布局。只有玩家
 * {@value #MAX_DISTANCE} 格内存在末影龙时才绘制，避免在远处白画一圈节点。</p>
 *
 * <p>线程约定与 {@link SculkVisualizer} 一致：tick 线程整理只读快照，渲染线程整体读取。</p>
 */
public final class EnderDragonPathVisualizer {
    /** 原版寻路节点总数。 */
    private static final int NODE_COUNT = 24;
    /** 外圈（半径 60）节点数，其余 12 个是中圈 + 内圈。 */
    private static final int OUTER_NODE_COUNT = 12;
    /** 中圈节点下标上界（12..19 是半径 40）。 */
    private static final int MIDDLE_NODE_LIMIT = 20;

    /** 外圈节点布局：半径 60，离地 +5，12 等分。 */
    private static final int OUTER_RADIUS = 60;
    private static final float OUTER_STEP = (float) Math.PI / 12.0F;
    private static final int OUTER_Y_OFFSET = 5;
    /** 中圈节点布局：半径 40，离地 +15（柱顶高度），8 等分。 */
    private static final int MIDDLE_RADIUS = 40;
    private static final float MIDDLE_STEP = (float) Math.PI / 8.0F;
    private static final int MIDDLE_Y_OFFSET = 15;
    /** 内圈节点布局：半径 20，离地 +5，4 等分（末地传送门附近）。 */
    private static final int INNER_RADIUS = 20;
    private static final float INNER_STEP = (float) Math.PI / 4.0F;
    private static final int INNER_Y_OFFSET = 5;
    /** 节点 y 的下限，与原版一致（末地祭坛高度附近）。 */
    private static final int MIN_NODE_Y = 73;

    /** 轨迹采样间隔（tick）。 */
    private static final int TRAIL_SAMPLE_INTERVAL = 2;
    /** 每条轨迹保留的采样点数（约 15 秒）。 */
    private static final int TRAIL_LENGTH = 150;
    /** 节点位置重新计算的间隔（tick）：地形被改写后节点会跟着变。 */
    private static final int NODE_REFRESH_TICKS = 20;
    /** 只有该距离内存活末影龙才绘制。 */
    private static final double MAX_DISTANCE = 200.0D;
    private static final double MAX_DISTANCE_SQR = MAX_DISTANCE * MAX_DISTANCE;

    /**
     * 轨迹折线。
     *
     * <p>lucidity 的飞行路径线用的就是 {@code ender_dragon_waypoint_color}
     * （255, 0, 118，alpha 240）——路径在反向飞行时换成反色，客户端这边只记录龙自己的轨迹，
     * 没有方向信息，统一用正向色。alpha 压在 255 以下，落进半透明组，保证叠在节点方块之后画。</p>
     */
    private static final int COLOR_TRAIL = 0xF0FF0076;
    /** 龙当前位置的圆点（黄）。 */
    private static final int COLOR_TIP = 0xFFFFE14D;
    /** 中圈 + 内圈节点方块：lucidity 的 ender_dragon_waypoint_color（255, 0, 118）。 */
    private static final int COLOR_NODE_INNER = 0xFFFF0076;
    /** 外圈节点方块：lucidity 对该色取反（0, 255, 137）。 */
    private static final int COLOR_NODE_OUTER = 0xFF00FF89;
    /** 节点方块的描边 / 填充 alpha（lucidity 的原色 alpha 就是 240）。 */
    private static final int MARKER_STROKE_ALPHA = 0xF0;
    private static final int MARKER_FILL_ALPHA = 0x40;
    /** 节点盒子往外让一点，避免六个面和世界方块面重合闪烁。 */
    private static final double MARKER_INFLATE = 0.004D;
    /** 节点方块描边与轨迹线的宽度。 */
    private static final float MARKER_STROKE_WIDTH = 2.5F;
    private static final float TRAIL_WIDTH = 4.0F;

    /** 本规则在总控里的名字。 */
    private static final String RULE = "enderDragonPathVisualizer";

    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    /** 每条龙最近一段飞行轨迹：实体 id -&gt; 环形采样。 */
    private static final Map<Integer, Trail> TRAILS = new HashMap<>();
    /** 上一次算好的节点标记（地形不变时复用）。 */
    private static List<Marker> nodeMarkers = Collections.emptyList();
    private static int nodeRefreshCounter;
    private static int tickCounter;

    private EnderDragonPathVisualizer() {
    }

    /** 客户端入口调用一次：注册按键、tick 采样与渲染回调。 */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(EnderDragonPathVisualizer::clientTick);
        SogRenderEvents.register(EnderDragonPathVisualizer::renderGizmos);
    }

    private static void clientTick(Minecraft client) {
        ClientLevel level = client.level;
        // lucidity 只在末地画寻路节点；别的维度里那 24 个绝对坐标节点没有意义。
        if (!VisualizerState.isActive(RULE) || level == null || !level.dimension().equals(Level.END)) {
            reset();
            return;
        }
        tickCounter++;
        List<EnderDragon> dragons = findDragons(level, client.player == null ? null : client.player.position());
        if (dragons.isEmpty()) {
            reset();
            return;
        }
        if (nodeRefreshCounter-- <= 0) {
            nodeRefreshCounter = NODE_REFRESH_TICKS;
            nodeMarkers = computeNodeMarkers(level);
        }
        boolean sampleNow = tickCounter % TRAIL_SAMPLE_INTERVAL == 0;
        Set<Integer> alive = new HashSet<>();
        List<Vec3> tips = new ArrayList<>(dragons.size());
        for (int i = 0; i < dragons.size(); i++) {
            EnderDragon dragon = dragons.get(i);
            int id = dragon.getId();
            alive.add(id);
            Vec3 center = centerOf(dragon);
            tips.add(center);
            Trail trail = TRAILS.get(id);
            if (trail == null) {
                trail = new Trail();
                TRAILS.put(id, trail);
            }
            if (sampleNow) {
                trail.add(center);
            }
        }
        Iterator<Integer> iterator = TRAILS.keySet().iterator();
        while (iterator.hasNext()) {
            if (!alive.contains(iterator.next())) {
                iterator.remove();
            }
        }
        List<List<Vec3>> trails = new ArrayList<>(TRAILS.size());
        for (Trail trail : TRAILS.values()) {
            if (trail.size() >= 2) {
                trails.add(trail.copy());
            }
        }
        snapshot = new Snapshot(nodeMarkers, trails, tips);
    }

    /** 龙的身体中心（包围盒中心），轨迹与黄点都用它。 */
    private static Vec3 centerOf(EnderDragon dragon) {
        return new Vec3(dragon.getX(), dragon.getY() + dragon.getBbHeight() * 0.5D, dragon.getZ());
    }

    /** 收集 {@value #MAX_DISTANCE} 格内的末影龙。 */
    private static List<EnderDragon> findDragons(ClientLevel level, Vec3 playerPos) {
        List<EnderDragon> dragons = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof EnderDragon)) {
                continue;
            }
            EnderDragon dragon = (EnderDragon) entity;
            if (playerPos != null && playerPos.distanceToSqr(centerOf(dragon)) > MAX_DISTANCE_SQR) {
                continue;
            }
            dragons.add(dragon);
        }
        return dragons;
    }

    private static void reset() {
        TRAILS.clear();
        nodeMarkers = Collections.emptyList();
        nodeRefreshCounter = 0;
        tickCounter = 0;
        snapshot = Snapshot.EMPTY;
    }

    /**
     * 按原版 {@code EnderDragon#findClosestNode()} 的公式算出 24 个节点，
     * 外圈 12 个记品红、其余 12 个记绿。
     */
    private static List<Marker> computeNodeMarkers(Level level) {
        List<Marker> markers = new ArrayList<>(NODE_COUNT);
        for (int i = 0; i < NODE_COUNT; i++) {
            int radius;
            int index;
            int yOffset;
            float step;
            if (i < OUTER_NODE_COUNT) {
                radius = OUTER_RADIUS;
                index = i;
                yOffset = OUTER_Y_OFFSET;
                step = OUTER_STEP;
            } else if (i < MIDDLE_NODE_LIMIT) {
                radius = MIDDLE_RADIUS;
                index = i - OUTER_NODE_COUNT;
                yOffset = MIDDLE_Y_OFFSET;
                step = MIDDLE_STEP;
            } else {
                radius = INNER_RADIUS;
                index = i - MIDDLE_NODE_LIMIT;
                yOffset = INNER_Y_OFFSET;
                step = INNER_STEP;
            }
            float angle = 2.0F * (-(float) Math.PI + step * index);
            int x = Mth.floor(radius * Mth.cos(angle));
            int z = Mth.floor(radius * Mth.sin(angle));
            int y = Math.max(MIN_NODE_Y, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, 0, z)).getY() + yOffset);
            int color = i < OUTER_NODE_COUNT ? COLOR_NODE_OUTER : COLOR_NODE_INNER;
            // lucidity 把节点画成“以该方块为起点、边长 1 的整块盒子”，这里沿用同一位置与体量。
            markers.add(new Marker(new Vec3(x, y, z), color));
        }
        return markers;
    }


    private static void renderGizmos(SogWorldRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        Snapshot current = snapshot;
        if (current.tips.isEmpty()) {
            return;
        }
        try (Gizmos.TemporaryCollection collection = Gizmos.begin(context)) {
            for (int t = 0; t < current.trails.size(); t++) {
                List<Vec3> trail = current.trails.get(t);
                for (int i = 0; i + 1 < trail.size(); i++) {
                    Gizmos.line(trail.get(i), trail.get(i + 1), COLOR_TRAIL, TRAIL_WIDTH);
                }
            }
            for (int i = 0; i < current.markers.size(); i++) {
                drawMarker(current.markers.get(i));
            }
            for (int i = 0; i < current.tips.size(); i++) {
                // Gizmos.point 的尺寸是屏幕像素（debug_point.vsh 里直接写 gl_PointSize），
                // 这里填 0.4 会变成亚像素、端点完全看不见；6 像素才是一个正常的光点。
                Gizmos.point(current.tips.get(i), COLOR_TIP, 6.0F);
            }
        }
    }

    /** 节点画成整块大小的半透明盒子（1×1×1），与 lucidity 的寻路节点指示器一致。 */
    private static void drawMarker(Marker marker) {
        Vec3 c = marker.center;
        AABB box = new AABB(c.x, c.y, c.z, c.x + 1.0D, c.y + 1.0D, c.z + 1.0D);
        Gizmos.cuboid(box.inflate(MARKER_INFLATE), GizmoStyle.strokeAndFill(
                withAlpha(marker.color, MARKER_STROKE_ALPHA), MARKER_STROKE_WIDTH,
                withAlpha(marker.color, MARKER_FILL_ALPHA)));
    }

    /** 把满不透明的基础色换成指定 alpha 的 ARGB。 */
    private static int withAlpha(int opaqueColor, int alpha) {
        return (opaqueColor & 0x00FFFFFF) | (alpha << 24);
    }

    /** 一条固定长度的轨迹：满了就丢最旧的采样。 */
    private static final class Trail {
        private final ArrayDeque<Vec3> samples = new ArrayDeque<>(TRAIL_LENGTH);

        private void add(Vec3 point) {
            if (this.samples.size() >= TRAIL_LENGTH) {
                this.samples.removeFirst();
            }
            this.samples.addLast(point);
        }

        private int size() {
            return this.samples.size();
        }

        private List<Vec3> copy() {
            return new ArrayList<>(this.samples);
        }
    }

    /** 一个寻路节点标记。 */
    private static final class Marker {
        private final Vec3 center;
        private final int color;

        private Marker(Vec3 center, int color) {
            this.center = center;
            this.color = color;
        }
    }

    /** 只读渲染快照，tick 线程整体替换、渲染线程整体读取。 */
    private static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(Collections.<Marker>emptyList(),
                Collections.<List<Vec3>>emptyList(), Collections.<Vec3>emptyList());

        private final List<Marker> markers;
        private final List<List<Vec3>> trails;
        private final List<Vec3> tips;

        private Snapshot(List<Marker> markers, List<List<Vec3>> trails, List<Vec3> tips) {
            this.markers = markers;
            this.trails = trails;
            this.tips = tips;
        }
    }
}
