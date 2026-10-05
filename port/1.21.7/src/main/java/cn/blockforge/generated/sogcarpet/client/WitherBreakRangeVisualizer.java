package cn.blockforge.generated.sogcarpet.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.AABB;

/**
 * “凋零破坏范围可视化”规则的客户端实现（按 lucidity 的写法重做）。
 *
 * <p>凋灵被打中之后，原版会等 {@code destroyBlocksTick} 归零时把身边一片区域里可破坏的
 * 方块全部打掉。范围就是一个<b>红色半透明实心立方体</b>：水平半径
 * {@code floor(bbWidth / 2 + 1)} 格、竖直从脚下方块起 {@code floor(bbHeight)} 格，
 * 与原版 {@code WitherBoss#customServerAiStep} 里 {@code BlockPos.betweenClosed} 的循环逐格一致。</p>
 *
 * <p>lucidity 的做法就是“整个范围画一个盒子、颜色取 {@code wither_destruction_range_color}”，
 * 不做逐格描边。这里照此重做：只保留一个红色半透明立方体（外面套一圈稍亮的描边，
 * 让六个棱从任何角度都看得清），去掉以前那层橙红单格描边——它和范围盒子叠在一起既乱又费性能。</p>
 *
 * <p>凋灵的破坏范围是静态的（只随它自己的位置走），所以每
 * {@value #SCAN_INTERVAL_TICKS} tick 重扫一次，渲染线程只读快照。</p>
 */
public final class WitherBreakRangeVisualizer {
    /** 本规则在总控里的名字。 */
    private static final String RULE = "witherBreakRangeVisualizer";

    /** 扫描节流：每多少 tick 重算一次快照。 */
    private static final int SCAN_INTERVAL_TICKS = 5;

    /** lucidity 默认色 wither_destruction_range_color：(255, 0, 0)，alpha 100。 */
    private static final int COLOR_RANGE_FILL = 0x64FF0000;
    /** 范围立方体的描边（亮红，几乎不透明）：加一圈棱之后从任何角度都能一眼看出范围。 */
    private static final int COLOR_RANGE_STROKE = 0xE6FF6B6B;
    /** 描边宽度。 */
    private static final float RANGE_STROKE_WIDTH = 2.5F;
    /** 边界往外让一点：范围立方体的六个面正好落在整数方块面上，和世界方块共面会
     *  深度打架（z-fighting），表现为侧面闪烁或干脆看不见。 */
    private static final double RANGE_INFLATE = 0.005D;

    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    private static int tickCounter;

    private WitherBreakRangeVisualizer() {
    }

    /** 客户端入口调用一次：tick 扫描与渲染回调（Ctrl+O 总开关在 {@link VisualizerState}）。 */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(WitherBreakRangeVisualizer::clientTick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(WitherBreakRangeVisualizer::renderGizmos);
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
            if (!(entity instanceof WitherBoss)) {
                continue;
            }
            WitherBoss wither = (WitherBoss) entity;
            int radius = Mth.floor(wither.getBbWidth() / 2.0F + 1.0F);
            int height = Mth.floor(wither.getBbHeight());
            BlockPos base = wither.blockPosition();
            int minX = base.getX() - radius;
            int minZ = base.getZ() - radius;
            int maxX = base.getX() + radius;
            int maxZ = base.getZ() + radius;
            // betweenClosed 覆盖的是整数方块坐标，换算成世界 AABB 要各加一格。
            boxes.add(new AABB(minX, base.getY(), minZ, maxX + 1.0D, base.getY() + height + 1.0D, maxZ + 1.0D));
        }
        if (boxes.isEmpty()) {
            return Snapshot.EMPTY;
        }
        return new Snapshot(boxes);
    }

    private static void renderGizmos(WorldRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        Snapshot current = snapshot;
        if (current.boxes.isEmpty()) {
            return;
        }
        try (Gizmos.TemporaryCollection collection = Gizmos.begin(context)) {
            GizmoStyle range = GizmoStyle.strokeAndFill(COLOR_RANGE_STROKE, RANGE_STROKE_WIDTH,
                    COLOR_RANGE_FILL);
            for (int i = 0; i < current.boxes.size(); i++) {
                Gizmos.cuboid(current.boxes.get(i).inflate(RANGE_INFLATE), range);
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
