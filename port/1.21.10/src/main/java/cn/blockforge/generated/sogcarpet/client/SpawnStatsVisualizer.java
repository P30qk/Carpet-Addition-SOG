package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.SogSpawnStats;
import cn.blockforge.generated.sogcarpet.SogSpawnTrace;
import cn.blockforge.generated.sogcarpet.net.SpawnStatsPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import cn.blockforge.generated.sogcarpet.client.gizmo.GizmoStyle;
import cn.blockforge.generated.sogcarpet.client.gizmo.Gizmos;
import cn.blockforge.generated.sogcarpet.client.gizmo.TextGizmo;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * “怪物生成可视化”规则（{@code spawnStatsVisualizer}）的客户端绘制。
 *
 * <p>本类画两样世界内的东西：</p>
 * <ul>
 *   <li><b>Y 轴生成高度柱</b>：对每次自然刷怪的起始点，从世界最低点到该列地表+1 画一根竖直柱，
 *       即原版 {@code getRandomPosWithin} 取起始 Y 的 {@code [minY, surface+1]} 可选范围；</li>
 *   <li><b>成功率标签</b>：最新出生点旁的整体成功率（红→绿）。</li>
 * </ul>
 *
 * <p>刷怪器的“随机游走路径”是独立规则 {@code randomWalkVisualizer} 的职责；按 MobCategory 与每个
 * 检查环节的成功率统计写在玩家的 Tab 列表页脚（见服务端 {@code SogSpawnStatsHud}）。</p>
 *
 * <p><b>/tick freeze、/tick step</b> 时画面停止推进。</p>
 */
public final class SpawnStatsVisualizer {
    private static final String RULE = "spawnStatsVisualizer";
    /** 统计快照多久没更新就当作过期（服务端每秒发一次，留足冗余）。 */
    private static final long TTL_MILLIS = 4000L;
    /** 出生点旁成功率标签的存活 tick 数（约 5 秒）。 */
    private static final int LABEL_LIFE_TICKS = 100;
    /** Y 轴生成高度柱的存活 tick 数（约 10 秒）。 */
    private static final int COLUMN_LIFE_TICKS = 200;
    /** 同屏最多多少根高度柱。 */
    private static final int MAX_COLUMNS = 64;
    private static final float HEIGHT_COLUMN_STROKE_WIDTH = 1.0F;

    private static volatile List<SogSpawnStats.CategoryStat> stats = Collections.emptyList();
    private static volatile long lastUpdateMillis;
    private static volatile Vec3 labelPos;
    private static volatile float labelRate = -1.0F;
    private static volatile int labelTicks;

    /** 正在显示的 Y 轴生成高度柱（只在客户端 tick 线程改动）。 */
    private static final List<HeightColumn> COLUMNS = new ArrayList<>();

    private SpawnStatsVisualizer() {
    }

    /** 客户端入口调用一次。 */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(SpawnStatsVisualizer::clientTick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(SpawnStatsVisualizer::renderGizmos);
    }

    /** 网络包接收器（{@code SpawnStatsPayload}）送来的统计快照。 */
    public static void acceptStats(SpawnStatsPayload payload) {
        if (payload == null) {
            return;
        }
        stats = payload.categories();
        lastUpdateMillis = System.currentTimeMillis();
    }

    /** 一条出生事件：记一根起始点处的 Y 轴生成高度柱，并在出生点旁记下当前整体成功率。 */
    public static void noteSpawn(SogSpawnTrace.SpawnEvent event) {
        if (event == null) {
            return;
        }
        if (event.initial() != null) {
            COLUMNS.add(new HeightColumn(event.initial(), event.spawnPos().getY()));
            while (COLUMNS.size() > MAX_COLUMNS) {
                COLUMNS.remove(0);
            }
        }
        float rate = totalRate();
        if (rate >= 0.0F) {
            labelPos = Vec3.atBottomCenterOf(event.spawnPos());
            labelRate = rate;
            labelTicks = LABEL_LIFE_TICKS;
        }
    }

    private static void clientTick(Minecraft client) {
        if (!VisualizerState.isActive(RULE)) {
            stats = Collections.emptyList();
            labelTicks = 0;
            labelPos = null;
            labelRate = -1.0F;
            COLUMNS.clear();
            return;
        }
        if (VisualizerState.tickFrozen(client)) {
            // /tick freeze、/tick step：画面停在那一刻。
            return;
        }
        if (labelTicks > 0) {
            labelTicks--;
        }
        Iterator<HeightColumn> iterator = COLUMNS.iterator();
        while (iterator.hasNext()) {
            HeightColumn column = iterator.next();
            if (++column.age > COLUMN_LIFE_TICKS) {
                iterator.remove();
            }
        }
    }

