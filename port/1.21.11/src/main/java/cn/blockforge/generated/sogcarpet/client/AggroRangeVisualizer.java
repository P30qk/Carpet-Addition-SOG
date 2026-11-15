package cn.blockforge.generated.sogcarpet.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * “索敌范围可视化”（原“敌对生物仇恨范围可视化”）的客户端绘制。
 *
 * <p>两种模式：</p>
 * <ul>
 *   <li><b>全部模式（默认）</b>：对每一只玩家附近的敌对生物，在它“索敌范围”的最外围画一个红色
 *       半透明圆球。范围取该生物的 {@code minecraft:follow_range} 属性值，再乘上<b>本地玩家</b>
 *       对它而言的“可见度系数” {@code player.getVisibilityPercent(mob)}，于是圆球会随玩家状态
 *       变化：潜行缩小到 0.8 倍、戴上对应头颅缩小到 0.5 倍。</li>
 *   <li><b>选择模式</b>：只显示用<b>木剑右键</b>选中的那一个生物的范围球（与速度可视化同样的
 *       选择方式），方便单独观察某只怪的索敌范围。</li>
 * </ul>
 *
 * <p>属性由原版同步到客户端，所以整条可视化完全在客户端完成，服务端不需要发任何数据。</p>
 */
public final class AggroRangeVisualizer {
    private static final String RULE = "aggroRangeVisualizer";

    /** 只扫玩家周围这个距离（格）以内的敌对生物。 */
    private static final double SCAN_RANGE = 48.0D;
    private static final double SCAN_RANGE_SQR = SCAN_RANGE * SCAN_RANGE;
    /** 同屏最多画多少个圆球。 */
    private static final int MAX_SPHERES = 12;
    /** 每隔多少客户端 tick 重算一次。 */
    private static final int INTERVAL_TICKS = 4;
    /** 半径夹取范围（格）。恶魂的 follow_range 是 100，这里放到 100 才能完整显示。 */
    private static final float MIN_RADIUS = 1.0F;
    private static final float MAX_RADIUS = 100.0F;

    /** 红色半透明填充。 */
    private static int COLOR_SPHERE = 0x55FF3B30;

    /** 从用户配置刷新颜色（没有配置时回落到默认值）。 */
    private static void refreshColors() {
        COLOR_SPHERE = VisualizerConfig.color(RULE, "sphere", 0x55FF3B30);
    }

    private static final int LONGITUDES = 20;
    private static final int LATITUDES = 10;
    private static final Vec3[][] RINGS = new Vec3[LATITUDES + 1][LONGITUDES];
    private static final Vec3[][] QUAD_CENTERS = new Vec3[LATITUDES][LONGITUDES];
    private static final Vec3[][] QUAD_NORMALS = new Vec3[LATITUDES][LONGITUDES];

    static {
        for (int i = 0; i <= LATITUDES; i++) {
            double latitude = -Math.PI / 2.0D + Math.PI * i / LATITUDES;
            double ringRadius = Math.cos(latitude);
            double ringY = Math.sin(latitude);
            for (int j = 0; j < LONGITUDES; j++) {
                double longitude = 2.0D * Math.PI * j / LONGITUDES;
                RINGS[i][j] = new Vec3(ringRadius * Math.cos(longitude), ringY,
                        ringRadius * Math.sin(longitude));
            }
        }
        for (int band = 0; band < LATITUDES; band++) {
            for (int j = 0; j < LONGITUDES; j++) {
                int next = (j + 1) % LONGITUDES;
                Vec3 center = RINGS[band][j].add(RINGS[band + 1][j])
                        .add(RINGS[band + 1][next]).add(RINGS[band][next]).scale(0.25D);
                QUAD_CENTERS[band][j] = center;
                QUAD_NORMALS[band][j] = center.normalize();
            }
        }
    }

    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    private static int tickCounter;
    /** 选择模式下木剑右键选中的目标。 */
    private static Entity selected;
    private static ClientLevel lastLevel;

    /**
     * 每种生物类型第一次读到的仇恨范围，缓存下来。
     *
     * <p>{@code follow_range} 属性在客户端不是同步属性，取值可能偶尔为空或抖动；把它按类型
     * 缓存后，同一种生物球的大小始终一致，不会出现“同一个怪一会儿大一会儿小”的观感。</p>
     */
    private static final Map<EntityType<?>, Double> BASE_RANGE = new ConcurrentHashMap<>();

    private AggroRangeVisualizer() {
    }

    /** 客户端入口调用一次。 */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(AggroRangeVisualizer::clientTick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(AggroRangeVisualizer::renderGizmos);
    }

    private static void clientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (!VisualizerState.isActive(RULE) || level == null || client.player == null) {
            snapshot = Snapshot.EMPTY;
            selected = null;
            lastLevel = null;
            return;
        }
        if (level != lastLevel) {
            selected = null;
            lastLevel = level;
        }
        if (selected != null && (selected.isRemoved() || selected.level() != level)) {
            selected = null;
        }

        // 选择模式：只画木剑右键选中的那一个目标，逐 tick 更新以便跟随它。
        if (VisualizerConfig.option(RULE, "selectMode", false)) {
            if (!(selected instanceof LivingEntity living) || living == client.player
                    || living instanceof EnderDragon || living instanceof WitherBoss) {
                snapshot = Snapshot.EMPTY;
                return;
            }
            Sphere sphere = sphereFor(client, living);
            snapshot = sphere == null ? Snapshot.EMPTY : new Snapshot(List.of(sphere));
            return;
        }

