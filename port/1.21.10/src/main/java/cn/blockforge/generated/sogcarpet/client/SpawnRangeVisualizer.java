package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.BaseSpawnerAccess;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import cn.blockforge.generated.sogcarpet.client.gizmo.GizmoStyle;
import cn.blockforge.generated.sogcarpet.client.gizmo.Gizmos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CreakingHeartBlockEntity;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * “可刷怪方块生成范围可视化”规则的客户端实现。
 *
 * <p>把<b>会自己刷怪的方块</b>（可刷怪方块）的生成范围画出来：</p>
 * <ul>
 *   <li><b>刷怪笼</b>（{@link SpawnerBlockEntity}）：原版 {@code BaseSpawner} 每
 *       随机偏移一次，位置取 {@code (nextDouble-nextDouble) * spawnRange + 0.5}，y 取
 *       {@code nextInt(3) - 1}，也就是水平 ±spawnRange、竖直 ±1 格；spawnRange 直接读
 *       方块实体同步下来的 NBT 真实值 {@code SpawnRange}（默认 4，经
 *       {@link BaseSpawnerAccess} 取用）；</li>
 *   <li><b>试炼刷怪笼</b>（{@link TrialSpawnerBlockEntity}）：同样的偏移公式（水平
 *       ±spawnRange、竖直 ±1 格），spawnRange 取它配置里的 spawn_range。注意自定义配置
 *       不会同步到客户端、客户端读到的是默认配置（原版所有数据——含试炼密室全部配置
 *       文件——都不覆盖 spawn_range，等于默认值 4，所以实际显示一致）；</li>
 *   <li><b>幽匿尖啸体</b>（{@link SculkShriekerBlockEntity}）：召唤坚守者时
 *       {@code SpawnUtil.trySpawnMob} 在水平 ±5、竖直 ±6 格内找位置；</li>
 *   <li><b>嘎吱之心</b>（{@link CreakingHeartBlockEntity}）：生成嘎吱怪时
 *       {@code SpawnUtil.trySpawnMob} 在水平 ±16、竖直 ±8 格内找位置。</li>
 * </ul>
 *
 * <p>每种范围用一个<b>半透明红色长方体</b>罩住，中心的刷怪方块本身再用一个<b>黄色线框</b>
 * 框住。数据全在客户端读方块实体得到，不需要服务端下发。
 * 扫描照抄 {@link SculkVisualizer} 的做法：只遍历玩家周围若干区块的方块实体表，每
 * {@value #SCAN_INTERVAL_TICKS} tick 重建一次快照，渲染线程只读快照。</p>
 */
public final class SpawnRangeVisualizer {
    private static final String RULE = "spawnRangeVisualizer";

    /** 以玩家为中心扫描的区块半径。 */
    private static final int SCAN_RADIUS_CHUNKS = 4;
    /** 扫描节流：每多少 tick 重建一次快照。 */
    private static final int SCAN_INTERVAL_TICKS = 20;
    /** 方块离玩家超过该距离就忽略。 */
    private static final double MAX_DISTANCE_SQR = 64.0D * 64.0D;
    /** 同屏最多画多少个范围。 */
    private static final int MAX_BOXES = 64;

    /** 范围长方体：半透明红色填充 + 红色描边。 */
    private static final float RANGE_STROKE_WIDTH = 1.5F;
    /** 中心刷怪方块的黄色线框宽度。 */
    private static final float CENTER_STROKE_WIDTH = 2.5F;
    /** 中心线框往外膨胀一点，避免和方块面重合闪烁。 */
    private static final float CENTER_INFLATE = 0.004F;

    private static final int DEFAULT_FILL = 0x40FF3B30;
    private static final int DEFAULT_STROKE = 0xE6FF3B30;
    private static final int DEFAULT_CENTER = 0xFFFFE14D;

    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    private static int tickCounter;

    private SpawnRangeVisualizer() {
    }

    /** 客户端入口调用一次：注册 tick 扫描与渲染回调。 */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(SpawnRangeVisualizer::clientTick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(SpawnRangeVisualizer::renderGizmos);
    }

    private static void clientTick(Minecraft client) {
        if (!VisualizerState.isActive(RULE)) {
            snapshot = Snapshot.EMPTY;
            return;
        }
        ClientLevel level = client.level;
        if (client.player == null || level == null) {
            snapshot = Snapshot.EMPTY;
            return;
        }
        if (VisualizerState.tickFrozen(client) || ++tickCounter < SCAN_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        snapshot = scan(level, client.player.blockPosition());
    }

    private static Snapshot scan(ClientLevel level, BlockPos center) {
        List<Box> boxes = new ArrayList<>();
        int chunkX = center.getX() >> 4;
        int chunkZ = center.getZ() >> 4;
        for (int dx = -SCAN_RADIUS_CHUNKS; dx <= SCAN_RADIUS_CHUNKS; dx++) {
            for (int dz = -SCAN_RADIUS_CHUNKS; dz <= SCAN_RADIUS_CHUNKS; dz++) {
                LevelChunk chunk = level.getChunk(chunkX + dx, chunkZ + dz);
                if (chunk == null || chunk.isEmpty()) {
                    continue;
                }
                for (Object value : chunk.getBlockEntities().values()) {
                    if (!(value instanceof BlockEntity)) {
                        continue;
                    }
                    BlockEntity blockEntity = (BlockEntity) value;
                    BlockPos pos = blockEntity.getBlockPos();
                    if (center.distSqr(pos) > MAX_DISTANCE_SQR) {
                        continue;
                    }
                    int kind;
                    int horizontal;
                    int vertical;
                    if (blockEntity instanceof SpawnerBlockEntity spawnerEntity) {
                        kind = Box.KIND_SPAWNER;
                        horizontal = Math.max(0, spawnerRange(spawnerEntity));
                        vertical = 1;
                    } else if (blockEntity instanceof TrialSpawnerBlockEntity trial) {
                        kind = Box.KIND_TRIAL_SPAWNER;
                        horizontal = Math.max(0, trialSpawnerRange(trial));
                        vertical = 1;
                    } else if (blockEntity instanceof SculkShriekerBlockEntity) {
                        kind = Box.KIND_SHRIEKER;
                        horizontal = 5;
                        vertical = 6;
                    } else if (blockEntity instanceof CreakingHeartBlockEntity) {
                        kind = Box.KIND_CREAKING_HEART;
                        horizontal = 16;
                        vertical = 8;
                    } else {
                        continue;
                    }
                    boxes.add(box(pos.immutable(), kind, horizontal, vertical));
                    if (boxes.size() >= MAX_BOXES) {
                        return new Snapshot(boxes);
                    }
                }
            }
        }
        return new Snapshot(boxes);
    }

    /** 刷怪笼的水平刷怪范围：读方块实体同步下来的真实 SpawnRange（NBT，默认 4）；读不到时退回 4。 */
    private static int spawnerRange(SpawnerBlockEntity entity) {
        try {
            return ((BaseSpawnerAccess) entity.getSpawner()).sog$getSpawnRange();
        } catch (RuntimeException ignored) {
            return 4;
        }
    }

    /**
     * 试炼刷怪笼的水平刷怪范围：取它配置里的 spawn_range。注意自定义配置不会同步到客户端
     * （方块实体的更新标签只带状态数据），客户端读到的始终是默认配置；原版所有数据都不覆盖
     * spawn_range、等于默认 4，所以实际显示正确。取不到时退回默认 4。
     */
    private static int trialSpawnerRange(TrialSpawnerBlockEntity entity) {
        try {
            return entity.getTrialSpawner().activeConfig().spawnRange();
        } catch (RuntimeException ignored) {
            return 4;
        }
    }

    /** 一个刷怪范围方块：范围长方体（水平 ±horizontal、竖直 ±vertical）+ 中心方块。 */
    private static Box box(BlockPos pos, int kind, int horizontal, int vertical) {
        AABB range = new AABB(
                pos.getX() - horizontal, pos.getY() - vertical, pos.getZ() - horizontal,
                pos.getX() + 1.0D + horizontal, pos.getY() + 1.0D + vertical,
                pos.getZ() + 1.0D + horizontal);
        return new Box(range, pos, kind);
    }

    private static void renderGizmos(WorldRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        Snapshot current = snapshot;
        if (current.boxes.isEmpty()) {
            return;
        }
        int fill = VisualizerConfig.color(RULE, "rangeFill", DEFAULT_FILL);
        int stroke = VisualizerConfig.color(RULE, "rangeStroke", DEFAULT_STROKE);
        int centerColor = VisualizerConfig.color(RULE, "centerBox", DEFAULT_CENTER);
        boolean showSpawner = VisualizerConfig.option(RULE, "showSpawner", true);
        boolean showTrialSpawner = VisualizerConfig.option(RULE, "showTrialSpawner", true);
        boolean showShrieker = VisualizerConfig.option(RULE, "showShrieker", true);
        boolean showCreakingHeart = VisualizerConfig.option(RULE, "showCreakingHeart", true);
        try (Gizmos.TemporaryCollection collection = Gizmos.begin(context)) {
            GizmoStyle rangeStyle = GizmoStyle.strokeAndFill(stroke, RANGE_STROKE_WIDTH, fill);
            GizmoStyle centerStyle = GizmoStyle.stroke(centerColor, CENTER_STROKE_WIDTH);
            for (int i = 0; i < current.boxes.size(); i++) {
                Box box = current.boxes.get(i);
                if (!enabled(box, showSpawner, showTrialSpawner, showShrieker, showCreakingHeart)) {
                    continue;
                }
                Gizmos.cuboid(box.range, rangeStyle);
                Gizmos.cuboid(box.center, CENTER_INFLATE, centerStyle);
            }
        }
    }

    /** 这个方块是不是关了对应的类型开关；快照没记录类型时用范围尺寸反推。 */
    private static boolean enabled(Box box, boolean showSpawner, boolean showTrialSpawner,
            boolean showShrieker, boolean showCreakingHeart) {
        switch (box.kind) {
            case Box.KIND_SPAWNER:
                return showSpawner;
            case Box.KIND_TRIAL_SPAWNER:
                return showTrialSpawner;
            case Box.KIND_SHRIEKER:
                return showShrieker;
            case Box.KIND_CREAKING_HEART:
                return showCreakingHeart;
            default:
                return true;
        }
    }

    /** 一个待渲染的刷怪范围：范围长方体 + 中心方块 + 方块类型。 */
    private static final class Box {
        private static final int KIND_SPAWNER = 0;
        private static final int KIND_TRIAL_SPAWNER = 1;
        private static final int KIND_SHRIEKER = 2;
        private static final int KIND_CREAKING_HEART = 3;

        private final AABB range;
        private final BlockPos center;
        private final int kind;

        private Box(AABB range, BlockPos center, int kind) {
            this.range = range;
            this.center = center;
            this.kind = kind;
        }
    }

    /** 只读渲染快照，tick 线程整体替换、渲染线程整体读取。 */
    private static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(Collections.<Box>emptyList());

        private final List<Box> boxes;

        private Snapshot(List<Box> boxes) {
            this.boxes = boxes;
        }
    }
}
