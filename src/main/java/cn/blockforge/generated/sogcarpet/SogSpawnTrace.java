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
 * “刷怪游走可视化”的数据通道。
 *
 * <p>原版自然刷怪只在服务端跑：{@code NaturalSpawner.spawnCategoryForPosition} 在一个区块里
 * 先随机取一个起始点，然后每隔几步把候选点往随机方向挪一段，逐个测试“离玩家够远、地形合适、
 * 碰撞箱放得下”。{@link cn.blockforge.generated.sogcarpet.mixin.NaturalSpawnerMixin} 在
 * 服务端把这条“游走”过程记下来：起始点、每个候选点、以及真正刷出生物时生物落在哪。</p>
 *
 * <p>线程约定：记录发生在服务端线程（单机时是集成服务端线程），通过 {@link #drain()} 交给
 * 客户端 tick 线程，再由客户端渲染线程读快照。完成的轨迹放进带锁的小队列里，上限
 * {@value #MAX_COMPLETED} 条，超出丢最旧的，避免专用服务器上长期累积。</p>
 *
 * <p><b>内存</b>：专用服务器上往往没有任何客户端来 {@link #drain()}，这些轨迹会一直留到上限，
 * 所以队列用 {@link ArrayDeque}（出队 O(1)，不像 ArrayList 那样每丢一条都要搬整个数组），
 * 上限也从 512 压到 {@value #MAX_COMPLETED}；规则一关就整队清空（{@link #clear()}）。</p>
 */
public final class SogSpawnTrace {
    /** 最多暂存多少条已完成轨迹。 */
    private static final int MAX_COMPLETED = 256;

    private static final Deque<Trace> COMPLETED = new ArrayDeque<>();
    private static final ThreadLocal<MutableTrace> CURRENT = new ThreadLocal<>();

    private SogSpawnTrace() {
    }

    /** 一次 {@code spawnCategoryForPosition} 开始时调用，记下维度与起始点。 */
    public static void begin(ResourceKey<Level> dimension, BlockPos initial) {
        CURRENT.set(new MutableTrace(dimension, initial.immutable()));
    }

    /** 记录一个候选点（原版每次 {@code pos.set(...)} 之后调用）。 */
    public static void step(BlockPos pos) {
        MutableTrace trace = CURRENT.get();
        if (trace != null) {
            trace.steps.add(pos.immutable());
        }
    }

    /** 记录一次成功刷怪：把当前轨迹连同生物落点归档。 */
    public static void spawned(BlockPos pos) {
        MutableTrace trace = CURRENT.get();
        if (trace == null || trace.initial == null) {
            return;
        }
        Trace completed = new Trace(trace.dimension, trace.initial, new ArrayList<>(trace.steps), pos.immutable());
        synchronized (COMPLETED) {
            while (COMPLETED.size() >= MAX_COMPLETED) {
                COMPLETED.pollFirst();
            }
            COMPLETED.addLast(completed);
        }
    }

    /** 一次 {@code spawnCategoryForPosition} 结束时调用（无论规则中途有没有被关）。 */
    public static void end() {
        CURRENT.remove();
    }

    /** 取出并清空自上次调用以来新归档的轨迹；没有则返回空表。 */
    public static List<Trace> drain() {
        synchronized (COMPLETED) {
            if (COMPLETED.isEmpty()) {
                return Collections.emptyList();
            }
            List<Trace> copy = new ArrayList<>(COMPLETED);
            COMPLETED.clear();
            return copy;
        }
    }

    /** 规则关掉时整队清空：专用服务器上没人来 drain，别让旧轨迹占着内存。 */
    public static void clear() {
        synchronized (COMPLETED) {
            COMPLETED.clear();
        }
        CURRENT.remove();
    }

    /** 一条完成的刷怪轨迹：维度、起始点、全部候选点、生物落点。 */
    public static final class Trace {
        public final ResourceKey<Level> dimension;
        public final BlockPos initial;
        public final List<BlockPos> steps;
        public final BlockPos spawnedAt;

        private Trace(ResourceKey<Level> dimension, BlockPos initial, List<BlockPos> steps,
                BlockPos spawnedAt) {
            this.dimension = dimension;
            this.initial = initial;
            this.steps = steps;
            this.spawnedAt = spawnedAt;
        }
    }

    /** 正在记录中的可变轨迹（只在服务端线程访问）。 */
    private static final class MutableTrace {
        private final ResourceKey<Level> dimension;
        private final BlockPos initial;
        private final List<BlockPos> steps = new ArrayList<>();

        private MutableTrace(ResourceKey<Level> dimension, BlockPos initial) {
            this.dimension = dimension;
            this.initial = initial;
        }
    }
}
