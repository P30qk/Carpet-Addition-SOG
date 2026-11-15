package cn.blockforge.generated.sogcarpet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * “刷怪可视化”的服务端数据通道。
 *
 * <p>同时承载两类信息：</p>
 * <ul>
 *   <li><b>出生事件</b>：生物真正进入世界时的维度、网络实体号、出生坐标。客户端据此盯住这只
 *       生物逐 tick 采样，把<b>它真实走过的路</b>画成轨迹（末端始终连着它）。</li>
 *   <li><b>刷怪候选路径</b>（移植自 lucidity）：这次刷怪过程中，刷怪器依次考察过的候选位置，
 *       以及它一开始挑中的锚点（{@code initial}）。客户端把这些候选点连成折线，命中的那一个
 *       高亮成白色，于是能看出刷怪器“游走挑点”的过程与成功落点。</li>
 * </ul>
 *
 * <p>线程约定：候选路径在 {@code spawnCategoryForPosition} 的一次调用内累积，存在一个
 * {@link ThreadLocal} 里；服务端主线程刷出生物时把它随出生事件一起入队。之后由服务端每 tick
 * 调用 {@link #drain()} 取出，打包成 {@code SpawnTracePayload} 发给装了本模组的客户端。</p>
 */
public final class SogSpawnTrace {
    /** 最多暂存多少条出生事件。 */
    private static final int MAX_EVENTS = 1024;
    /** 单次刷怪最多记录多少个候选位置。 */
    private static final int MAX_CANDIDATES = 64;

    private static final Deque<SpawnEvent> COMPLETED = new ArrayDeque<>();

    /**
     * 当前是否有人在看：由 {@link SogSpawnTraceSync} 每个服务端 tick 末尾刷新。
     *
     * <p>专用服务器上一个装了本模组、且能收出生事件的玩家都没有时，服务端连候选点都不记，
     * 全部空转。规则关掉时同样为 {@code false}。</p>
     */
    private static volatile boolean recording;

    /** 当前这次 {@code spawnCategoryForPosition} 正在累积的候选路径（服务端主线程）。 */
    private static final ThreadLocal<Trace> CURRENT = ThreadLocal.withInitial(Trace::new);

    private SogSpawnTrace() {
    }

    /** 现在有没有必要采集（规则开 &amp;&amp; 至少一个装了本模组的玩家在线）。 */
    public static boolean isRecording() {
        return recording;
    }

    /** 由服务端每 tick 刷新。 */
    public static void setRecording(boolean value) {
        recording = value;
    }

    /** 一次 {@code spawnCategoryForPosition} 开始：记下锚点位置并清空游走列表。 */
    public static void beginTrace(BlockPos initial) {
        Trace trace = CURRENT.get();
        trace.active = recording;
        trace.initial = initial == null ? null : initial.immutable();
        trace.roamSteps.clear();
    }

    /**
     * 记录一次“游走”经过的候选位置（原版刷怪器每随机偏移一次就调用一次距离检查，这里把所有
     * 偏移点都记下来），于是客户端能画出与刷怪算法一致的完整游走折线，而不是只画通过校验的点。
     */
    public static void addRoamStep(BlockPos pos) {
        if (!recording || pos == null) {
            return;
        }
        Trace trace = CURRENT.get();
        if (!trace.active || trace.roamSteps.size() >= MAX_CANDIDATES) {
            return;
        }
        trace.roamSteps.add(pos.immutable());
    }

    /** 一次 {@code spawnCategoryForPosition} 结束：清掉线程本地的临时路径。 */
    public static void endTrace() {
        Trace trace = CURRENT.get();
        trace.active = false;
        trace.initial = null;
        trace.roamSteps.clear();
    }

    /**
     * 记录一次成功的自然刷怪，并附上本次刷怪的候选路径。
     *
     * @param dimension 生物所在维度
     * @param entityId  生物的网络实体号，客户端用 {@code ClientLevel#getEntity(int)} 找回它
     * @param pos       出生方块坐标，客户端把它当成轨迹的起始点（画粗点）
     */
    public static void spawned(ResourceKey<Level> dimension, int entityId, BlockPos pos) {
        if (!recording) {
            return;
        }
        Trace trace = CURRENT.get();
        BlockPos initial = trace.initial;
        List<BlockPos> attempts = new ArrayList<>(trace.roamSteps);
        SpawnEvent event = new SpawnEvent(dimension, entityId, pos.immutable(), initial, attempts);
        synchronized (COMPLETED) {
            while (COMPLETED.size() >= MAX_EVENTS) {
                COMPLETED.pollFirst();
            }
            COMPLETED.addLast(event);
        }
    }

    /** 取出并清空自上次调用以来新记录的出生事件；没有则返回空表。 */
    public static List<SpawnEvent> drain() {
        synchronized (COMPLETED) {
            if (COMPLETED.isEmpty()) {
                return Collections.emptyList();
            }
            List<SpawnEvent> copy = new ArrayList<>(COMPLETED);
            COMPLETED.clear();
            return copy;
        }
    }

    /** 规则关掉时整队清空：立刻丢掉还没发出去的旧事件。 */
    public static void clear() {
        synchronized (COMPLETED) {
            COMPLETED.clear();
        }
        endTrace();
    }

    /** 当前一次刷怪累积中的候选路径。 */
    private static final class Trace {
        private boolean active;
        private BlockPos initial;
        private final List<BlockPos> roamSteps = new ArrayList<>();
    }

    /**
     * 一次自然刷怪成功：维度、生物的网络实体号、出生方块坐标、本次刷怪的锚点位置与候选路径。
     *
     * @param attempts 刷怪器依次考察过、并进入类型校验的候选位置（按发生顺序）；可能为空
     */
    public record SpawnEvent(ResourceKey<Level> dimension, int entityId, BlockPos spawnPos,
            BlockPos initial, List<BlockPos> attempts) {
    }
}
