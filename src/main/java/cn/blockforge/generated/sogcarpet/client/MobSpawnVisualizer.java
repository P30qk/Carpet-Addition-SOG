package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.SogSpawnTrace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * “刷怪游走可视化”的客户端绘制。
 *
 * <p>数据来自 {@link SogSpawnTrace}：服务端 {@code NaturalSpawner} 每刷出一只生物就会归档一条
 * 轨迹，记录它从区块角落“游走”到落点的全过程。这里把轨迹还原成一条彩色折线，和直接移植过来的
 * 原实现一样：</p>
 * <ul>
 *   <li><b>灰色</b>：区块最小角出发；</li>
 *   <li><b>红色</b>：竖直抬到起始高度；<b>蓝色</b>：沿 x 横移；<b>绿色</b>：落到起始点；</li>
 *   <li><b>灰色</b>：之后的每个候选点；真正刷出生物的那个候选点用<b>白色</b>；</li>
 *   <li><b>深灰</b>：最后一笔收在生物实际落点上。</li>
 * </ul>
 *
 * <p>每条轨迹存活约 {@value #LIFETIME_TICKS} tick，期间线宽从 {@value #BASE_WIDTH} 均匀收到 0、
 * alpha 同步淡出，正好还原原实现“线越缩越细直到消失”的效果。</p>
 *
 * <p>原实现在客户端直接截服务端的刷怪循环；这里把采集放进服务端、客户端只消费快照，
 * 因此单机与“对局域网开放”的房主能看到，纯多人服务器（客户端连别人的服）看不到——
 * 和该功能本身依赖服务端数据的性质一致。</p>
 */
public final class MobSpawnVisualizer {
    /** 轨迹存活 tick 数：线宽从基准值一路收到 0。 */
    private static final int LIFETIME_TICKS = 24;
    /** 起始线宽（Gizmos 世界单位），每 tick 收 0.25，{@value #LIFETIME_TICKS} tick 刚好归零。 */
    private static final float BASE_WIDTH = 6.0F;
    /** 一帧最多画多少段线，防止刷怪塔里瞬间刷出上千条轨迹拖垮渲染。 */
    private static final int MAX_SEGMENTS = 4000;

    /** 区块最小角 → 起始点：底部连接段（灰）。 */
    private static final int COLOR_GRAY = 0xFFB0B0B0;
    /** 竖直抬升段（红）。 */
    private static final int COLOR_RED = 0xFFFF4136;
    /** 沿 x 横移段（蓝）。 */
    private static final int COLOR_BLUE = 0xFF3D7BFF;
    /** 落到起始点（绿）。 */
    private static final int COLOR_GREEN = 0xFF3CE04A;
    /** 真正刷出生物的那个候选点（白）。 */
    private static final int COLOR_SPAWNED = 0xFFFFFFFF;
    /** 收尾连到生物落点（深灰）。 */
    private static final int COLOR_DARK = 0xFF5A5A5A;

    /** 本规则在总控里的名字。 */
    private static final String RULE = "mobSpawnVisualizer";

    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    /** 仍在淡出中的轨迹（只在客户端 tick 线程改动）。 */
    private static final List<LiveTrace> LIVE = new ArrayList<>();
    private static ClientLevel lastLevel;

    private MobSpawnVisualizer() {
    }

    /** 客户端入口调用一次：注册按键、tick 汇总与渲染回调。 */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(MobSpawnVisualizer::clientTick);
        LevelRenderEvents.BEFORE_GIZMOS.register(MobSpawnVisualizer::renderGizmos);
    }

    private static void clientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (!VisualizerState.isActive(RULE) || level == null) {
            LIVE.clear();
            lastLevel = null;
            snapshot = Snapshot.EMPTY;
            return;
        }
        if (level != lastLevel) {
            // 换世界后旧轨迹的坐标已经没有意义，直接丢掉。
            LIVE.clear();
            lastLevel = level;
        }
        for (SogSpawnTrace.Trace trace : SogSpawnTrace.drain()) {
            if (trace.dimension.equals(level.dimension())) {
                LIVE.add(new LiveTrace(trace));
            }
        }
        List<Segment> segments = new ArrayList<>();
        for (int i = LIVE.size() - 1; i >= 0; i--) {
            LiveTrace live = LIVE.get(i);
            live.age++;
            if (live.age >= LIFETIME_TICKS) {
                LIVE.remove(i);
                continue;
            }
            if (segments.size() < MAX_SEGMENTS) {
                buildSegments(level, live, segments);
            }
        }
        snapshot = new Snapshot(segments);
    }


    /**
     * 把一条轨迹还原成折线：区块最小角 →（红）起始高度 →（蓝）横移 →（绿）起始点 →
     * 每个候选点（灰／白）→（深灰）生物落点。线段用“终点”的颜色。
     */
    private static void buildSegments(ClientLevel level, LiveTrace live, List<Segment> out) {
        SogSpawnTrace.Trace trace = live.trace;
        BlockPos initial = trace.initial;
        int minY = level.getMinY();
        ChunkPos chunkPos = ChunkPos.containing(initial);
        double cornerX = chunkPos.getMinBlockX() + 0.5D;
        double cornerZ = chunkPos.getMinBlockZ() + 0.5D;
        double floorY = minY + 0.5D;
        double initX = initial.getX() + 0.5D;
        double initZ = initial.getZ() + 0.5D;

        float width = Math.max(0.0F, BASE_WIDTH * (1.0F - live.age / (float) LIFETIME_TICKS));
        int alpha = Math.max(0, Math.round(255.0F * (1.0F - live.age / (float) LIFETIME_TICKS)));

        Vec3 previous = new Vec3(cornerX, floorY, cornerZ);
        previous = addSegment(out, previous, new Vec3(cornerX, floorY, cornerZ), COLOR_GRAY, width, alpha);
        previous = addSegment(out, previous, new Vec3(initX, floorY, cornerZ), COLOR_RED, width, alpha);
        previous = addSegment(out, previous, new Vec3(initX, floorY, initZ), COLOR_BLUE, width, alpha);
        previous = addSegment(out, previous, new Vec3(initX, initial.getY() + 0.5D, initZ), COLOR_GREEN,
                width, alpha);

        for (int i = 0; i < trace.steps.size(); i++) {
            BlockPos step = trace.steps.get(i);
            Vec3 point = new Vec3(step.getX() + 0.5D, step.getY() + 0.5D, step.getZ() + 0.5D);
            int color = step.equals(trace.spawnedAt) ? COLOR_SPAWNED : COLOR_GRAY;
            previous = addSegment(out, previous, point, color, width, alpha);
        }
        if (trace.spawnedAt != null) {
            addSegment(out, previous,
                    new Vec3(trace.spawnedAt.getX() + 0.5D, trace.spawnedAt.getY(),
                            trace.spawnedAt.getZ() + 0.5D),
                    COLOR_DARK, width, alpha);
        }
    }

    /** 加一段线（起点沿用上一段的终点，颜色跟随终点），返回本次的终点。零长度段直接跳过。 */
    private static Vec3 addSegment(List<Segment> out, Vec3 from, Vec3 to, int toColor, float width,
            int alpha) {
        if (!from.equals(to)) {
            out.add(new Segment(from, to, withAlpha(toColor, alpha), width));
        }
        return to;
    }

    /** 把满不透明的基础色换成指定 alpha 的 ARGB。 */
    private static int withAlpha(int opaqueColor, int alpha) {
        return (opaqueColor & 0x00FFFFFF) | (alpha << 24);
    }

    private static void renderGizmos(LevelRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        Snapshot current = snapshot;
        if (current.segments.isEmpty()) {
            return;
        }
        try (Gizmos.TemporaryCollection collection = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
            for (int i = 0; i < current.segments.size(); i++) {
                Segment segment = current.segments.get(i);
                Gizmos.line(segment.from, segment.to, segment.color, segment.width);
            }
        }
    }

    /** 一条仍在淡出的轨迹。 */
    private static final class LiveTrace {
        private final SogSpawnTrace.Trace trace;
        private int age;

        private LiveTrace(SogSpawnTrace.Trace trace) {
            this.trace = trace;
        }
    }

    /** 一段待绘制线段。 */
    private static final class Segment {
        private final Vec3 from;
        private final Vec3 to;
        private final int color;
        private final float width;

        private Segment(Vec3 from, Vec3 to, int color, float width) {
            this.from = from;
            this.to = to;
            this.color = color;
            this.width = width;
        }
    }

    /** 只读渲染快照，tick 线程整体替换、渲染线程整体读取。 */
    private static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(Collections.<Segment>emptyList());

        private final List<Segment> segments;

        private Snapshot(List<Segment> segments) {
            this.segments = segments;
        }
    }
}
