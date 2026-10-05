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
 * “刷怪游走可视化”的服务端数据通道。
 *
 * <p><b>本轮起只传“出生事件”。</b> 以前服务端会把 {@code NaturalSpawner} 在一个区块里
 * 逐个候选点“游走”的整条路线都记下来再下发；那条路线是<b>刷怪器在挑位置</b>，
 * 和生物出生后真正走的路线完全没有关系，画出来就是“怪物没沿着轨迹走”。现在改成：
 * 服务端只在自然刷怪真正刷出一只生物时记一条极小的
 * {@link SpawnEvent}（维度、生物的网络实体号、出生方块坐标），
 * 客户端拿到后自己去盯这只生物的每一步移动，把<b>它真实走过的路</b>画成轨迹。</p>
 *
 * <p>于是服务端这边的工作量只剩一次“记一条记录”：不再包装 {@code BlockPos.MutableBlockPos#set}
 * 去抓候选点，也不再为每条轨迹复制候选点列表，专用服务器上每个 tick 刷出上百只生物时
 * 分配量骤降。事件队列仍每 tick 由 {@link SogSpawnTraceSync} 取空并下发，
 * 保留 {@value #MAX_EVENTS} 条上限兜底；规则一关就整队清空（{@link #clear()}）。</p>
 *
 * <p>线程约定：记录发生在服务端主线程，之后由服务端每 tick 调用 {@link #drain()} 取出，
 * 打包成 {@code SpawnTracePayload} 发给装了本模组的客户端。</p>
 */
public final class SogSpawnTrace {
    /** 最多暂存多少条出生事件。 */
    private static final int MAX_EVENTS = 1024;

    private static final Deque<SpawnEvent> COMPLETED = new ArrayDeque<>();

    /**
     * 当前是否有人在看：由 {@link SogSpawnTraceSync} 每个服务端 tick 末尾刷新。
     *
     * <p>专用服务器上一个装了本模组、且能收出生事件的玩家都没有时，服务端连
     * “记一条出生事件”都不做——注入点还在，但一律空转。规则关掉时同样为 {@code false}。</p>
     */
    private static volatile boolean recording;

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

    /**
     * 记录一次成功的自然刷怪。
     *
     * @param dimension 生物所在维度
     * @param entityId  生物的网络实体号，客户端用 {@code ClientLevel#getEntity(int)} 找回它
     * @param pos       出生方块坐标，客户端把它当成轨迹的起始点（画粗点）
     */
    public static void spawned(ResourceKey<Level> dimension, int entityId, BlockPos pos) {
        if (!recording) {
            return;
        }
        SpawnEvent event = new SpawnEvent(dimension, entityId, pos.immutable());
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
    }

    /** 一次自然刷怪成功：维度、生物的网络实体号、出生方块坐标。 */
    public record SpawnEvent(ResourceKey<Level> dimension, int entityId, BlockPos spawnPos) {
    }
}