    /** 当前整体成功率；没有有效快照时返回 -1。 */
    private static float totalRate() {
        if (stats.isEmpty() || System.currentTimeMillis() - lastUpdateMillis > TTL_MILLIS) {
            return -1.0F;
        }
        long attempts = 0L;
        long successes = 0L;
        for (SogSpawnStats.CategoryStat stat : stats) {
            attempts += stat.attempts();
            successes += stat.successes();
        }
        return attempts <= 0L ? -1.0F : (float) successes / (float) attempts;
    }

    private static void renderGizmos(WorldRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        boolean drawColumn = level != null && VisualizerConfig.option(RULE, "heightColumn", true)
                && !COLUMNS.isEmpty();
        boolean drawLabel = labelTicks > 0 && labelPos != null && labelRate >= 0.0F;
        if (!drawColumn && !drawLabel) {
            return;
        }
        int columnFill = VisualizerConfig.color(RULE, "heightColumn", 0x5533D6FF);
        int columnStroke = VisualizerConfig.color(RULE, "heightColumnStroke", 0xCC33D6FF);
        try (Gizmos.TemporaryCollection collection =
                Gizmos.begin(context)) {
            if (drawColumn) {
                for (HeightColumn column : COLUMNS) {
                    drawHeightColumn(level, column, columnStroke, columnFill);
                }
            }
            if (drawLabel) {
                Gizmos.billboardText(label(labelRate), labelPos.add(0.0D, 1.2D, 0.0D),
                        TextGizmo.Style.forColor(rateColor(labelRate)).withScale(1.0F));
            }
        }
    }

    /**
     * 起始点处的一根 Y 轴生成高度柱：从世界最低点到该列地表+1，就是原版
     * {@code NaturalSpawner#getRandomPosWithin} 取刷怪起始 Y 的 [minY, surface+1] 可选范围。
     *
     * <p><b>底部 → 怪物生成高度再高一格</b>这一段保持原有颜色；<b>该高度往上</b>（没被用到的那段
     * 可选范围）变成白色，于是从柱子颜色就能看出生物实际生成在多高。彩色段特意画到生成高度上方的
     * 一整格，避免生物脚底那一格被算进白色段而看起来“高度少了一格”。</p>
     */
    private static void drawHeightColumn(ClientLevel level, HeightColumn column, int stroke, int fill) {
        BlockPos initial = column.pos;
        int minY = level.getMinY();
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, initial.getX(), initial.getZ()) + 1;
        int top = Math.max(minY + 1, surface);
        int spawnY = Math.max(minY, Math.min(top, column.spawnY));
        // 彩色段的上界：怪物生成高度再高一格（并夹在 [minY, top] 之内）。
        int coloredTop = Math.max(minY, Math.min(top, spawnY + 1));
        // 白色版：沿用配置颜色的透明度，只把 RGB 换成白色。
        int whiteFill = (fill & 0xFF000000) | 0x00FFFFFF;
        int whiteStroke = (stroke & 0xFF000000) | 0x00FFFFFF;
        if (coloredTop > minY) {
            AABB lower = new AABB(initial.getX(), minY, initial.getZ(),
                    initial.getX() + 1.0D, coloredTop, initial.getZ() + 1.0D);
            Gizmos.cuboid(lower, GizmoStyle.strokeAndFill(stroke, HEIGHT_COLUMN_STROKE_WIDTH, fill));
        }
        if (top > coloredTop) {
            AABB upper = new AABB(initial.getX(), coloredTop, initial.getZ(),
                    initial.getX() + 1.0D, top, initial.getZ() + 1.0D);
            Gizmos.cuboid(upper, GizmoStyle.strokeAndFill(whiteStroke, HEIGHT_COLUMN_STROKE_WIDTH, whiteFill));
        }
    }

    private static String label(float rate) {
        return String.format(java.util.Locale.ROOT, "P=%.0f%%", rate * 100.0F);
    }

    /** 成功率 → 颜色：低红、中黄、高绿；未知为灰。 */
    private static int rateColor(float rate) {
        if (rate < 0.0F) {
            return 0xFF9AA4AE;
        }
        float clamped = Math.max(0.0F, Math.min(1.0F, rate));
        int red = (int) (255.0F * (1.0F - clamped));
        int green = (int) (255.0F * clamped);
        return 0xFF000000 | (red << 16) | (green << 8) | 60;
    }

    /** 一根正在显示的 Y 轴生成高度柱：起始方块 + 生物实际生成高度。 */
    private static final class HeightColumn {
        private final BlockPos pos;
        private final int spawnY;
        private int age;

        private HeightColumn(BlockPos pos, int spawnY) {
            this.pos = pos;
            this.spawnY = spawnY;
        }
    }
}
