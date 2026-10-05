package cn.blockforge.generated.sogcarpet.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.phys.AABB;

/**
 * “末影龙破坏范围可视化”规则的客户端实现（按 lucidity 的写法重做）。
 *
 * <p>末影龙飞行时会撞毁沿途的方块。原版 {@code EnderDragon#aiStep()} 只拿
 * <b>头、脖子、身体</b>三个部位（{@code getSubEntities()} 的前三项）的包围盒去调用
 * {@code checkWalls(...)}，逐个方块判断：不是空气、不在 {@code DRAGON_TRANSPARENT} 里、
 * 且（开启怪物破坏时）不在 {@code DRAGON_IMMUNE} 里，就把方块直接移除——翅膀和尾巴不参与破坏。</p>
 *
 * <p>lucidity 的 {EnderdragonMixin} 把这三个部位各“对齐到整数方块”后，用同一个颜色
 * {@code ender_dragon_destruction_color}（137, 101, 255，alpha 100）画成半透明实心盒子。
 * 这里照此重做：<b>只画三个对齐后的紫色半透明盒子</b>，去掉以前那张整体白框与逐格红边
 * （白框与红边既不属 lucidity 的做法，和盒子叠在一起也看不清体积边界）。</p>
 *
 * <p>每 {@value #SCAN_INTERVAL_TICKS} tick 重扫一次，渲染线程只读快照。</p>
 */
public final class EnderDragonBreakRangeVisualizer {
    /** 本规则在总控里的名字。 */
    private static final String RULE = "enderDragonBreakRangeVisualizer";

    /** 扫描节流：每多少 tick 重算一次快照。 */
    private static final int SCAN_INTERVAL_TICKS = 5;
    /** 参与破坏方块的部位个数：头、脖子、身体。 */
    private static final int WALL_BREAK_PARTS = 3;

    /** lucidity 默认色 ender_dragon_destruction_color：(137, 101, 255)，alpha 100。 */
    private static final int COLOR_PART_FILL = 0x648965FF;
    /** 部位体积的描边（同色，几乎不透明），保证从任何角度都能看清盒子边界。 */
    private static final int COLOR_PART_STROKE = 0xE68965FF;
    private static final float PART_STROKE_WIDTH = 2.5F;
    /** 头 / 脖子 / 身体三块体积互相重叠，面重合会 z-fighting 闪烁：逐块错开一点点。 */
    private static final double PART_INFLATE = 0.003D;
    private static final double PART_INFLATE_STEP = 0.0015D;

    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    private static int tickCounter;

    private EnderDragonBreakRangeVisualizer() {
    }

    /** 客户端入口调用一次：tick 扫描与渲染回调（Ctrl+O 总开关在 {@link VisualizerState}）。 */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(EnderDragonBreakRangeVisualizer::clientTick);
        LevelRenderEvents.BEFORE_GIZMOS.register(EnderDragonBreakRangeVisualizer::renderGizmos);
    }

    private static void clientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (!VisualizerState.isActive(RULE) || level == null) {
            snapshot = Snapshot.EMPTY;
            return;
        }
        if (++tickCounter < SCAN_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        snapshot = scan(level);
    }

    private static Snapshot scan(ClientLevel level) {
        List<AABB> boxes = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof EnderDragon)) {
                continue;
            }
            EnderDragon dragon = (EnderDragon) entity;
            EnderDragonPart[] parts = dragon.getSubEntities();
            int count = Math.min(WALL_BREAK_PARTS, parts.length);
            for (int i = 0; i < count; i++) {
                boxes.add(snapToBlocks(parts[i].getBoundingBox()));
            }
        }
        if (boxes.isEmpty()) {
            return Snapshot.EMPTY;
        }
        return new Snapshot(boxes);
    }

    /**
     * 把部位包围盒对齐到整数方块：范围取 floor(最小) 到 floor(最大) + 1，
     * 与 lucidity 的 {@code getDestructionAABB} 完全一致，也就是原版 {@code checkWalls}
     * 真正会遍历到的那批方块。
     */
    private static AABB snapToBlocks(AABB box) {
        return new AABB(
                Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(box.maxX) + 1.0D, Mth.floor(box.maxY) + 1.0D, Mth.floor(box.maxZ) + 1.0D);
    }

    private static void renderGizmos(LevelRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        Snapshot current = snapshot;
        if (current.boxes.isEmpty()) {
            return;
        }
        try (Gizmos.TemporaryCollection collection = context.levelRenderer().collectPerFrameGizmos()) {
            for (int i = 0; i < current.boxes.size(); i++) {
                Gizmos.cuboid(current.boxes.get(i).inflate(PART_INFLATE + i * PART_INFLATE_STEP),
                        GizmoStyle.strokeAndFill(COLOR_PART_STROKE, PART_STROKE_WIDTH, COLOR_PART_FILL));
            }
        }
    }

    /** 只读渲染快照，tick 线程整体替换、渲染线程整体读取。 */
    private static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(Collections.<AABB>emptyList());

        private final List<AABB> boxes;

        private Snapshot(List<AABB> boxes) {
            this.boxes = boxes;
        }
    }
}
