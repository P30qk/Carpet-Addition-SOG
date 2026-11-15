package cn.blockforge.generated.sogcarpet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * “幽匿检测范围可视化”里“触发判定线”的服务端数据通道。
 *
 * <p>幽匿感测体与坚守者收到振动（声音）时，服务端在两个 {@code onReceiveVibration} 收口点各记
 * 一条极小的 {@link Event}：收到振动的检测体方块坐标＋引起振动的声音源方块坐标。服务端每 tick
 * 由 {@link SogSculkTriggerSync} 取出并只发给附近、明确要收的玩家；客户端把两点连成一条短暂
 * 显示的线。</p>
 *
 * <p>规则关闭、或没有一个装了本模组且开着这条可视化的玩家在线时，{@code recording} 为 false，
 * 注入点照常存在但一律空转，连事件都不记。</p>
 */
public final class SogSculkTrigger {
    /** 最多暂存多少条触发事件，防爆。 */
    private static final int MAX_EVENTS = 1024;

    private static final Deque<Event> EVENTS = new ArrayDeque<>();
    private static volatile boolean recording;

    private SogSculkTrigger() {
    }

    /** 现在有没有必要采集（规则开 && 至少一个装了本模组且开着这条可视化的玩家在线）。 */
    public static boolean isRecording() {
        return recording;
    }

    public static void setRecording(boolean value) {
        recording = value;
    }

    /** 记一次振动触发：{@code receiver} 是收到振动的检测体，{@code source} 是声音源。 */
    public static void received(ServerLevel level, BlockPos receiver, BlockPos source) {
        if (!recording || level == null || receiver == null || source == null) {
            return;
        }
        Event event = new Event(level.dimension(), receiver.immutable(), source.immutable());
        synchronized (EVENTS) {
            while (EVENTS.size() >= MAX_EVENTS) {
                EVENTS.pollFirst();
            }
            EVENTS.addLast(event);
        }
    }

    /** 取出并清空自上次调用以来新记录的事件。 */
    public static List<Event> drain() {
        synchronized (EVENTS) {
            if (EVENTS.isEmpty()) {
                return Collections.emptyList();
            }
            List<Event> copy = new ArrayList<>(EVENTS);
            EVENTS.clear();
            return copy;
        }
    }

    /** 规则关闭时整队清空。 */
    public static void clear() {
        synchronized (EVENTS) {
            EVENTS.clear();
        }
    }

    /** 一次振动触发：维度、检测体坐标、声音源坐标。 */
    public record Event(ResourceKey<Level> dimension, BlockPos receiver, BlockPos source) {
    }
}
