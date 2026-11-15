package cn.blockforge.generated.sogcarpet.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import cn.blockforge.generated.sogcarpet.client.gizmo.GizmoStyle;
import cn.blockforge.generated.sogcarpet.client.gizmo.Gizmos;
import cn.blockforge.generated.sogcarpet.client.gizmo.TextGizmo;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * “爆炸可视化”规则的客户端实现（移植自 lucidity 的 explosion visualizer）。
 *
 * <p>只要玩家附近存在爆炸源，就按原版爆炸射线模型把这次爆炸整体预览出来。爆炸源与 lucidity
 * 一致：已点燃的 {@link PrimedTnt}、正在膨胀的 {@link Creeper}、水晶 {@link EndCrystal}、
 * 已点火的 {@link MinecartTNT}、飞行的 {@link WitherSkull}，以及处于无敌阶段的 {@link WitherBoss}。</p>
 *
 * <p>各部分都可以在 {@link ExplosionVisualizerConfigScreen}（Ctrl+V → 本规则的“配置”）里
 * 单独开关，选项与 lucidity 的 ExplosionVisualizerConfigs 对齐，并额外提供“渲染圆球”开关：</p>
 * <ul>
 *   <li><b>方块破坏</b>：会被这次爆炸破坏的方块，橙色半透明，越靠中心越实；</li>
 *   <li><b>方块射线投射</b>：爆炸射线上每一步的采样点（爆心色反色，蓝）；</li>
 *   <li><b>爆炸中心</b>：黄绿色光点；</li>
 *   <li><b>实体采样点</b>：被波及实体身上的伤害采样点，被挡住的是安全点（绿），直通的是暴露点（红）；</li>
 *   <li><b>采样点射线投射</b>：从采样点到命中点/爆心的连线；</li>
 *   <li><b>爆炸计时器</b>：在爆炸源头顶显示引信倒计时（秒）；</li>
 *   <li><b>渲染圆球</b>：最外围的浅绿色半透明范围球（本模组新增）。</li>
 * </ul>
 *
 * <p>爆炸是短时事件，所以每 {@value #SCAN_INTERVAL_TICKS} tick 重算一次快照，渲染只读快照；
 * 只预览玩家 {@value #SEARCH_RANGE} 格以内的爆炸，避免 TNT 阵列拖垮客户端。</p>
 */
public final class ExplosionVisualizer {
    /** 本规则在总控里的名字。 */
    private static final String RULE = "explosionVisualizer";

    /** 只预览玩家周围这个距离（格）以内的爆炸。 */
    private static final double SEARCH_RANGE = 16.0D;
    /** 扫描间隔（每 tick 一次，爆炸源自己也在动）。 */
    private static final int SCAN_INTERVAL_TICKS = 1;
    /** 同屏最多同时预览多少个实体爆炸源。 */
    private static final int MAX_SOURCES = 8;
    /** 单个爆炸最多标出多少块会被破坏的方块。 */
    private static final int MAX_BLOCKS = 2000;
    /** 单个爆炸最多画多少个射线采样点（所有射线共享预算）。 */
    private static final int MAX_RAY_POINTS = 20000;
    /** 单个爆炸最多处理多少个被波及的实体。 */
    private static final int MAX_ENTITIES = 12;
    /** 单个实体最多取多少个伤害采样点。 */
    private static final int MAX_SAMPLES_PER_ENTITY = 256;
    /** 方块爆炸源最多同时预览多少个。 */
    private static final int MAX_BLOCK_SOURCES = 8;

    /** TNT 爆炸威力（原版默认值）与爆心抬高量。 */
    private static final float TNT_POWER = 4.0F;
    private static final double TNT_CENTER_Y_OFFSET = 0.0625D;
    /** 苦力怕爆炸威力、引信长度（tick）。 */
    private static final float CREEPER_POWER = 3.0F;
    private static final float CHARGED_CREEPER_POWER = 6.0F;
    private static final int CREEPER_FUSE_TICKS = 30;
    /** 其他爆炸源威力。 */
    private static final float CRYSTAL_POWER = 6.0F;
    private static final float SKULL_POWER = 1.0F;
    private static final float WITHER_POWER = 7.0F;
    private static final float FIREBALL_POWER = 1.0F;
    private static final float WIND_CHARGE_POWER = 1.2F;
    private static final float BREEZE_WIND_CHARGE_POWER = 3.0F;
    private static final float ANCHOR_POWER = 5.0F;
    private static final float BED_POWER = 5.0F;
    /** 方块爆炸源（重生锚、床）的扫描半径（格）与扫描间隔（tick）。 */
    private static final int BLOCK_SCAN_RADIUS = 10;
    private static final int BLOCK_SCAN_INTERVAL_TICKS = 2;

    /** 爆炸射线每步前进的距离、每步衰减、初始能量系数。 */
    private static final float RAY_STEP = 0.3F;
    private static final float RAY_DECAY = 0.225F;
    private static final float POWER_MIN_FACTOR = 0.7F;
    private static final float POWER_MAX_FACTOR = 1.3F;
    private static final float EDGE_FACTOR = POWER_MAX_FACTOR / RAY_DECAY * RAY_STEP;
    private static final int RAY_GRID = 16;

    /** lucidity 的 explosion_destruction_color：橙，基础透明度 70/255。 */
    private static int DESTRUCTION_RED = 255;
    private static int DESTRUCTION_GREEN = 122;
    private static int DESTRUCTION_BLUE = 30;
    private static final float DESTRUCTION_ALPHA = 130.0F / 255.0F;
    private static final float DESTRUCTION_MIN_ALPHA_FACTOR = 0.3F;
    private static final double BLOCK_INFLATE = 0.002D;
    private static final int MAX_OUTLINED_BLOCKS = 600;
    private static final float BLOCK_STROKE_WIDTH = 1.5F;
    private static final float BLOCK_STROKE_ALPHA = 200.0F / 255.0F;
    /** lucidity 的 explosion_center_color：(220,255,0)。 */
    private static int COLOR_CENTER = 0xFFDCFF00;
    /** 射线采样点用爆心色的反色：(35,0,255) 的蓝。 */
    private static int RAY_RED = 35;
    private static int RAY_GREEN = 0;
    private static int RAY_BLUE = 255;
    /** lucidity 的 sample_point_safe_color / exposed_color。 */
    private static int COLOR_SAFE = 0xA500FF1E;
    private static int COLOR_EXPOSED = 0xF0FF0000;
    /** 与玩家相连的判定线（爆炸暴露判定）统一红色。 */
    private static final int COLOR_PLAYER_LINE = 0xFFFF3B30;
    private static final int COLOR_SAFE_RETURN = 0xA5FF00E1;
    /** 采样点射线线宽。 */
    private static final float SAMPLE_RAY_WIDTH = 1.5F;

    /** 爆炸源本体配色。 */
    private static final int KIND_TNT = 0;
    private static final int KIND_CREEPER = 1;
    private static final int KIND_CRYSTAL = 2;
    private static final int KIND_MINECART = 3;
    private static final int KIND_SKULL = 4;
    private static final int KIND_WITHER = 5;
    private static final int KIND_FIREBALL = 6;
    private static final int KIND_WIND = 7;
    private static final int KIND_ANCHOR = 8;
    private static final int KIND_BED = 9;
    private static final int[] SOURCE_STROKE = {
            0xFF7E96E8, 0xFF8CE08C, 0xFFE8D9FF, 0xFFE8D9FF, 0xFFE8D9FF, 0xFFE8D9FF,
            0xFFE8D9FF, 0xFFE8D9FF, 0xFFE8D9FF, 0xFFE8D9FF};
    private static final int[] SOURCE_FILL = {
            0x883A4C9E, 0x884FA64F, 0x88C8A2FF, 0x88C8A2FF, 0x88C8A2FF, 0x88C8A2FF,
            0x88C8A2FF, 0x88C8A2FF, 0x88C8A2FF, 0x88C8A2FF};

    /** 光点尺寸，单位是屏幕像素。 */
    private static final float RAY_POINT_SIZE = 2.5F;
    private static final float CENTER_POINT_SIZE = 9.0F;
    private static final float SAMPLE_POINT_SIZE = 4.0F;
    private static final float RAY_ALPHA_GAIN = 2.0F;

    private static final double ENTITY_INFLATE = 0.02D;
    private static final float SOURCE_STROKE_WIDTH = 2.0F;

    /** 淡绿色半透明球：浅绿 + 约 40% 不透明度。 */
    private static int COLOR_SPHERE = 0x66B4F0B4;

    /** 计时器文字颜色：快炸了红、还早黄。 */
    private static final int TIMER_COLOR_SOON = 0xFFFF3B30;
    private static final int TIMER_COLOR_LATER = 0xFFFFD21E;
    /** 计时器离爆炸源包围盒顶面的高度。 */
    private static final double TIMER_Y_OFFSET = 0.6D;

    // —— 单位球壳几何（半径 1），渲染时按各自半径缩放 ——
    private static final int SPHERE_LONGITUDES = 20;
    private static final int SPHERE_LATITUDES = 10;
    private static final Vec3[][] SPHERE_RINGS = new Vec3[SPHERE_LATITUDES + 1][SPHERE_LONGITUDES];
    private static final Vec3[][] SPHERE_QUAD_CENTERS = new Vec3[SPHERE_LATITUDES][SPHERE_LONGITUDES];
    private static final Vec3[][] SPHERE_QUAD_NORMALS = new Vec3[SPHERE_LATITUDES][SPHERE_LONGITUDES];

    /** 原版爆炸用的 1352 条方向（16³ 立方体表面）。 */
    private static final Vec3[] RAYS = buildRays();

    static {
        for (int i = 0; i <= SPHERE_LATITUDES; i++) {
            double latitude = -Math.PI / 2.0D + Math.PI * i / SPHERE_LATITUDES;
            double ringRadius = Math.cos(latitude);
            double ringY = Math.sin(latitude);
            for (int j = 0; j < SPHERE_LONGITUDES; j++) {
                double longitude = 2.0D * Math.PI * j / SPHERE_LONGITUDES;
                SPHERE_RINGS[i][j] = new Vec3(ringRadius * Math.cos(longitude), ringY,
                        ringRadius * Math.sin(longitude));
            }
        }
        for (int band = 0; band < SPHERE_LATITUDES; band++) {
            for (int j = 0; j < SPHERE_LONGITUDES; j++) {
                int next = (j + 1) % SPHERE_LONGITUDES;
                Vec3 center = SPHERE_RINGS[band][j].add(SPHERE_RINGS[band + 1][j])
                        .add(SPHERE_RINGS[band + 1][next]).add(SPHERE_RINGS[band][next]).scale(0.25D);
                SPHERE_QUAD_CENTERS[band][j] = center;
                SPHERE_QUAD_NORMALS[band][j] = center.normalize();
            }
        }
    }

    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    private static int tickCounter;
    private static int blockTickCounter;
    private static List<Source> blockSources = Collections.emptyList();

    private ExplosionVisualizer() {
    }

    /** 客户端入口调用一次：tick 扫描与渲染回调（Ctrl+O 总开关在 {@link VisualizerState}）。 */
    public static void init() {
        ExplosionVisualizerConfig.ensure();
        ClientTickEvents.END_CLIENT_TICK.register(ExplosionVisualizer::clientTick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ExplosionVisualizer::renderGizmos);
    }

    private static void clientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (!VisualizerState.isActive(RULE) || level == null || client.player == null) {
            snapshot = Snapshot.EMPTY;
            blockSources = Collections.emptyList();
            return;
        }
        if (++tickCounter < SCAN_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        Vec3 playerPos = client.player.position();
        if (++blockTickCounter >= BLOCK_SCAN_INTERVAL_TICKS) {
            blockTickCounter = 0;
            blockSources = scanBlocks(level, playerPos);
        }
        snapshot = scan(level, playerPos);
    }

    private static Snapshot scan(ClientLevel level, Vec3 playerPos) {
        List<Source> sources = new ArrayList<>();
        double searchSqr = SEARCH_RANGE * SEARCH_RANGE;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.position().distanceToSqr(playerPos) > searchSqr) {
                continue;
            }
            Source source = sourceOf(entity);
            if (source != null) {
                sources.add(source);
            }
            if (sources.size() >= MAX_SOURCES) {
                break;
            }
        }
        // 方块爆炸源（重生锚、床）按较低频率扫描，这里把上次的结果并进来。
        for (Source source : blockSources) {
            if (sources.size() >= MAX_SOURCES + MAX_BLOCK_SOURCES) {
                break;
            }
            sources.add(source);
        }
        if (sources.isEmpty()) {
            return Snapshot.EMPTY;
        }

        List<RenderSource> render = new ArrayList<>();
        for (Source source : sources) {
            render.add(simulate(level, source));
        }
        return new Snapshot(render);
    }

    /**
     * 扫描玩家附近的方块爆炸源（移植自 lucidity 的 {@code findExplosivesInRange}）：
     * 充能过的<b>重生锚</b>在“重生锚不起作用”的维度会爆炸（半径 5）；<b>床</b>在床规则
     * 判定为会爆炸的维度会爆炸（半径 5）。两者都取方块中心为爆心，威力固定 5。
     */
    private static List<Source> scanBlocks(ClientLevel level, Vec3 playerPos) {
        List<Source> out = new ArrayList<>();
        BlockPos center = BlockPos.containing(playerPos);
        int r = BLOCK_SCAN_RADIUS;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (out.size() >= MAX_BLOCK_SOURCES) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof RespawnAnchorBlock) {
                int charges = state.getValue(RespawnAnchorBlock.CHARGE);
                if (charges > 0 && !anchorWorks(level, pos)) {
                    out.add(blockSource(pos.immutable(), ANCHOR_POWER, KIND_ANCHOR));
                }
            } else if (state.getBlock() instanceof BedBlock
                    && state.getValue(BedBlock.PART) == BedPart.HEAD) {
                if (bedExplodes(level, pos)) {
                    out.add(blockSource(pos.immutable(), BED_POWER, KIND_BED));
                }
            }
        }
        return out;
    }

    /**
     * 该位置的重生锚会不会起作用。原版用 {@code EnvironmentAttributes.RESPAWN_ANCHOR_WORKS} 判定；
     * 客户端读环境属性偶尔可能拿不到采样器，所以这里用 try/catch 兜底：拿不到就按“不起作用”处理，
     * 让预览照常显示，不会因为一次读取失败整条方块预览消失。
     */
    private static boolean anchorWorks(ClientLevel level, BlockPos pos) {
        try {
            return level.dimensionType().respawnAnchorWorks();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** 该位置的床会不会爆炸：1.21.10 仍由维度类型决定（床在下界/末地不可用即爆炸）。 */
    private static boolean bedExplodes(ClientLevel level, BlockPos pos) {
        try {
            return !level.dimensionType().bedWorks();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Source blockSource(BlockPos pos, float power, int kind) {
        return new Source(null, Vec3.atCenterOf(pos), power, kind, new AABB(pos), -1, true, pos);
    }

    /** 把附近的爆炸源实体映射成一个待模拟的 {@link Source}；不是爆炸源时返回 null。 */
    private static Source sourceOf(Entity entity) {
        if (entity instanceof PrimedTnt tnt) {
            return new Source(entity, new Vec3(tnt.getX(), tnt.getY() + TNT_CENTER_Y_OFFSET, tnt.getZ()),
                    TNT_POWER, KIND_TNT, entity.getBoundingBox(), tnt.getFuse(), true);
        }
        if (entity instanceof Creeper creeper && creeper.getSwellDir() > 0) {
            float power = creeper.isPowered() ? CHARGED_CREEPER_POWER : CREEPER_POWER;
            int fuse = (int) Math.ceil((1.0F - creeper.getSwelling(0.0F)) * CREEPER_FUSE_TICKS);
            return new Source(entity, creeper.position(), power, KIND_CREEPER,
                    entity.getBoundingBox(), fuse, true);
        }
        if (entity instanceof EndCrystal) {
            return new Source(entity, entity.position(), CRYSTAL_POWER, KIND_CRYSTAL,
                    entity.getBoundingBox(), -1, true);
        }
        if (entity instanceof MinecartTNT cart && cart.isPrimed()) {
            // 原版威力 4.0 + 随机(0~1.5)*速度（速度封顶 5）；这里取上界做确定性预览。
            double speed = Math.min(Math.sqrt(cart.getDeltaMovement().horizontalDistanceSqr()), 5.0D);
            float power = (float) (4.0D + speed * 1.5D);
            return new Source(entity, cart.position(), power, KIND_MINECART,
                    entity.getBoundingBox(), cart.getFuse(), true);
        }
        if (entity instanceof WitherSkull) {
            return new Source(entity, entity.position(), SKULL_POWER, KIND_SKULL,
                    entity.getBoundingBox(), -1, true);
        }
        if (entity instanceof WitherBoss wither && wither.getInvulnerableTicks() > 0) {
            return new Source(entity, new Vec3(wither.getX(), wither.getEyeY(), wither.getZ()),
                    WITHER_POWER, KIND_WITHER, entity.getBoundingBox(), -1, true);
        }
        if (entity instanceof LargeFireball) {
            // 恶魂火球：原版威力由构造参数决定，默认 1；客户端读不到该字段，按默认值预览。
            return new Source(entity, entity.position(), FIREBALL_POWER, KIND_FIREBALL,
                    entity.getBoundingBox(), -1, true);
        }
        if (entity instanceof BreezeWindCharge) {
            return new Source(entity, entity.position(), BREEZE_WIND_CHARGE_POWER, KIND_WIND,
                    entity.getBoundingBox(), -1, false);
        }
        if (entity instanceof WindCharge) {
            return new Source(entity, entity.position(), WIND_CHARGE_POWER, KIND_WIND,
                    entity.getBoundingBox(), -1, false);
        }
        return null;
    }

    /** 对单个爆炸源跑一遍原版射线模型，收集破坏方块、射线采样点与实体伤害采样点。 */
    private static RenderSource simulate(ClientLevel level, Source source) {
        float radius = source.power;
        float maxPower = radius * POWER_MAX_FACTOR;
        float minPower = radius * POWER_MIN_FACTOR;

        Map<Long, Float> affects = new HashMap<>();
        Map<Long, BlockPos> affectedPositions = new HashMap<>();
        List<RayPoint> rayPoints = new ArrayList<>();
        // 风弹只触发方块、不破坏方块；这类爆炸源跳过整条射线推演，只画范围球与爆炸源本体。
        if (source.destructs) {
            int stepsPerRay = (int) Math.ceil(maxPower / RAY_DECAY);
            int pointStride = Math.max(1,
                    (int) Math.ceil((double) RAYS.length * stepsPerRay / MAX_RAY_POINTS));
            for (int i = 0; i < RAYS.length; i++) {
                Vec3 direction = RAYS[i];
                double x = source.center.x;
                double y = source.center.y;
                double z = source.center.z;
                int step = 0;
                for (float power = maxPower; power > 0.0F; power -= RAY_DECAY, step++) {
                    BlockPos pos = BlockPos.containing(x, y, z);
                    if (level.isOutsideBuildHeight(pos)) {
                        break;
                    }
                    // 方块爆炸源（重生锚、床）的爆心就在源方块里：把它当成空气，否则射线一出发
                    // 就被源方块的高爆炸抗性挡死，蓝色采样点与破坏方块都不会出现。
                    boolean isSourceBlock = source.sourceBlock != null && source.sourceBlock.equals(pos);
                    BlockState state = isSourceBlock ? Blocks.AIR.defaultBlockState()
                            : level.getBlockState(pos);
                    FluidState fluid = state.getFluidState();
                    if (!state.isAir() || !fluid.isEmpty()) {
                        float resistance = Math.max(state.getBlock().getExplosionResistance(),
                                fluid.getExplosionResistance());
                        power -= (resistance + 0.3F) * 0.3F;
                    }
                    if (power <= 0.0F) {
                        break;
                    }
                    if (step % pointStride == 0) {
                        rayPoints.add(new RayPoint(new Vec3(x, y, z), power));
                    }
                    if (!state.isAir()) {
                        float spent = maxPower - power;
                        float probability = spent <= minPower ? 1.0F
                                : spent >= maxPower ? 0.0F
                                        : (maxPower - spent) / (maxPower - minPower);
                        long key = pos.asLong();
                        Float previous = affects.get(key);
                        if (previous == null || probability > previous) {
                            affects.put(key, probability);
                            affectedPositions.put(key, pos.immutable());
                        }
                    }
                    x += direction.x * RAY_STEP;
                    y += direction.y * RAY_STEP;
                    z += direction.z * RAY_STEP;
                }
            }
        }

        List<BlockProbability> blocks = new ArrayList<>();
        for (Map.Entry<Long, Float> entry : affects.entrySet()) {
            if (blocks.size() >= MAX_BLOCKS) {
                break;
            }
            blocks.add(new BlockProbability(affectedPositions.get(entry.getKey()), entry.getValue()));
        }

        List<EntitySample> samples = collectEntitySamples(level, source, radius);

        return new RenderSource(source.center, radius, source.kind, source.entityBox, maxPower,
                source.fuseTicks, blocks, rayPoints, samples);
    }

    /**
     * 照抄原版 {@code Explosion.getSeenPercent}：在实体包围盒里均匀取样，从每个采样点向爆心
     * 连线，被方块挡住的是安全点（绿），直通的会吃到伤害（红）。
     */
    private static List<EntitySample> collectEntitySamples(ClientLevel level, Source source, float radius) {
        List<EntitySample> samples = new ArrayList<>();
        double reach = radius * 2.0D;
        double reachSqr = reach * reach;
        Entity localPlayer = Minecraft.getInstance().player;
        int entities = 0;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == source.entity || entity.position().distanceToSqr(source.center) > reachSqr) {
                continue;
            }
            if (++entities > MAX_ENTITIES) {
                break;
            }
            sampleEntity(level, entity, source.center, source.sourceBlock, entity == localPlayer, samples);
        }
        return samples;
    }

    private static void sampleEntity(ClientLevel level, Entity entity, Vec3 center, BlockPos ignore,
            boolean player, List<EntitySample> out) {
        AABB box = entity.getBoundingBox();
        double stepX = 1.0D / ((box.maxX - box.minX) * 2.0D + 1.0D);
        double stepY = 1.0D / ((box.maxY - box.minY) * 2.0D + 1.0D);
        double stepZ = 1.0D / ((box.maxZ - box.minZ) * 2.0D + 1.0D);
        if (stepX <= 0.0D || stepY <= 0.0D || stepZ <= 0.0D) {
            return;
        }
        double offsetX = (1.0D - Math.floor(1.0D / stepX) * stepX) / 2.0D;
        double offsetZ = (1.0D - Math.floor(1.0D / stepZ) * stepZ) / 2.0D;
        for (double a = 0.0D; a <= 1.0D; a += stepX) {
            for (double b = 0.0D; b <= 1.0D; b += stepY) {
                for (double c = 0.0D; c <= 1.0D; c += stepZ) {
                    if (out.size() >= MAX_ENTITIES * MAX_SAMPLES_PER_ENTITY) {
                        return;
                    }
                    Vec3 start = new Vec3(Mth.lerp(a, box.minX, box.maxX) + offsetX,
                            Mth.lerp(b, box.minY, box.maxY),
                            Mth.lerp(c, box.minZ, box.maxZ) + offsetZ);
                    Vec3 end = center;
                    boolean blocked = false;
                    BlockHitResult hit = level.clip(new ClipContext(start, center,
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
                    if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(ignore)) {
                        blocked = true;
                        end = hit.getLocation();
                    }
                    out.add(new EntitySample(start, end, blocked, player));
                }
            }
        }
    }

    /** 原版爆炸方向表：16³ 立方体表面上归一化后的方向，共 1352 条。 */
    private static Vec3[] buildRays() {
        List<Vec3> directions = new ArrayList<>(1352);
        for (int i = 0; i < RAY_GRID; i++) {
            for (int j = 0; j < RAY_GRID; j++) {
                for (int k = 0; k < RAY_GRID; k++) {
                    if (i != 0 && i != RAY_GRID - 1 && j != 0 && j != RAY_GRID - 1
                            && k != 0 && k != RAY_GRID - 1) {
                        continue;
                    }
                    double x = (double) i / (RAY_GRID - 1) * 2.0D - 1.0D;
                    double y = (double) j / (RAY_GRID - 1) * 2.0D - 1.0D;
                    double z = (double) k / (RAY_GRID - 1) * 2.0D - 1.0D;
                    double length = Math.sqrt(x * x + y * y + z * z);
                    if (length > 1.0E-6D) {
                        directions.add(new Vec3(x / length, y / length, z / length));
                    }
                }
            }
        }
        return directions.toArray(new Vec3[0]);
    }

    private static void renderGizmos(WorldRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        refreshColors();
        Snapshot current = snapshot;
        if (current.sources.isEmpty()) {
            return;
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        try (Gizmos.TemporaryCollection collection = Gizmos.begin(context)) {
            for (int i = 0; i < current.sources.size(); i++) {
                renderSource(current.sources.get(i), camera);
            }
        }
    }

    private static void renderSource(RenderSource source, Vec3 camera) {
        // 淡绿色半透明球先画：它是最远的一层，垫在最后面；橙色方块、光点随后叠上去。
        if (ExplosionVisualizerConfig.sphere()) {
            drawSphere(source.center, source.radius * EDGE_FACTOR, COLOR_SPHERE, camera);
        }

        // 会被炸掉的方块：lucidity 橙色，透明度按概率渐变。
        if (ExplosionVisualizerConfig.blockDestruction()) {
            boolean outline = source.blocks.size() <= MAX_OUTLINED_BLOCKS;
            int stroke = argb(255, 150, 60, (int) (BLOCK_STROKE_ALPHA * 255.0F));
            for (int i = 0; i < source.blocks.size(); i++) {
                BlockProbability block = source.blocks.get(i);
                float alpha = DESTRUCTION_ALPHA * DESTRUCTION_MIN_ALPHA_FACTOR
                        + block.probability * DESTRUCTION_ALPHA * (1.0F - DESTRUCTION_MIN_ALPHA_FACTOR);
                int fill = argb(DESTRUCTION_RED, DESTRUCTION_GREEN, DESTRUCTION_BLUE, (int) (alpha * 255.0F));
                Gizmos.cuboid(new AABB(block.pos).inflate(BLOCK_INFLATE),
                        outline ? GizmoStyle.strokeAndFill(stroke, BLOCK_STROKE_WIDTH, fill)
                                : GizmoStyle.fill(fill));
            }
        }

        // 射线采样点（线判定）：爆心色的反色蓝小点，越靠近爆心越亮。
        if (ExplosionVisualizerConfig.blockRayCast()) {
            for (int i = 0; i < source.rayPoints.size(); i++) {
                RayPoint point = source.rayPoints.get(i);
                int alpha = (int) Math.min(255.0F, point.power / source.maxPower * 255.0F * RAY_ALPHA_GAIN);
                Gizmos.point(point.pos, argb(RAY_RED, RAY_GREEN, RAY_BLUE, alpha), RAY_POINT_SIZE);
            }
        }

        // 实体伤害采样点：安全点绿、暴露点红；可选再从采样点画一条射线。
        if (ExplosionVisualizerConfig.entitySamplePoints()) {
            boolean rayCast = ExplosionVisualizerConfig.samplePointRayCast();
            for (int i = 0; i < source.samples.size(); i++) {
                EntitySample sample = source.samples.get(i);
                Gizmos.point(sample.start, sample.blocked ? COLOR_SAFE : COLOR_EXPOSED, SAMPLE_POINT_SIZE);
                if (rayCast) {
                    // 与玩家相连的判定线统一画成红色，便于一眼看出自己有没有暴露在爆炸里。
                    int lineColor = sample.player ? COLOR_PLAYER_LINE
                            : (sample.blocked ? COLOR_SAFE : COLOR_EXPOSED);
                    Gizmos.line(sample.start, sample.end, lineColor, SAMPLE_RAY_WIDTH);
                }
                if (sample.blocked) {
                    Gizmos.point(sample.end, COLOR_SAFE_RETURN, SAMPLE_POINT_SIZE);
                }
            }
        }

        // 爆炸中心。
        if (ExplosionVisualizerConfig.explosionCenter()) {
            Gizmos.point(source.center, COLOR_CENTER, CENTER_POINT_SIZE);
        }

        // 爆炸源的引信倒计时。
        if (ExplosionVisualizerConfig.explodeTimer() && source.fuseTicks >= 0) {
            float seconds = source.fuseTicks / 20.0F;
            int color = seconds <= 1.0F ? TIMER_COLOR_SOON : TIMER_COLOR_LATER;
            Vec3 at = new Vec3(source.center.x, source.entityBox.maxY + TIMER_Y_OFFSET, source.center.z);
            Gizmos.billboardText(String.format(java.util.Locale.ROOT, "%.1f", seconds), at,
                    TextGizmo.Style.forColor(color).withScale(1.0F));
        }

        // 爆炸源本体，画在最上层。
        Gizmos.cuboid(source.entityBox.inflate(ENTITY_INFLATE),
                GizmoStyle.strokeAndFill(SOURCE_STROKE[source.kind], SOURCE_STROKE_WIDTH,
                        SOURCE_FILL[source.kind]));
    }

    /**
     * 画一个半透明浅绿球：只画背对镜头的那半面，于是无论相机在球外还是球内，一条视线都只
     * 穿过一层球壳。
     */
    private static void drawSphere(Vec3 center, float radius, int color, Vec3 camera) {
        GizmoStyle style = GizmoStyle.fill(color);
        for (int band = 0; band < SPHERE_LATITUDES; band++) {
            Vec3[] lower = SPHERE_RINGS[band];
            Vec3[] upper = SPHERE_RINGS[band + 1];
            for (int j = 0; j < SPHERE_LONGITUDES; j++) {
                Vec3 quadCenter = center.add(SPHERE_QUAD_CENTERS[band][j].scale(radius));
                if (SPHERE_QUAD_NORMALS[band][j].dot(camera.subtract(quadCenter)) > 0.0D) {
                    continue;
                }
                int next = (j + 1) % SPHERE_LONGITUDES;
                Gizmos.rect(
                        center.add(lower[j].scale(radius)),
                        center.add(upper[j].scale(radius)),
                        center.add(upper[next].scale(radius)),
                        center.add(lower[next].scale(radius)), style);
            }
        }
    }

    /** 每次绘制前把配色从配置读进来，于是配置界面的改动立刻生效。 */
    private static void refreshColors() {
        int destruction = VisualizerConfig.color(RULE, "destruction", 0xFFFF7A1E);
        DESTRUCTION_RED = (destruction >> 16) & 0xFF;
        DESTRUCTION_GREEN = (destruction >> 8) & 0xFF;
        DESTRUCTION_BLUE = destruction & 0xFF;
        COLOR_CENTER = VisualizerConfig.color(RULE, "center", 0xFFDCFF00);
        int ray = VisualizerConfig.color(RULE, "ray", 0xFF2300FF);
        RAY_RED = (ray >> 16) & 0xFF;
        RAY_GREEN = (ray >> 8) & 0xFF;
        RAY_BLUE = ray & 0xFF;
        COLOR_SAFE = VisualizerConfig.color(RULE, "safe", 0xA500FF1E);
        COLOR_EXPOSED = VisualizerConfig.color(RULE, "exposed", 0xF0FF0000);
        COLOR_SPHERE = VisualizerConfig.color(RULE, "sphere", 0x66B4F0B4);
    }

    private static int argb(int red, int green, int blue, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (red << 16) | (green << 8) | blue;
    }

    /** 一个爆炸源的采集结果。 */
    private static final class Source {
        private final Entity entity;
        private final Vec3 center;
        private final float power;
        private final int kind;
        private final AABB entityBox;
        /** 引信剩余 tick；没有倒计时的爆炸源为 -1。 */
        private final int fuseTicks;
        /** 这次爆炸会不会破坏方块（风弹只触发方块，为 false）。 */
        private final boolean destructs;
        /** 方块爆炸源所在方块；实体爆炸源为 null。射线推演时把源方块当成空气，否则会立刻被它挡死。 */
        private final BlockPos sourceBlock;

        private Source(Entity entity, Vec3 center, float power, int kind, AABB entityBox,
                int fuseTicks, boolean destructs) {
            this(entity, center, power, kind, entityBox, fuseTicks, destructs, null);
        }

        private Source(Entity entity, Vec3 center, float power, int kind, AABB entityBox,
                int fuseTicks, boolean destructs, BlockPos sourceBlock) {
            this.entity = entity;
            this.center = center;
            this.power = power;
            this.kind = kind;
            this.entityBox = entityBox;
            this.fuseTicks = fuseTicks;
            this.destructs = destructs;
            this.sourceBlock = sourceBlock;
        }
    }

    /** 渲染线程只读的爆炸快照。 */
    private static final class RenderSource {
        private final Vec3 center;
        private final float radius;
        private final int kind;
        private final AABB entityBox;
        private final float maxPower;
        private final int fuseTicks;
        private final List<BlockProbability> blocks;
        private final List<RayPoint> rayPoints;
        private final List<EntitySample> samples;

        private RenderSource(Vec3 center, float radius, int kind, AABB entityBox, float maxPower,
                int fuseTicks, List<BlockProbability> blocks, List<RayPoint> rayPoints,
                List<EntitySample> samples) {
            this.center = center;
            this.radius = radius;
            this.kind = kind;
            this.entityBox = entityBox;
            this.maxPower = maxPower;
            this.fuseTicks = fuseTicks;
            this.blocks = blocks;
            this.rayPoints = rayPoints;
            this.samples = samples;
        }
    }

    /** 一块会被破坏的方块及其概率。 */
    private static final class BlockProbability {
        private final BlockPos pos;
        private final float probability;

        private BlockProbability(BlockPos pos, float probability) {
            this.pos = pos;
            this.probability = probability;
        }
    }

    /** 射线上的一个采样点。 */
    private static final class RayPoint {
        private final Vec3 pos;
        private final float power;

        private RayPoint(Vec3 pos, float power) {
            this.pos = pos;
            this.power = power;
        }
    }

    /** 实体身上的一个爆炸伤害采样点。 */
    private static final class EntitySample {
        private final Vec3 start;
        private final Vec3 end;
        private final boolean blocked;
        /** 这个采样点是否属于本地玩家（与玩家相连的判定线画成红色）。 */
        private final boolean player;

        private EntitySample(Vec3 start, Vec3 end, boolean blocked, boolean player) {
            this.start = start;
            this.end = end;
            this.blocked = blocked;
            this.player = player;
        }
    }

    /** 只读渲染快照，tick 线程整体替换、渲染线程整体读取。 */
    private static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(Collections.<RenderSource>emptyList());

        private final List<RenderSource> sources;

        private Snapshot(List<RenderSource> sources) {
            this.sources = sources;
        }
    }
}
