package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.SogSpawnTrace;
import cn.blockforge.generated.sogcarpet.net.SpawnTracePayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * “刷怪游走可视化”的客户端绘制。
 *
 * <p><b>本轮是一次重做：轨迹等于生物真实走过的路。</b> 以前画的是原版
 * {@code NaturalSpawner} 在一个区块里“挑位置”的随机游走——那条线属于刷怪器，跟生物出生后
 * 往哪走毫无关系，所以看上去就是“怪物没沿着轨迹走”，而且只有刚刷出、还没走远的几只才有线。
 * 现在改成：</p>
 * <ul>
 *   <li>服务端只在生物<b>刷出来那一刻</b>发一条出生事件（实体号 + 出生坐标），
 *       见 {@code SpawnTracePayload}；</li>
 *   <li>客户端顺着实体号盯住这只生物，每 tick 采一个点，轨迹就是它自己走过的路，
 *       轨迹的末端始终连着它——怪物和轨迹永远对得上；</li>
 *   <li>只要生物活在玩家 {@value #TRACK_RANGE} 格以内就一定有轨迹，不再取决于它是不是
 *       “刚刷出来的那一只”，于是视野里的怪物都会同时有线。</li>
 * </ul>
 *
 * <p><b>轨迹画法</b>：</p>
 * <ul>
 *   <li><b>起点</b>：{@link Gizmos#point} 的亮绿色粗点，标在出生坐标（服务端没报出生点时
 *       退回“第一次看到它的位置”）；</li>
 *   <li><b>行走过程线</b>：近白色折线，沿路每隔几格叠一个 {@link Gizmos#arrow} 黄色箭头，
 *       指出行进方向；</li>
 *   <li><b>终点</b>：红色箭头，从生物当前位置沿它最后的移动方向（站着不动时按朝向）指出去。</li>
 * </ul>
 *
 * <p><b>服务器适配</b>：采样完全在客户端做，服务端从“每 tick 复制整条候选点列表下发”降到
 * “只在刷怪时记一条几字节的事件”，而且出生事件本来就只发给附近、明确说要收的玩家；
 * 客户端这边生物数、单条轨迹的采样点、一帧的图元数都有上限，手机刷怪塔里也不会被拖垮。</p>
 */
public final class MobSpawnVisualizer {
    /** 玩家周围多少格以内的生物才画轨迹。 */
    private static final double TRACK_RANGE = 128.0D;
    private static final double TRACK_RANGE_SQR = TRACK_RANGE * TRACK_RANGE;
    /** 相邻两个采样点至少隔这么多格才记一个新点，站着不动不会一直堆点。 */
    private static final double MIN_STEP = 0.35D;
    private static final double MIN_STEP_SQR = MIN_STEP * MIN_STEP;
    /** 单条轨迹最多保留多少个采样点，超出的从最旧的开始丢。 */
    private static final int MAX_POINTS = 96;
    /** 同时最多跟踪多少只生物（就近优先）。 */
    private static final int MAX_TRAILS = 48;
    /** 生物看不见或走出范围后，轨迹再淡出这么多 tick 才彻底移除。 */
    private static final int FADE_TICKS = 40;
    /** 服务端出生点的暂存上限：等客户端真看到生物时才用它当起点。 */
    private static final int MAX_SPAWN_POINTS = 512;
    /** 一帧最多画多少图元，兜住极限刷怪塔。 */
    private static final int MAX_PRIMITIVES = 12000;

    /** 行走过程线线宽。 */
    private static final float LINE_WIDTH = 3.5F;
    /** 箭头线宽。 */
    private static final float ARROW_WIDTH = 4.0F;
    /** 起点粗点的尺寸，单位是屏幕像素（原版 {@code Gizmos.point} 就是按像素画点）。 */
    private static final float START_POINT_SIZE = 9.0F;
    /** 终点箭头长度。原版箭头头的张开幅度是长度的 1/10（并限制在 0.1~1.0 格），所以别画太短。 */
    private static final double HEAD_ARROW_LENGTH = 3.0D;
    /** 过程箭头之间大约隔多少格。 */
    private static final double ARROW_SPACING = 5.0D;
    /** 单个过程箭头沿路画多长。 */
    private static final double ARROW_LENGTH = 3.0D;
    /** 单条轨迹最多画多少个过程箭头，避免长轨迹上箭头糊成一片。 */
    private static final int MAX_ARROWS_PER_TRAIL = 6;
    /** 轨迹离地高度，免得线正好压在方块顶面上看不清。 */
    private static final double Y_OFFSET = 0.12D;

    /** 起点粗点：亮绿。 */
    private static final int COLOR_START = 0xFF35FF6A;
    /** 行走过程线：近白。 */
    private static final int COLOR_PATH = 0xFFE6F2FF;
    /** 过程箭头：黄。 */
    private static final int COLOR_ARROW = 0xFFFFE24A;
    /** 终点箭头：红。 */
    private static final int COLOR_HEAD = 0xFFFF4136;

    /** 本规则在总控里的名字。 */
    private static final String RULE = "mobSpawnVisualizer";

    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    /** 生物网络实体号 → 正在跟踪的轨迹（只在客户端 tick 线程改动）。 */
    private static final Map<Integer, MobTrail> TRAILS = new HashMap<>();
    /** 服务端刚报来的出生点，等客户端真能看见这只生物时拿来当轨迹起点。 */
    private static final Map<Integer, Vec3> SPAWN_POINTS = new HashMap<>();
    private static ClientLevel lastLevel;

    private MobSpawnVisualizer() {
    }

    /** 客户端入口调用一次：注册 tick 汇总与渲染回调。 */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(MobSpawnVisualizer::clientTick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(MobSpawnVisualizer::renderGizmos);
    }

    /**
     * 网络包接收器（{@code SpawnTracePayload}）送来的出生事件。
     *
     * <p>只在收包线程把“实体号 → 出生坐标”塞进暂存表，不碰 {@link #TRAILS} 与渲染快照；
     * 下一次 {@link #clientTick} 会用它当新轨迹的起点。暂存表有上限，刷怪塔里一 tick
     * 来上千条时最旧的会被顶掉，内存有界。</p>
     */
    public static void accept(SpawnTracePayload payload) {
        if (payload == null || payload.events().isEmpty()) {
            return;
        }
        synchronized (SPAWN_POINTS) {
            for (SogSpawnTrace.SpawnEvent event : payload.events()) {
                while (SPAWN_POINTS.size() >= MAX_SPAWN_POINTS) {
                    Iterator<Integer> iterator = SPAWN_POINTS.keySet().iterator();
                    if (!iterator.hasNext()) {
                        break;
                    }
                    iterator.next();
                    iterator.remove();
                }
                SPAWN_POINTS.put(event.entityId(), Vec3.atBottomCenterOf(event.spawnPos()));
            }
        }
    }

    private static void clientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (!VisualizerState.isActive(RULE) || level == null) {
            reset();
            return;
        }
        if (level != lastLevel) {
            // 换世界后旧轨迹的坐标已经没有意义，直接丢掉。
            resetTracking();
            lastLevel = level;
        }
        Vec3 eye = client.player == null ? null : client.player.position();
        updateTrails(level, eye);
        snapshot = new Snapshot(buildPrimitives());
    }

    /** 关掉可视化时清空一切。 */
    private static void reset() {
        resetTracking();
        lastLevel = null;
        snapshot = Snapshot.EMPTY;
    }

    private static void resetTracking() {
        TRAILS.clear();
        synchronized (SPAWN_POINTS) {
            SPAWN_POINTS.clear();
        }
    }

    /**
     * 采样这一 tick 里所有看得见的生物。
     *
     * <p>先把已有轨迹全部标成“这一 tick 没见到”，再遍历客户端认识的实体，凡是
     * {@link Mob}、没被移除、又在 {@link #TRACK_RANGE} 以内的就认领或新建一条轨迹并采样；
     * 没被认领的（死了、走远了、区块卸载了）开始倒计时淡出。轨迹数有上限，超了就不再新建，
     * 于是刷怪塔里也不会为了几万只怪堆内存。</p>
     */
    private static void updateTrails(ClientLevel level, Vec3 eye) {
        for (MobTrail trail : TRAILS.values()) {
            trail.seen = false;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof Mob mob) || mob.isRemoved()) {
                continue;
            }
            Vec3 pos = mob.position();
            if (eye != null && pos.distanceToSqr(eye) > TRACK_RANGE_SQR) {
                continue;
            }
            MobTrail trail = TRAILS.get(mob.getId());
            if (trail == null) {
                if (TRAILS.size() >= MAX_TRAILS) {
                    continue;
                }
                trail = new MobTrail(startOf(mob, pos));
                TRAILS.put(mob.getId(), trail);
            }
            trail.seen = true;
            trail.missingTicks = 0;
            trail.update(pos, mob.getYRot());
        }
        Iterator<MobTrail> iterator = TRAILS.values().iterator();
        while (iterator.hasNext()) {
            MobTrail trail = iterator.next();
            if (trail.seen) {
                continue;
            }
            trail.missingTicks++;
            if (trail.missingTicks > FADE_TICKS) {
                iterator.remove();
            }
        }
    }

    /** 轨迹起点：有服务端报来的出生点就用出生点，否则退回第一次看到它的位置。 */
    private static Vec3 startOf(Mob mob, Vec3 pos) {
        Vec3 spawn;
        synchronized (SPAWN_POINTS) {
            spawn = SPAWN_POINTS.remove(mob.getId());
        }
        return spawn == null ? pos : spawn;
    }

    /** 把当前所有轨迹编成待绘制图元。 */
    private static List<Primitive> buildPrimitives() {
        List<Primitive> out = new ArrayList<>();
        for (MobTrail trail : TRAILS.values()) {
            if (out.size() >= MAX_PRIMITIVES) {
                break;
            }
            appendTrail(trail, out);
        }
        return out;
    }

    /**
     * 一条轨迹的全部图元：起点粗点 → 行走过程线（带方向箭头）→ 终点箭头。
     *
     * <p>线段用近白色，每隔约 {@link #ARROW_SPACING} 格叠一个黄色箭头指出行进方向；
     * 最后再画一个红色箭头，从生物当前位置沿它最后的移动方向指出去。轨迹末端直接取
     * 生物这一 tick 的坐标，所以怪走到哪线就跟到哪。</p>
     */
    private static void appendTrail(MobTrail trail, List<Primitive> out) {
        List<Vec3> points = trail.points;
        if (points.isEmpty()) {
            return;
        }
        // 生物消失后按剩余寿命线性淡出；还活着就整条满不透明。
        float alpha = trail.missingTicks <= 0
                ? 1.0F
                : Math.max(0.0F, 1.0F - trail.missingTicks / (float) FADE_TICKS);
        int lineColor = withAlpha(COLOR_PATH, alpha);
        int arrowColor = withAlpha(COLOR_ARROW, alpha);
        int headColor = withAlpha(COLOR_HEAD, alpha);
        int startColor = withAlpha(COLOR_START, alpha);
        float lineWidth = Math.max(1.0F, LINE_WIDTH * alpha);
        float arrowWidth = Math.max(1.0F, ARROW_WIDTH * alpha);

        // 起点：粗点。
        out.add(Primitive.point(points.get(0), startColor, START_POINT_SIZE));

        // 把“上一个采样点 → 生物当前位置”也接上，保证轨迹末端连着怪。
        Vec3 head = trail.current;
        int count = points.size();
        boolean headIsFresh = head != null && head.distanceToSqr(points.get(count - 1)) > 1.0E-6D;

        double total = pathLength(points, headIsFresh ? head : null);
        double spacing = Math.max(ARROW_SPACING, total / MAX_ARROWS_PER_TRAIL);
        double nextArrow = spacing;
        double walked = 0.0D;
        int arrows = 0;

        Vec3 from = points.get(0);
        for (int i = 1; i <= count; i++) {
            Vec3 to = i < count ? points.get(i) : (headIsFresh ? head : null);
            if (to == null) {
                break;
            }
            Vec3 delta = to.subtract(from);
            double length = delta.length();
            if (length > 1.0E-6D) {
                Vec3 direction = delta.scale(1.0D / length);
                while (arrows < MAX_ARROWS_PER_TRAIL && walked + length >= nextArrow) {
                    double offset = nextArrow - walked;
                    Vec3 at = from.add(direction.scale(offset));
                    out.add(Primitive.arrow(at, at.add(direction.scale(ARROW_LENGTH)),
                            arrowColor, arrowWidth));
                    arrows++;
                    nextArrow += spacing;
                }
                out.add(Primitive.line(from, to, lineColor, lineWidth));
                walked += length;
            }
            from = to;
            if (out.size() >= MAX_PRIMITIVES) {
                return;
            }
        }

        // 终点：沿生物当前朝向（没动过就看它的朝向）指出去的红箭头。
        if (head != null) {
            Vec3 direction = trail.heading != null
                    ? trail.heading
                    : Vec3.directionFromRotation(0.0F, trail.yaw);
            if (direction.lengthSqr() > 1.0E-6D) {
                out.add(Primitive.arrow(head, head.add(direction.normalize().scale(HEAD_ARROW_LENGTH)),
                        headColor, arrowWidth));
            }
        }
    }

    /** 折线总长度，用来决定过程箭头之间的间距。 */
    private static double pathLength(List<Vec3> points, Vec3 head) {
        double total = 0.0D;
        for (int i = 1; i < points.size(); i++) {
            total += points.get(i).distanceTo(points.get(i - 1));
        }
        if (head != null) {
            total += head.distanceTo(points.get(points.size() - 1));
        }
        return total;
    }

    /** 把满不透明的基础色换成指定 alpha 的 ARGB。 */
    private static int withAlpha(int opaqueColor, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(255.0F * alpha)));
        return (opaqueColor & 0x00FFFFFF) | (a << 24);
    }

    private static void renderGizmos(WorldRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        Snapshot current = snapshot;
        if (current.primitives.isEmpty()) {
            return;
        }
        try (Gizmos.TemporaryCollection collection = context.worldRenderer().collectPerFrameGizmos()) {
            for (int i = 0; i < current.primitives.size(); i++) {
                Primitive primitive = current.primitives.get(i);
                switch (primitive.kind) {
                    case Primitive.KIND_POINT -> Gizmos.point(primitive.from, primitive.color,
                            primitive.width);
                    case Primitive.KIND_ARROW -> Gizmos.arrow(primitive.from, primitive.to,
                            primitive.color, primitive.width);
                    default -> Gizmos.line(primitive.from, primitive.to, primitive.color,
                            primitive.width);
                }
            }
        }
    }

    /** 一只生物正在被跟踪的轨迹（只在客户端 tick 线程改动）。 */
    private static final class MobTrail {
        /** 采样点（已抬高 {@link #Y_OFFSET}），第一个是起点。 */
        private final List<Vec3> points = new ArrayList<>();
        /** 生物这一 tick 的位置（同样已抬高）；渲染时把折线一直连到这里。 */
        private Vec3 current;
        /** 上一次采样到的原始位置，用来算移动方向。 */
        private Vec3 lastPos;
        /** 最后一次有意义的移动方向（单位向量），没动过时为 null。 */
        private Vec3 heading;
        private float yaw;
        private boolean seen;
        private int missingTicks;

        private MobTrail(Vec3 start) {
            Vec3 raised = raise(start);
            points.add(raised);
            current = raised;
            lastPos = start;
        }

        private void update(Vec3 pos, float yaw) {
            this.yaw = yaw;
            Vec3 raised = raise(pos);
            Vec3 delta = pos.subtract(lastPos);
            if (delta.lengthSqr() > 1.0E-6D) {
                this.heading = delta.normalize();
            }
            if (raised.distanceToSqr(points.get(points.size() - 1)) >= MIN_STEP_SQR) {
                points.add(raised);
                if (points.size() > MAX_POINTS) {
                    points.remove(0);
                }
            }
            this.current = raised;
            this.lastPos = pos;
        }

        private static Vec3 raise(Vec3 pos) {
            return pos.add(0.0D, Y_OFFSET, 0.0D);
        }
    }

    /** 待绘制图元：线段、箭头或点。 */
    private static final class Primitive {
        private static final int KIND_LINE = 0;
        private static final int KIND_ARROW = 1;
        private static final int KIND_POINT = 2;

        private final int kind;
        private final Vec3 from;
        private final Vec3 to;
        private final int color;
        /** 线段／箭头的线宽，点的屏幕像素尺寸。 */
        private final float width;

        private Primitive(int kind, Vec3 from, Vec3 to, int color, float width) {
            this.kind = kind;
            this.from = from;
            this.to = to;
            this.color = color;
            this.width = width;
        }

        private static Primitive line(Vec3 from, Vec3 to, int color, float width) {
            return new Primitive(KIND_LINE, from, to, color, width);
        }

        private static Primitive arrow(Vec3 from, Vec3 to, int color, float width) {
            return new Primitive(KIND_ARROW, from, to, color, width);
        }

        private static Primitive point(Vec3 at, int color, float size) {
            return new Primitive(KIND_POINT, at, null, color, size);
        }
    }

    /** 只读渲染快照，tick 线程整体替换、渲染线程整体读取。 */
    private static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(Collections.<Primitive>emptyList());

        private final List<Primitive> primitives;

        private Snapshot(List<Primitive> primitives) {
            this.primitives = primitives;
        }
    }
}
