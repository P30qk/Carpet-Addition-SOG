package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.SogSpawnTrace;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import cn.blockforge.generated.sogcarpet.client.gizmo.Gizmos;
import net.minecraft.world.phys.Vec3;

/**
 * 刷怪随机游走路径的客户端绘制（已并入 {@code spawnStatsVisualizer} 规则）。
 *
 * <p>这里的“随机游走”指<b>刷怪器真正的随机游走</b>：原版 {@code NaturalSpawner} 从起始点出发，
 * 每一步把 x/z 各加 {@code random.nextInt(6) - random.nextInt(6)}（y 不变），逐个考察候选位置。
 * 本类分两种来源绘制：</p>
 * <ul>
 *   <li><b>服务端数据</b>：服务端把每次成功刷怪完整游走经过的候选位置发下来（含距离检查失败的
 *       偏移点），客户端连成品红折线、命中点高亮白色；</li>
 *   <li><b>纯客户端模拟</b>：对面没装本模组、或暂时没有数据时，客户端按<b>同一套原版偏移规则</b>
 *       在本地模拟一条随机游走（从玩家脚下出发，逐格走完再重来），于是纯客户端也能渲染。</li>
 * </ul>
 *
 * <p><b>/tick freeze、/tick step</b> 时画面停止推进。颜色与开关在 Ctrl+V 的“配置”里改。</p>
 */
public final class RandomWalkVisualizer {
    private static final String RULE = "spawnStatsVisualizer";
    /** 服务端游走路径的存活 tick 数（约 10 秒）。 */
    private static final int PATH_LIFE_TICKS = 200;
    /** 同屏最多保留多少条游走路径（防止刷怪塔里路径太多拖垮客户端）。 */
    private static final int MAX_PATHS = 48;
    /** 单条游走路径最多多少个节点（原版一次游走最多几十步，够用且防止过度绘制）。 */
    private static final int MAX_PATH_NODES = 64;
    private static final double Y_OFFSET = 0.12D;

    /** 纯客户端模拟：多少 tick 走一步、最多走几步、走完后停多久再重来。 */
    private static final int SIM_STEP_INTERVAL = 20;
    private static final int SIM_MAX_STEPS = 8;
    private static final int SIM_HOLD_TICKS = 60;

    private static final float LINE_WIDTH = 2.5F;
    private static final float SUCCESS_WIDTH = 3.5F;
    private static final float POINT_SIZE = 4.0F;
    private static final float SUCCESS_POINT_SIZE = 8.0F;

    /** 收包线程刚送来、还没并入渲染的游走路径。 */
    private static final List<SpawnPath> PENDING_PATHS = new ArrayList<>();
    /** 正在显示的服务端游走路径（只在客户端 tick 线程改动）。 */
    private static final List<SpawnPath> PATHS = new ArrayList<>();

    /** 纯客户端模拟出来的折线。 */
    private static final List<Vec3> SIM_POINTS = new ArrayList<>();
    private static BlockPos simStart;
    private static int simStepTimer;
    private static int simHoldTimer;

    private RandomWalkVisualizer() {
    }

    /** 客户端入口调用一次。 */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(RandomWalkVisualizer::clientTick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(RandomWalkVisualizer::renderGizmos);
    }

    /** 一条出生事件：把它完整的游走路径收进来（服务端数据）。 */
    public static void noteSpawn(SogSpawnTrace.SpawnEvent event) {
        if (event == null) {
            return;
        }
        SpawnPath path = SpawnPath.of(event);
        if (path != null) {
            synchronized (PENDING_PATHS) {
                PENDING_PATHS.add(path);
                while (PENDING_PATHS.size() > MAX_PATHS) {
                    PENDING_PATHS.remove(0);
                }
            }
        }
    }

    private static void clientTick(Minecraft client) {
        if (!VisualizerState.isActive(RULE) || client.level == null || client.player == null) {
            PATHS.clear();
            synchronized (PENDING_PATHS) {
                PENDING_PATHS.clear();
            }
            SIM_POINTS.clear();
            simStart = null;
            return;
        }
        boolean frozen = VisualizerState.tickFrozen(client);
        synchronized (PENDING_PATHS) {
            if (!PENDING_PATHS.isEmpty()) {
                PATHS.addAll(PENDING_PATHS);
                PENDING_PATHS.clear();
                while (PATHS.size() > MAX_PATHS) {
                    PATHS.remove(0);
                }
            }
        }
        if (frozen) {
            return;
        }
        Iterator<SpawnPath> iterator = PATHS.iterator();
        while (iterator.hasNext()) {
            SpawnPath path = iterator.next();
            if (++path.age > PATH_LIFE_TICKS) {
                iterator.remove();
            }
        }
        updateSimulation(client);
    }

