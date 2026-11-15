package cn.blockforge.generated.sogcarpet.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * “物品合并范围可视化”的客户端绘制（移植自 lucidity 的 {@code itemMergRange}）。
 *
 * <p>原版 {@link ItemEntity} 每 tick 会看待自己包围盒向外扩 {@code 0.5} 格（只扩水平方向）的
 * 范围里有没有别的可合并物品；lucidity 直接把这块范围画成一个半透明黄色盒子，于是掉落物
 * 一多就能一眼看出哪些物品马上会被吸到一起。</p>
 *
 * <p>完全在客户端完成：物品实体本来就会同步到客户端，服务端不需要做任何事。为了在刷石机、
 * 分类机这种掉一堆物品的场合也不拖垮客户端，只扫玩家 {@value #SCAN_RANGE} 格以内的物品，
 * 同屏最多画 {@value #MAX_BOXES} 个（优先保留离玩家最近的）。</p>
 *
 * <p>快照每 {@value #INTERVAL_TICKS} tick 只重算“画哪些物品”，盒子本身每帧按插值位置重建：
 * 盒子跟随物品平滑移动，不会每 2 tick 才跳一次位置（挪动起来就不会一抖一抖）；盒子底面
 * 比地面略低一点，避免和方块顶面共面产生深度打架（z-fighting）闪烁。</p>
 */
public final class ItemMergeRangeVisualizer {
    private static final String RULE = "itemMergeRangeVisualizer";

    /** 只画玩家周围这个距离（格）以内的物品合并范围。 */
    private static final double SCAN_RANGE = 32.0D;
    private static final double SCAN_RANGE_SQR = SCAN_RANGE * SCAN_RANGE;
    /** 收集阶段最多保留多少个候选（排序前的硬上限，兜住极端情况）。 */
    private static final int MAX_CANDIDATES = 4096;
    /** 同屏最多画多少个盒子。 */
    private static final int MAX_BOXES = 256;
    /** 每隔多少客户端 tick 重算一次“画哪些物品”。 */
    private static final int INTERVAL_TICKS = 2;

    /** 合并范围盒的水平半宽：物品包围盒半宽 0.125 加上原版水平外扩的 0.5。 */
    private static final double BOX_HALF = 0.125D + 0.5D;
    /** 盒底比物品脚底再低一点，避免与方块顶面共面产生深度打架（z-fighting）闪烁。 */
    private static final double GROUND_EPSILON = 0.01D;

    /** lucidity 的 item_merg_range_visualize_color：黄，基础透明度 100/255。 */
    private static int COLOR_FILL = 0x64FFFF00;
    private static int COLOR_STROKE = 0xE6FFFF00;
    private static final float STROKE_WIDTH = 1.5F;

    /** 从用户配置刷新颜色（没有配置时回落到默认值）。 */
    private static void refreshColors() {
        COLOR_FILL = VisualizerConfig.color(RULE, "fill", 0x64FFFF00);
        COLOR_STROKE = VisualizerConfig.color(RULE, "stroke", 0xE6FFFF00);
    }

    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    private static int tickCounter;

    private ItemMergeRangeVisualizer() {
    }

    /** 客户端入口调用一次。 */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ItemMergeRangeVisualizer::clientTick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ItemMergeRangeVisualizer::renderGizmos);
    }

    private static void clientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (!VisualizerState.isActive(RULE) || level == null || client.player == null) {
            snapshot = Snapshot.EMPTY;
            return;
        }
        if (++tickCounter < INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        Vec3 eye = client.player.position();
        List<Entry> entries = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity item) || item.isRemoved()) {
                continue;
            }
            double distanceSqr = item.position().distanceToSqr(eye);
            if (distanceSqr > SCAN_RANGE_SQR) {
                continue;
            }
            // 与原版 ItemEntity#mergeWithNeighbours 完全一致：包围盒只在水平方向外扩 0.5 格。
            // 盒子坐标不在这里算，每次渲染时按当前插值位置重建，让盒子跟随物品平滑移动。
            entries.add(new Entry(item, distanceSqr));
            if (entries.size() >= MAX_CANDIDATES) {
                break;
            }
        }
        if (entries.size() > MAX_BOXES) {
            entries.sort(Comparator.comparingDouble(Entry::distanceSqr));
            entries = new ArrayList<>(entries.subList(0, MAX_BOXES));
        }
        snapshot = new Snapshot(entries);
    }

    private static void renderGizmos(WorldRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        refreshColors();
        Snapshot current = snapshot;
        if (current.entries.isEmpty()) {
            return;
        }
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        GizmoStyle style = GizmoStyle.strokeAndFill(COLOR_STROKE, STROKE_WIDTH, COLOR_FILL);
        try (Gizmos.TemporaryCollection collection =
                context.worldRenderer().collectPerFrameGizmos()) {
            for (int i = 0; i < current.entries.size(); i++) {
                ItemEntity item = current.entries.get(i).item();
                if (item.isRemoved() || item.getItem().isEmpty()) {
                    continue;
                }
                // 每帧按插值位置重建盒子：跟随物品平滑移动，不会每 2 tick 跳一次。
                Vec3 pos = item.getPosition(partialTick);
                Gizmos.cuboid(new AABB(
                        pos.x - BOX_HALF, pos.y - GROUND_EPSILON, pos.z - BOX_HALF,
                        pos.x + BOX_HALF, pos.y + item.getBbHeight(), pos.z + BOX_HALF), style);
            }
        }
    }

    /** 收集阶段的一对“物品 + 到玩家距离”。 */
    private record Entry(ItemEntity item, double distanceSqr) {
    }

    /** 只读渲染快照。 */
    private static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(Collections.<Entry>emptyList());

        private final List<Entry> entries;

        private Snapshot(List<Entry> entries) {
            this.entries = entries;
        }
    }
}