        if (++tickCounter < INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        Vec3 eye = client.player.position();
        List<Sphere> spheres = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !isTracked(entity, living)) {
                continue;
            }
            double distanceSqr = entity.position().distanceToSqr(eye);
            if (distanceSqr > SCAN_RANGE_SQR) {
                continue;
            }
            Sphere sphere = sphereFor(client, living);
            if (sphere != null) {
                spheres.add(sphere);
            }
        }
        if (spheres.size() > MAX_SPHERES) {
            spheres.sort(Comparator.comparingDouble(Sphere::distanceSqr));
            spheres = new ArrayList<>(spheres.subList(0, MAX_SPHERES));
        }
        snapshot = new Snapshot(spheres);
    }

    /** 由某只生物的 follow_range 与本地玩家可见度算出它的范围球；算不出来返回 null。 */
    private static Sphere sphereFor(Minecraft client, LivingEntity living) {
        AttributeInstance attribute = living.getAttribute(Attributes.FOLLOW_RANGE);
        if (attribute == null) {
            return null;
        }
        double range = baseRange(living.getType(), attribute);
        if (!Double.isFinite(range) || range <= 0.0D) {
            return null;
        }
        double factor = client.player.getVisibilityPercent(living);
        if (!Double.isFinite(factor) || factor <= 0.0D) {
            factor = 1.0D;
        }
        float radius = (float) Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, range * factor));
        return new Sphere(living.getBoundingBox().getCenter(), radius,
                living.position().distanceToSqr(client.player.position()));
    }

    /**
     * 选择模式下用<b>石剑</b>右键选中一个实体（由客户端 {@code UseEntityCallback} 调用）。
     * 只在选择模式、且主手是石剑时生效，不影响正常右键使用。
     */
    public static void selectFromInteraction(Level level, Entity entity) {
        if (level == null || !level.isClientSide() || !VisualizerState.isActive(RULE)) {
            return;
        }
        if (!VisualizerConfig.option(RULE, "selectMode", false)) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || entity == null
                || entity == client.player || entity.isRemoved()) {
            return;
        }
        if (!client.player.getMainHandItem().is(Items.STONE_SWORD)) {
            return;
        }
        selected = entity;
    }

    /**
     * 这只生物要不要画仇恨范围球：跳过会被巨球糊屏的末影龙、凋灵；普通敌对生物（{@link Enemy}，
     * 含只能靠指令召唤的巨人、幻术师、恶魂等）按逐生物开关决定；杀手兔是兔子的一种变体
     * （不是独立的 {@link net.minecraft.world.entity.EntityType}），单独判断。
     */
    private static boolean isTracked(Entity entity, LivingEntity living) {
        if (living.isRemoved() || entity instanceof EnderDragon || entity instanceof WitherBoss) {
            return false;
        }
        if (living instanceof Rabbit rabbit && rabbit.getVariant() == Rabbit.Variant.EVIL) {
            return AggroRangeConfig.isKillerBunnyEnabled();
        }
        return living instanceof Enemy && AggroRangeConfig.isEnabled(living.getType());
    }

    /** 取该生物类型的仇恨范围：优先用缓存，首次读到时把有效的值记下来。 */
    private static double baseRange(EntityType<?> type, AttributeInstance attribute) {
        Double cached = BASE_RANGE.get(type);
        if (cached != null) {
            return cached;
        }
        double value = attribute.getValue();
        if (Double.isFinite(value) && value > 0.0D) {
            BASE_RANGE.put(type, value);
        }
        return value;
    }

    private static void renderGizmos(WorldRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        refreshColors();
        Snapshot current = snapshot;
        if (current.spheres.isEmpty()) {
            return;
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        try (Gizmos.TemporaryCollection collection =
                context.worldRenderer().collectPerFrameGizmos()) {
            GizmoStyle style = GizmoStyle.fill(COLOR_SPHERE);
            for (int i = 0; i < current.spheres.size(); i++) {
                Sphere sphere = current.spheres.get(i);
                drawSphere(sphere.center, sphere.radius, style, camera);
            }
        }
    }

    /**
     * 画一个半透明球壳，做法与幽匿检测范围可视化的检测球完全一致：
     * 相机在球外时只提交<b>正对镜头</b>的那一半面片，相机在球内（玩家就站在范围里）时提交全部面片。
     * 无论哪种情况一条视线最多穿过一层球壳，颜色和轮廓都稳定，不会随视角忽大忽小；球壳分辨率
     * 取 {@value #LONGITUDES}×{@value #LATITUDES}，看着是一个浑圆的正球而不是多面体。
     */
    private static void drawSphere(Vec3 center, float radius, GizmoStyle style, Vec3 camera) {
        boolean inside = camera.distanceToSqr(center) <= (double) radius * radius;
        for (int band = 0; band < LATITUDES; band++) {
            Vec3[] lower = RINGS[band];
            Vec3[] upper = RINGS[band + 1];
            for (int j = 0; j < LONGITUDES; j++) {
                if (!inside) {
                    Vec3 quadCenter = center.add(QUAD_CENTERS[band][j].scale(radius));
                    if (QUAD_NORMALS[band][j].dot(camera.subtract(quadCenter)) <= 0.0D) {
                        continue;
                    }
                }
                int next = (j + 1) % LONGITUDES;
                Gizmos.rect(
                        center.add(lower[j].scale(radius)),
                        center.add(upper[j].scale(radius)),
                        center.add(upper[next].scale(radius)),
                        center.add(lower[next].scale(radius)), style);
            }
        }
    }

    /** 一个待绘制圆球。 */
    private record Sphere(Vec3 center, float radius, double distanceSqr) {
    }

    /** 只读渲染快照。 */
    private static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(Collections.<Sphere>emptyList());

        private final List<Sphere> spheres;

        private Snapshot(List<Sphere> spheres) {
            this.spheres = spheres;
        }
    }
}