    /**
     * 纯客户端模拟：对面没装本模组时，从玩家脚下出发，按原版偏移规则逐格走出一条随机游走，
     * 走完停一会儿再重来。这样即使没有服务端数据也能看到“真正的随机游走”。
     */
    private static void updateSimulation(Minecraft client) {
        if (VisualizerState.serverHasSogRules()
                || !VisualizerConfig.option(RULE, "simulate", true)) {
            SIM_POINTS.clear();
            simStart = null;
            return;
        }
        if (simStart == null) {
            simStart = client.player.blockPosition();
            SIM_POINTS.clear();
            SIM_POINTS.add(raised(Vec3.atBottomCenterOf(simStart)));
            simStepTimer = 0;
            simHoldTimer = 0;
        }
        if (SIM_POINTS.size() >= SIM_MAX_STEPS + 1) {
            if (++simHoldTimer > SIM_HOLD_TICKS) {
                simStart = client.player.blockPosition();
                SIM_POINTS.clear();
                SIM_POINTS.add(raised(Vec3.atBottomCenterOf(simStart)));
                simStepTimer = 0;
                simHoldTimer = 0;
            }
            return;
        }
        if (++simStepTimer < SIM_STEP_INTERVAL) {
            return;
        }
        simStepTimer = 0;
        BlockPos last = BlockPos.containing(SIM_POINTS.get(SIM_POINTS.size() - 1));
        var random = client.level.getRandom();
        // 与 NaturalSpawner 完全一致的偏移：x/z 各加 nextInt(6) - nextInt(6)。
        int x = last.getX() + random.nextInt(6) - random.nextInt(6);
        int z = last.getZ() + random.nextInt(6) - random.nextInt(6);
        SIM_POINTS.add(raised(Vec3.atBottomCenterOf(new BlockPos(x, last.getY(), z))));
    }

    private static Vec3 raised(Vec3 pos) {
        return pos.add(0.0D, Y_OFFSET, 0.0D);
    }

    private static void renderGizmos(WorldRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        int roam = VisualizerConfig.color(RULE, "roam", 0xCCFF3AD8);
        int roamSuccess = VisualizerConfig.color(RULE, "roamSuccess", 0xFFFFFFFF);
        boolean drawServer = VisualizerConfig.option(RULE, "roamPath", true) && !PATHS.isEmpty();
        boolean drawSim = VisualizerConfig.option(RULE, "simulate", true) && SIM_POINTS.size() >= 2;
        if (!drawServer && !drawSim) {
            return;
        }
        try (Gizmos.TemporaryCollection collection =
                Gizmos.begin(context)) {
            if (drawServer) {
                for (SpawnPath path : PATHS) {
                    drawRoamPath(path, roam, roamSuccess);
                }
            }
            if (drawSim) {
                drawPolyline(SIM_POINTS, roam);
            }
        }
    }

    private static void drawRoamPath(SpawnPath path, int roam, int roamSuccess) {
        List<Vec3> nodes = path.nodes;
        if (nodes.size() < 2) {
            return;
        }
        float alpha = path.age <= 0
                ? 1.0F
                : Math.max(0.0F, 1.0F - path.age / (float) PATH_LIFE_TICKS);
        int lineColor = withAlpha(roam, alpha);
        int successColor = withAlpha(roamSuccess, alpha);
        float lineWidth = Math.max(1.0F, LINE_WIDTH * alpha);
        float successWidth = Math.max(1.0F, SUCCESS_WIDTH * alpha);
        for (int i = 1; i < nodes.size(); i++) {
            boolean hit = i == path.successIndex;
            Gizmos.line(nodes.get(i - 1), nodes.get(i), hit ? successColor : lineColor,
                    hit ? successWidth : lineWidth);
        }
        for (int i = 0; i < nodes.size(); i++) {
            boolean hit = i == path.successIndex;
            Gizmos.point(nodes.get(i), hit ? successColor : lineColor,
                    hit ? SUCCESS_POINT_SIZE : POINT_SIZE);
        }
    }

    private static void drawPolyline(List<Vec3> nodes, int color) {
        for (int i = 1; i < nodes.size(); i++) {
            Gizmos.line(nodes.get(i - 1), nodes.get(i), color, LINE_WIDTH);
        }
        for (Vec3 node : nodes) {
            Gizmos.point(node, color, POINT_SIZE);
        }
    }

    private static int withAlpha(int argb, float alpha) {
        int src = (argb >>> 24) & 0xFF;
        int a = Math.max(0, Math.min(255, Math.round(alpha * src)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** 一条游走路径：折线节点 + 命中节点 + 存活 tick。 */
    private static final class SpawnPath {
        private final List<Vec3> nodes;
        private final int successIndex;
        private int age;

        private SpawnPath(List<Vec3> nodes, int successIndex) {
            this.nodes = nodes;
            this.successIndex = successIndex;
        }

        private static SpawnPath of(SogSpawnTrace.SpawnEvent event) {
            if (event.initial() == null || event.attempts() == null || event.attempts().isEmpty()) {
                return null;
            }
            List<Vec3> nodes = new ArrayList<>();
            addNode(nodes, Vec3.atBottomCenterOf(event.initial()));
            for (BlockPos pos : event.attempts()) {
                if (nodes.size() >= MAX_PATH_NODES) {
                    break;
                }
                addNode(nodes, Vec3.atBottomCenterOf(pos));
            }
            addNode(nodes, Vec3.atBottomCenterOf(event.spawnPos()));
            if (nodes.size() < 2) {
                return null;
            }
            return new SpawnPath(nodes, nodes.size() - 1);
        }

        private static void addNode(List<Vec3> nodes, Vec3 pos) {
            Vec3 raised = pos.add(0.0D, Y_OFFSET, 0.0D);
            if (nodes.isEmpty() || nodes.get(nodes.size() - 1).distanceToSqr(raised) > 1.0E-6D) {
                nodes.add(raised);
            }
        }
    }
}
