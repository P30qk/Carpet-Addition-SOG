package cn.blockforge.generated.sogcarpet.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * “爆炸可视化”规则的客户端实现（移植自 lucidity 的 explosion visualizer）。
 *
 * <p>只要玩家附近存在<b>已点燃的 TNT</b>（{@link PrimedTnt}）或<b>正在膨胀、马上要炸的苦力怕</b>
 * （{@link Creeper#getSwellDir()} 大于 0），就按 lucidity 的算法把这次爆炸整体预览出来：</p>
 * <ul>
 *   <li><b>橙色半透明方块</b>：会被这次爆炸破坏的方块，颜色取 lucidity 默认的
 *       {@code explosion_destruction_color}(255,122,30)，透明度按“方块离爆心有多近”的概率
 *       从 30% 到 100% 渐变——越靠中心越实；</li>
 *   <li><b>蓝色小光点</b>：从爆心射出的爆炸射线上每一步的采样点（线判定），颜色取爆心色的反色
 *       （lucidity 里的蓝三角就是这么来的），越靠近爆心越亮；</li>
 *   <li><b>黄绿色光点</b>：爆炸中心；</li>
 *   <li><b>绿色／红色光点</b>：会被波及的实体身上的爆炸伤害采样点——被方块挡住的是安全点（绿），
 *       能直通爆心的暴露点（红）；</li>
 *   <li><b>淡绿色半透明圆球</b>：在爆炸范围最外围罩一圈，以爆炸中心为球心。</li>
 * </ul>
 *
 * <p>光点用原版 {@code Gizmos.point} 画，它的尺寸是<b>屏幕像素</b>而非世界坐标
 * （{@code debug_point.vsh} 里直接写 {@code gl_PointSize = LineWidth}）。所以尺寸常量
 * 取 2.5 / 4 / 9 这种像素量级；填 0.06 之类的“世界尺寸”会变成亚像素、什么都看不见。</p>
 *
 * <p>破坏判定完全照搬原版爆炸：把 16×16×16 立方体表面上的 1352 个方向当作射线，从爆心出发，
 * 每步前进 0.3 格、能量衰减 0.225，命中方块再按 {@code (爆炸抗性 + 0.3) * 0.3} 额外衰减，
 * 能量耗尽就停。每根射线的初始能量用 {@code 半径 * 1.3}（原版是 0.7~1.3 随机，这里取上界），
 * 于是每个方块会算出一个“被破坏概率”，预览稳定不闪。</p>
 *
 * <p>光点云按“所有射线共享预算、各自等步长抽稀”来采样，铺满整个球壳；若按“装够就停”
 * 截断，点会全堆在最先遍历到的几条射线上，整团光点偏到一侧。</p>
 *
 * <p>所有几何都用<b>方块/实体的真实坐标</b>：方块用 {@code new AABB(BlockPos)}（正好是
 * 那一格，只额外外扩 0.002 格避开共面闪烁），爆心、采样点直接用各自的 {@link Vec3}。
 * TNT 的爆心按原版 {@code Explosion} 的取法固定在 {@code getY() + 0.0625}（1/16 格），
 * <b>不再乘引信 tick</b>：之前用 {@code getFuse() * 0.0625}，引信从 80 倒数到 0 时爆心会
 * 从方块上方 5 格一路掉回原地，整幅预览跟着往下滑，这才是“渲染位置偏移下降”。</p>
 *
 * <p>爆炸是短时事件，所以每 {@value #SCAN_INTERVAL_TICKS} tick 重算一次快照，渲染只读快照；
 * 只预览玩家 {@value #SEARCH_RANGE} 格以内的爆炸，避免 TNT 阵列拖垮客户端。</p>
 */
public final class ExplosionVisualizer {
    /** 本规则在总控里的名字。 */
    private static final String RULE = "explosionVisualizer";

    /** 只预览玩家周围这个距离（格）以内的爆炸。 */
    private static final double SEARCH_RANGE = 16.0D;
    /**
     * 扫描间隔。取 1（每 tick 重算一遍）而不是 3：爆炸源（尤其是还在下落的 TNT）本身
     * 一直在动，隔几 tick 才算一次的话，整幅预览会明显落在 TNT 后面，看起来就是
     * “偏离方向”。射线推演本身只有几万次循环，每 tick 一次完全不卡。
     */
    private static final int SCAN_INTERVAL_TICKS = 1;
    /** 同屏最多同时预览多少个爆炸源。 */
    private static final int MAX_SOURCES = 4;
    /** 单个爆炸最多标出多少块会被破坏的方块。 */
    private static final int MAX_BLOCKS = 2000;
    /**
     * 单个爆炸最多画多少个射线采样点。所有 1352 条射线<b>共享</b>这份预算：按“每条射线
     * 每隔 stride 步取一个点”均匀抽稀，点云才会铺满整个球壳。
     * <p>之前是“装满就停止添加”，而射线是按 i/j/k 顺序遍历的，前 4000 个点全挤在最先
     * 遍历到的那一小撮方向里——光点云整个偏到一侧，这才是“偏离方向”的观感来源之一。</p>
     */
    private static final int MAX_RAY_POINTS = 20000;
    /** 单个爆炸最多处理多少个被波及的实体。 */
    private static final int MAX_ENTITIES = 12;
    /** 单个实体最多取多少个伤害采样点。 */
    private static final int MAX_SAMPLES_PER_ENTITY = 256;

    /** TNT 爆炸威力（原版默认值）。 */
    private static final float TNT_POWER = 4.0F;
    /**
     * 原版 TNT 引爆时爆心的固定抬高量：{@code PrimedTnt.explode()} 用的是
     * {@code getY(0.0625)}，也就是实体原点往上 1/16 格，和引信剩余 tick 无关。
     */
    private static final double TNT_CENTER_Y_OFFSET = 0.0625D;
    /** 苦力怕爆炸威力。 */
    private static final float CREEPER_POWER = 3.0F;
    /** 闪电苦力怕爆炸威力。 */
    private static final float CHARGED_CREEPER_POWER = 6.0F;

    /** 爆炸射线每步前进的距离。 */
    private static final float RAY_STEP = 0.3F;
    /** 爆炸射线每步的自然衰减。 */
    private static final float RAY_DECAY = 0.225F;
    /** 每根射线初始能量的下界比例（原版 0.7~1.3 随机，这里统一取 1.3 的上界做确定性预览）。 */
    private static final float POWER_MIN_FACTOR = 0.7F;
    private static final float POWER_MAX_FACTOR = 1.3F;
    /** 爆炸边缘 = 威力 × 这个系数：射线初始能量 1.3×威力，每 0.3 格衰减 0.225。 */
    private static final float EDGE_FACTOR = POWER_MAX_FACTOR / RAY_DECAY * RAY_STEP;
    /** 立方体射线的网格边长：只有表面上的方向会被采用，共 1352 条。 */
    private static final int RAY_GRID = 16;

    /** lucidity 的 explosion_destruction_color：橙，基础透明度 70/255。 */
    private static final int DESTRUCTION_RED = 255;
    private static final int DESTRUCTION_GREEN = 122;
    private static final int DESTRUCTION_BLUE = 30;
    private static final float DESTRUCTION_ALPHA = 130.0F / 255.0F;
    /**
     * 破坏方块的最低透明度系数。lucidity 原值是 0.05，叠在深色方块上、或者隔几步远
     * 就基本看不出来，用户看到的就是“方块没有变色”；上调到 0.3，贴脸到边缘都能看清。
     */
    private static final float DESTRUCTION_MIN_ALPHA_FACTOR = 0.3F;
    /** 方块高亮盒微微外扩，避免和方块自身的表面共面而深度闪烁。 */
    private static final double BLOCK_INFLATE = 0.002D;
    /**
     * 破坏方块描边的开关阈值：方块数不超过它时，除了橙色填充再补一圈亮橙描边，
     * 相邻方块之间才有分界、不会糊成一大团橙色。超过阈值（例如 TNT 阵列连环炸）
     * 就退回纯填充，省下几万条描边线的开销。
     */
    private static final int MAX_OUTLINED_BLOCKS = 600;
    private static final float BLOCK_STROKE_WIDTH = 1.5F;
    private static final float BLOCK_STROKE_ALPHA = 200.0F / 255.0F;
    /** lucidity 的 explosion_center_color：(220,255,0)。 */
    private static final int COLOR_CENTER = 0xFFDCFF00;
    /** 射线采样点用爆心色的反色：(35,0,255) 的蓝。 */
    private static final int RAY_RED = 35;
    private static final int RAY_GREEN = 0;
    private static final int RAY_BLUE = 255;
    /** lucidity 的 sample_point_safe_color：(0,255,30)，alpha 165。 */
    private static final int COLOR_SAFE = 0xA500FF1E;
    /** lucidity 的 sample_point_exposed_color：(255,0,0)，alpha 240。 */
    private static final int COLOR_EXPOSED = 0xF0FF0000;
    /** 被挡住的安全点回程线用的反色。 */
    private static final int COLOR_SAFE_RETURN = 0xA5FF00E1;
    /** 爆炸源本体：TNT 深蓝、苦力怕绿色。 */
    private static final int COLOR_TNT_STROKE = 0xFF7E96E8;
    private static final int COLOR_TNT_FILL = 0x883A4C9E;
    private static final int COLOR_CREEPER_STROKE = 0xFF8CE08C;
    private static final int COLOR_CREEPER_FILL = 0x884FA64F;

    /**
     * 光点尺寸，单位是<b>屏幕像素</b>，不是世界坐标：原版 {@code Gizmos.point} 的 size 会
     * 原样写进 {@code gl_PointSize}（{@code debug_point.vsh} 里就一行
     * {@code gl_PointSize = LineWidth;}）。
     * <p>之前这里填的是 0.06 / 0.09 / 0.22 这种“世界尺寸”量级，被当成像素后就只剩亚像素，
     * 等于什么都没画——<b>这就是“蓝色射线光点”和“实体红点”完全不出现的根因</b>。
     * 现在按 lucidity 的观感折算成像素：射线点约等于它那个 0.03 格的面向镜头小三角，
     * 实体伤害采样点稍大，爆心点最大。</p>
     */
    private static final float RAY_POINT_SIZE = 2.5F;
    private static final float CENTER_POINT_SIZE = 9.0F;
    private static final float SAMPLE_POINT_SIZE = 4.0F;
    /** 射线采样点透明度最多放大到 2 倍（lucidity 原样）。 */
    private static final float RAY_ALPHA_GAIN = 2.0F;

    private static final double ENTITY_INFLATE = 0.02D;
    private static final float SOURCE_STROKE_WIDTH = 2.0F;

    /** 淡绿色半透明球：浅绿 + 约 40% 不透明度。 */
    private static final int COLOR_SPHERE = 0x66B4F0B4;

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

    private ExplosionVisualizer() {
    }

    /** 客户端入口调用一次：tick 扫描与渲染回调（Ctrl+O 总开关在 {@link VisualizerState}）。 */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ExplosionVisualizer::clientTick);
        LevelRenderEvents.BEFORE_GIZMOS.register(ExplosionVisualizer::renderGizmos);
    }

    private static void clientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (!VisualizerState.isActive(RULE) || level == null || client.player == null) {
            snapshot = Snapshot.EMPTY;
            return;
        }
        if (++tickCounter < SCAN_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        snapshot = scan(level, client.player.position());
    }

    private static Snapshot scan(ClientLevel level, Vec3 playerPos) {
        List<Source> sources = new ArrayList<>();
        double searchSqr = SEARCH_RANGE * SEARCH_RANGE;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.position().distanceToSqr(playerPos) > searchSqr) {
                continue;
            }
            if (entity instanceof PrimedTnt tnt) {
                sources.add(new Source(entity,
                        new Vec3(tnt.getX(), tnt.getY() + TNT_CENTER_Y_OFFSET, tnt.getZ()),
                        TNT_POWER, true, entity.getBoundingBox()));
            } else if (entity instanceof Creeper creeper && creeper.getSwellDir() > 0) {
                float power = creeper.isPowered() ? CHARGED_CREEPER_POWER : CREEPER_POWER;
                sources.add(new Source(entity, creeper.position(), power, false, entity.getBoundingBox()));
            }
            if (sources.size() >= MAX_SOURCES) {
                break;
            }
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

    /** 对单个爆炸源跑一遍原版射线模型，收集破坏方块、射线采样点与实体伤害采样点。 */
    private static RenderSource simulate(ClientLevel level, Source source) {
        float radius = source.power;
        float maxPower = radius * POWER_MAX_FACTOR;
        float minPower = radius * POWER_MIN_FACTOR;

        Map<Long, Float> affects = new HashMap<>();
        Map<Long, BlockPos> affectedPositions = new HashMap<>();
        List<RayPoint> rayPoints = new ArrayList<>();
        // 所有射线共享同一套抽稀步长：单条射线最多走 ceil(maxPower / RAY_DECAY) 步，
        // 1352 条射线全走完就是这个上限；据此算出 stride，保证点云撒满整个球壳且不超预算。
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
                BlockState state = level.getBlockState(pos);
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

        List<BlockProbability> blocks = new ArrayList<>();
        for (Map.Entry<Long, Float> entry : affects.entrySet()) {
            if (blocks.size() >= MAX_BLOCKS) {
                break;
            }
            blocks.add(new BlockProbability(affectedPositions.get(entry.getKey()), entry.getValue()));
        }

        List<EntitySample> samples = collectEntitySamples(level, source, radius);

        return new RenderSource(source.center, radius, source.tnt, source.entityBox, maxPower,
                blocks, rayPoints, samples);
    }

    /**
     * 照抄原版 {@code Explosion.getSeenPercent}：在实体包围盒里均匀取样，从每个采样点向爆心
     * 连线，被方块挡住的是安全点（绿），直通的会吃到伤害（红）。
     */
    private static List<EntitySample> collectEntitySamples(ClientLevel level, Source source, float radius) {
        List<EntitySample> samples = new ArrayList<>();
        double reach = radius * 2.0D;
        double reachSqr = reach * reach;
        int entities = 0;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == source.entity || entity.position().distanceToSqr(source.center) > reachSqr) {
                continue;
            }
            if (++entities > MAX_ENTITIES) {
                break;
            }
            sampleEntity(level, entity, source.center, samples);
        }
        return samples;
    }

    private static void sampleEntity(ClientLevel level, Entity entity, Vec3 center, List<EntitySample> out) {
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
                    if (hit.getType() == HitResult.Type.BLOCK) {
                        blocked = true;
                        end = hit.getLocation();
                    }
                    out.add(new EntitySample(start, end, blocked));
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

    private static void renderGizmos(LevelRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        Snapshot current = snapshot;
        if (current.sources.isEmpty()) {
            return;
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        try (Gizmos.TemporaryCollection collection = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
            for (int i = 0; i < current.sources.size(); i++) {
                renderSource(current.sources.get(i), camera);
            }
        }
    }

    private static void renderSource(RenderSource source, Vec3 camera) {
        // 淡绿色半透明球先画：它是最远的一层，垫在最后面；橙色方块、光点随后叠上去。
        // 顺序很关键——半透明是“先画的在后、后画的在前”，球画在最后的话会把球内的
        // 方块和光点整体罩上一层绿，看起来就发浑、不清晰。
        drawSphere(source.center, source.radius * EDGE_FACTOR, COLOR_SPHERE, camera);

        // 会被炸掉的方块：lucidity 橙色，透明度按概率渐变；方块用精确的那一格 AABB，
        // 只微微外扩一点点避开与方块表面的共面闪烁。方块不多时再补一圈描边，块与块之间
        // 才有分界，不会糊成一团橙色。
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

        // 射线采样点（线判定）：爆心色的反色蓝小点，越靠近爆心越亮——参考图里的蓝色光点群。
        for (int i = 0; i < source.rayPoints.size(); i++) {
            RayPoint point = source.rayPoints.get(i);
            int alpha = (int) Math.min(255.0F, point.power / source.maxPower * 255.0F * RAY_ALPHA_GAIN);
            Gizmos.point(point.pos, argb(RAY_RED, RAY_GREEN, RAY_BLUE, alpha), RAY_POINT_SIZE);
        }

        // 实体伤害采样点：安全点绿、暴露点红；安全点被挡住时再画一条回程点。
        for (int i = 0; i < source.samples.size(); i++) {
            EntitySample sample = source.samples.get(i);
            Gizmos.point(sample.start, sample.blocked ? COLOR_SAFE : COLOR_EXPOSED, SAMPLE_POINT_SIZE);
            if (sample.blocked) {
                Gizmos.point(sample.end, COLOR_SAFE_RETURN, SAMPLE_POINT_SIZE);
            }
        }

        // 爆炸中心。
        Gizmos.point(source.center, COLOR_CENTER, CENTER_POINT_SIZE);

        // 爆炸源本体：TNT 深蓝、苦力怕绿色，画在最上层。
        GizmoStyle body = source.tnt
                ? GizmoStyle.strokeAndFill(COLOR_TNT_STROKE, SOURCE_STROKE_WIDTH, COLOR_TNT_FILL)
                : GizmoStyle.strokeAndFill(COLOR_CREEPER_STROKE, SOURCE_STROKE_WIDTH, COLOR_CREEPER_FILL);
        Gizmos.cuboid(source.entityBox.inflate(ENTITY_INFLATE), body);
    }

    /**
     * 画一个半透明浅绿球：把预先算好的单位球壳顶点按半径缩放、按球心平移后提交。
     * <b>只画背对镜头的那半面</b>（法线背离相机的一侧），于是无论相机在球外还是球内，
     * 一条视线都只穿过一层球壳：
     * <ul>
     *   <li>相机在球外时，看到的是远侧球面——球里的橙色方块、光点不会被近侧球面再糊上
     *       一层，内容看得清楚；</li>
     *   <li>相机在球内时（玩家就站在爆炸半径里，最常见的情况），不会像以前那样把前后两面
     *       的 800 个面片全部叠上来，透明度翻倍、越看越浑。</li>
     * </ul>
     * 调用方把球<b>最先</b>提交：半透明是后画的盖在先画的上面，球垫在最底层，球内的方块、
     * 光点才能清楚地叠在它前面。
     */
    private static void drawSphere(Vec3 center, float radius, int color, Vec3 camera) {
        GizmoStyle style = GizmoStyle.fill(color);
        for (int band = 0; band < SPHERE_LATITUDES; band++) {
            Vec3[] lower = SPHERE_RINGS[band];
            Vec3[] upper = SPHERE_RINGS[band + 1];
            for (int j = 0; j < SPHERE_LONGITUDES; j++) {
                Vec3 quadCenter = center.add(SPHERE_QUAD_CENTERS[band][j].scale(radius));
                // 丢弃法线朝向相机的面片，只留下背离相机的一层，保证一条视线最多穿过一层球壳。
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

    private static int argb(int red, int green, int blue, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (red << 16) | (green << 8) | blue;
    }

    /** 一个爆炸源的采集结果。 */
    private static final class Source {
        private final Entity entity;
        private final Vec3 center;
        private final float power;
        private final boolean tnt;
        private final AABB entityBox;

        private Source(Entity entity, Vec3 center, float power, boolean tnt, AABB entityBox) {
            this.entity = entity;
            this.center = center;
            this.power = power;
            this.tnt = tnt;
            this.entityBox = entityBox;
        }
    }

    /** 渲染线程只读的爆炸快照。 */
    private static final class RenderSource {
        private final Vec3 center;
        private final float radius;
        private final boolean tnt;
        private final AABB entityBox;
        private final float maxPower;
        private final List<BlockProbability> blocks;
        private final List<RayPoint> rayPoints;
        private final List<EntitySample> samples;

        private RenderSource(Vec3 center, float radius, boolean tnt, AABB entityBox, float maxPower,
                List<BlockProbability> blocks, List<RayPoint> rayPoints, List<EntitySample> samples) {
            this.center = center;
            this.radius = radius;
            this.tnt = tnt;
            this.entityBox = entityBox;
            this.maxPower = maxPower;
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

        private EntitySample(Vec3 start, Vec3 end, boolean blocked) {
            this.start = start;
            this.end = end;
            this.blocked = blocked;
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
