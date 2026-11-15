package cn.blockforge.generated.sogcarpet.client;

import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import cn.blockforge.generated.sogcarpet.client.render.WorldRenderContext;
import cn.blockforge.generated.sogcarpet.client.render.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import cn.blockforge.generated.sogcarpet.client.gizmo.Gizmos;
import cn.blockforge.generated.sogcarpet.client.gizmo.TextGizmo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * “速度/动量/加速度可视化”的客户端实现。
 *
 * <p>用<b>木剑右键</b>某个实体把它设为观察对象。选中的方式是自己做一次从相机出发、
 * 射程很远的射线检测（不依赖原版手长），所以配合 Tweakeroo 的灵魂出窍（freecam）可以在
 * 手长范围外选中实体。选中后以它为中心建立坐标系：</p>
 * <ul>
 *   <li><b>直角坐标轴（X/Y/Z）</b>：正半轴带箭头、负半轴也画出来（较暗）；每根轴上做正交分解，
 *       分别画速度/动量与加速度分量；</li>
 *   <li><b>球极坐标轴（r/仰角/方位角）</b>：画径向矢量、水平与竖直参考圆，并用投影线标出仰角；
 *       两种坐标系可在 Ctrl+V 的配置界面里切换；</li>
 *   <li>“外力”拆成重力 {@code g} 与阻力 {@code -f·v}（见 {@link MotionPhysics}），
 *       外加服务端同步来的施加冲量；</li>
 *   <li>合矢量单独一支，并在末端标注数值（每轴分量只标速度/动量，避免文字拥挤）。</li>
 * </ul>
 *
 * <p>速度单位是格/gt，加速度单位是格/gt²。动量＝速度 × 质量（质量按实体包围盒体积估算）。
 * 全部在客户端计算与绘制，服务端只同步规则开关与施加冲量。</p>
 */
public final class MotionVisualizer {
    private static final String RULE = "motionVisualizer";

    /** 选中射线的最大长度（配合灵魂出窍，尽量选得到远处的实体）。 */
    private static final double SELECT_RANGE = 128.0D;
    /** 坐标轴长度（格）。 */
    private static final double AXIS_LENGTH = 2.0D;
    /** 轴与矢量的线宽。 */
    private static final float AXIS_WIDTH = 2.0F;
    private static final float VECTOR_WIDTH = 4.0F;
    private static final float ACCEL_WIDTH = 2.5F;
    /** 参考圆的分段数（球极模式）。 */
    private static final int CIRCLE_SEGMENTS = 48;
    /** 夹角圆弧的半径（格）。 */
    private static final double ANGLE_RADIUS = 0.9D;
    /** 动量模式下把矢量再放大一点，方便看清。 */
    private static final double MOMENTUM_DISPLAY_SCALE = 5.0D;
    /** 加速度（含重力、阻力）本身很小（0.01~0.1 格/gt²），统一放大后才能看清。 */
    private static final double ACCELERATION_DISPLAY_SCALE = 10.0D;

    private static final int COLOR_AXIS_X = 0xFF9A3A3A;
    private static final int COLOR_AXIS_Y = 0xFF3A9A3A;
    private static final int COLOR_AXIS_Z = 0xFF3A4A9A;
    private static int COLOR_AXIS_DIM = 0xFF555555;
    private static int COLOR_X = 0xFFFF5555;
    private static int COLOR_Y = 0xFF55FF55;
    private static int COLOR_Z = 0xFF5599FF;
    private static int COLOR_RESULTANT = 0xFFFFFFFF;
    private static int COLOR_ACCEL = 0xFFFFAA33;
    private static int COLOR_GRAVITY = 0xFF55FF88;
    private static int COLOR_DRAG = 0xFF33DDDD;
    private static int COLOR_EXTERNAL = 0xFFFF55FF;
    private static final int COLOR_ELEVATION = 0xFFFFE080;
    private static int COLOR_INFO = 0xFFB0F0FF;

    private static Entity selected;
    private static Entity prevEntity;
    private static Vec3 prevVelocity = Vec3.ZERO;
    private static boolean lastUseDown;
    private static ClientLevel lastLevel;
    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    private MotionVisualizer() {
    }

    /** 客户端入口调用一次：注册 tick 采样、外部冲量清空与渲染回调。 */
    public static void init() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> MotionImpulseTracker.clear());
        ClientTickEvents.END_CLIENT_TICK.register(MotionVisualizer::clientTick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(MotionVisualizer::renderGizmos);
    }

    private static void refreshColors() {
        COLOR_X = VisualizerConfig.color(RULE, "x", 0xFFFF5555);
        COLOR_Y = VisualizerConfig.color(RULE, "y", 0xFF55FF55);
        COLOR_Z = VisualizerConfig.color(RULE, "z", 0xFF5599FF);
        COLOR_RESULTANT = VisualizerConfig.color(RULE, "resultant", 0xFFFFFFFF);
        COLOR_ACCEL = VisualizerConfig.color(RULE, "accel", 0xFFFFAA33);
        COLOR_GRAVITY = VisualizerConfig.color(RULE, "gravity", 0xFF55FF88);
        COLOR_DRAG = VisualizerConfig.color(RULE, "drag", 0xFF33DDDD);
        COLOR_EXTERNAL = VisualizerConfig.color(RULE, "external", 0xFFFF55FF);
        COLOR_AXIS_DIM = VisualizerConfig.color(RULE, "axisDim", 0xFF555555);
        COLOR_INFO = VisualizerConfig.color(RULE, "info", 0xFFB0F0FF);
    }

    private static void clientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (!VisualizerState.isActive(RULE) || level == null || client.player == null) {
            reset();
            return;
        }
        if (level != lastLevel) {
            selected = null;
            prevEntity = null;
            lastLevel = level;
        }
        if (selected != null && (selected.isRemoved() || selected.level() != level)) {
            selected = null;
            prevEntity = null;
        }
        handleSelection(client, level);
        if (selected == null) {
            snapshot = Snapshot.EMPTY;
            return;
        }
        snapshot = buildSnapshot(selected);
    }

    private static void reset() {
        selected = null;
        prevEntity = null;
        prevVelocity = Vec3.ZERO;
        lastUseDown = false;
        lastLevel = null;
        snapshot = Snapshot.EMPTY;
    }

    /**
     * 木剑右键选实体：只在“右键按下的那一 tick”、主手是木剑、且鼠标射线上有实体时选中，
     * 不会一直重复触发，也不影响正常的右键使用。
     */
    private static void handleSelection(Minecraft client, ClientLevel level) {
        boolean down = client.options.keyUse.isDown();
        if (down && !lastUseDown && client.screen == null
                && isWoodenSword(client.player.getMainHandItem())) {
            Entity target = pickEntity(client, level);
            if (target != null) {
                selected = target;
                prevEntity = null;
            }
        }
        lastUseDown = down;
    }

    private static boolean isWoodenSword(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.WOODEN_SWORD);
    }

    /** 自己做的射线检测：从相机眼睛沿视线找最近的、射线真正穿过的实体。 */
    private static Entity pickEntity(Minecraft client, ClientLevel level) {
        // 优先用客户端已经算好的准星目标：普通手长内、以及 Tweakeroo 灵魂出窍时它可能就是
        // 从相机算出来的实体，直接采用最稳。
        if (client.hitResult instanceof EntityHitResult existing
                && existing.getEntity() != client.player && !existing.getEntity().isRemoved()) {
            return existing.getEntity();
        }
        Entity camera = client.getCameraEntity();
        if (camera == null) {
            camera = client.player;
        }
        Vec3 start = camera.getEyePosition();
        Vec3 look = camera.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(SELECT_RANGE));
        Entity best = null;
        double bestSqr = Double.MAX_VALUE;
        for (Entity entity : level.entitiesForRendering()) {
            // 不再要求 isPickable：投掷物（末影珍珠/雪球）默认不可拾取，但用户就是要选它们。
            if (entity == client.player || entity.isRemoved() || entity == camera) {
                continue;
            }
            AABB box = entity.getBoundingBox().inflate(0.3D);
            Optional<Vec3> hit = box.clip(start, end);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = hit.get().distanceToSqr(start);
            if (distance < bestSqr) {
                bestSqr = distance;
                best = entity;
            }
        }
        return best;
    }

    /**
     * 由客户端交互事件（{@code UseEntityCallback} 的客户端侧）直接选中所右键的实体。
     *
     * <p>比逐 tick 轮询右键按键更可靠：不管玩家是点一下还是按住，只要真的右键到了实体就会调用。
     * 仍然要求主手是木剑、规则处于激活状态。</p>
     */
    public static void selectFromInteraction(Level level, Entity entity) {
        if (level == null || !level.isClientSide() || !VisualizerState.isActive(RULE)) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || entity == null
                || entity == client.player || entity.isRemoved()) {
            return;
        }
        if (!isWoodenSword(client.player.getMainHandItem())) {
            return;
        }
        selected = entity;
        prevEntity = null;
    }

    private static Snapshot buildSnapshot(Entity entity) {
        Vec3 origin = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        Vec3 velocity = entity.getDeltaMovement();
        if (entity != prevEntity) {
            prevEntity = entity;
            prevVelocity = velocity;
        }
        Vec3 acceleration = velocity.subtract(prevVelocity);
        prevVelocity = velocity;
        // 优先用服务端下发的“被施加冲量”（远端实体/专用服务器上才拿得到），拿不到再退回本地采集。
        Vec3 impulse = MotionImpulseState.get(entity);
        if (impulse == null) {
            impulse = MotionImpulseTracker.get(entity);
        }
        Vec3 gravity = MotionPhysics.gravity(entity);
        Vec3 drag = MotionPhysics.drag(entity, velocity);

        double mass = mass(entity);
        boolean momentum = MotionVisualizerConfig.momentum();
        double velocityScale = momentum ? MOMENTUM_DISPLAY_SCALE : 1.0D;
        Vec3 momentumVelocity = momentum ? velocity.scale(mass) : velocity;
        Vec3 momentumImpulse = momentum ? impulse.scale(mass) : impulse;
        double speed = velocity.length();
        double momentumMagnitude = speed * mass;

        return new Snapshot(origin, momentumImpulse, momentumVelocity, acceleration, gravity, drag,
                velocityScale, ACCELERATION_DISPLAY_SCALE, momentum,
                MotionVisualizerConfig.spherical(),
                MotionVisualizerConfig.axes(), MotionVisualizerConfig.axisVectors(),
                MotionVisualizerConfig.forces(), MotionVisualizerConfig.external(),
                MotionVisualizerConfig.resultant(),
                speed, momentumMagnitude, entity.getXRot(), entity.getYRot());
    }

    /** 质量的粗略估算：包围盒体积（最小的物体也至少按 0.05 算）。 */
    private static double mass(Entity entity) {
        double width = entity.getBbWidth();
        double height = entity.getBbHeight();
        return Math.max(0.05D, width * width * height);
    }

    // ------------------------------------------------------------------ 绘制

    private static void renderGizmos(WorldRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        refreshColors();
        Snapshot state = snapshot;
        if (state.origin == null) {
            return;
        }
        try (Gizmos.TemporaryCollection collection =
                Gizmos.begin(context)) {
            if (state.spherical) {
                // 球极模式也在原点画上 X/Y/Z 直角坐标轴（正半轴带箭头、负半轴较暗），
                // 再叠加球极的参考球与径向/角度标注。
                if (state.showAxes) {
                    drawAxes(state.origin);
                }
                drawSpherical(state);
            } else {
                if (state.showAxes) {
                    drawAxes(state.origin);
                }
                if (state.showAxisVectors) {
                    drawAxisVectors(state);
                }
            }
            if (state.showForces) {
                // 重力/阻力只画箭头，不标数字，减少文字拥挤。
                drawVector(state.origin, state.gravityVec, state.accelScale, COLOR_GRAVITY,
                        ACCEL_WIDTH, null);
                drawVector(state.origin, state.dragVec, state.accelScale, COLOR_DRAG, ACCEL_WIDTH,
                        null);
            }
            if (state.showExternal) {
                drawVector(state.origin, state.impulseVec, state.velocityScale, COLOR_EXTERNAL,
                        VECTOR_WIDTH, state.momentum ? "外力动量" : "外力速度");
            }
            if (state.showResultant) {
                drawVector(state.origin, state.velocityVec, state.velocityScale, COLOR_RESULTANT,
                        VECTOR_WIDTH, state.momentum ? "合动量" : "合速度");
                drawVector(state.origin, state.accelVec, state.accelScale, COLOR_ACCEL,
                        ACCEL_WIDTH, "加速度");
            }
            drawAnglesAndInfo(state);
        }
    }

    /** 各轴夹角（X/Y/Z）、仰角、方位角，以及实体的俯仰/偏航与速度/动量数值。 */
    private static void drawAnglesAndInfo(Snapshot state) {
        Vec3 origin = state.origin;
        Vec3 value = state.velocityVec;
        // 悬停的珍珠实际速度为 0：此时用“待施加的外力矢量”来算夹角，让联动结果也能看到角度。
        if (value.lengthSqr() < 1.0E-6D && state.impulseVec.lengthSqr() > 1.0E-6D) {
            value = state.impulseVec;
        }
        double length = value.length();
        boolean showX = MotionVisualizerConfig.angleX();
        boolean showY = MotionVisualizerConfig.angleY();
        boolean showZ = MotionVisualizerConfig.angleZ();
        boolean showElevation = MotionVisualizerConfig.elevation();
        boolean showAzimuth = MotionVisualizerConfig.azimuth();

        if (length > 1.0E-6D && (showX || showY || showZ || showElevation || showAzimuth)) {
            Vec3 unit = value.scale(1.0D / length);
            if (showX) {
                drawArc(origin, new Vec3(1, 0, 0), unit,
                        "X " + degrees(Math.toDegrees(Math.acos(clamp(unit.x)))), COLOR_X);
            }
            if (showY) {
                drawArc(origin, new Vec3(0, 1, 0), unit,
                        "Y " + degrees(Math.toDegrees(Math.acos(clamp(unit.y)))), COLOR_Y);
            }
            if (showZ) {
                drawArc(origin, new Vec3(0, 0, 1), unit,
                        "Z " + degrees(Math.toDegrees(Math.acos(clamp(unit.z)))), COLOR_Z);
            }
            Vec3 horizontal = new Vec3(unit.x, 0.0D, unit.z);
            double horizontalLength = horizontal.length();
            if (showElevation) {
                Vec3 horizontalDir = horizontalLength > 1.0E-6D
                        ? horizontal.scale(1.0D / horizontalLength)
                        : new Vec3(1, 0, 0);
                drawArc(origin, horizontalDir, unit,
                        "仰角 " + degrees(Math.toDegrees(Math.asin(clamp(unit.y)))), COLOR_ELEVATION);
            }
            if (showAzimuth && horizontalLength > 1.0E-6D) {
                drawArc(origin, new Vec3(1, 0, 0), horizontal.scale(1.0D / horizontalLength),
                        "方位 " + degrees(Math.toDegrees(Math.atan2(unit.z, unit.x))), COLOR_Z);
            }
        }

        if (MotionVisualizerConfig.pitchYaw()) {
            Gizmos.billboardText(
                    String.format(Locale.ROOT, "俯仰 %.0f°  偏航 %.0f°", state.pitch, state.yaw),
                    origin.add(0.0D, 1.25D, 0.0D), TextGizmo.Style.forColor(COLOR_INFO));
            Gizmos.billboardText(
                    String.format(Locale.ROOT, "v=%.2f  p=%.2f", state.speed,
                            state.momentumMagnitude),
                    origin.add(0.0D, 1.5D, 0.0D), TextGizmo.Style.forColor(COLOR_INFO));
            if (state.impulseVec.lengthSqr() > 1.0E-6D) {
                Gizmos.billboardText(
                        String.format(Locale.ROOT, "外力=%.2f  (待施加)", state.impulseVec.length()),
                        origin.add(0.0D, 1.75D, 0.0D), TextGizmo.Style.forColor(COLOR_EXTERNAL));
            }
        }
    }

    /** 在世界里画一段“方向 a 到方向 b”的夹角圆弧，并在弧中点标注角度。 */
    private static void drawArc(Vec3 origin, Vec3 dirA, Vec3 dirB, String label, int color) {
        Vec3 a = dirA.normalize();
        Vec3 b = dirB.normalize();
        double dot = clamp(a.dot(b));
        double theta = Math.acos(dot);
        if (theta < 0.02D || theta > Math.PI - 0.02D) {
            return;
        }
        int segments = Math.max(4, (int) Math.ceil(theta / (Math.PI / 36.0D)));
        double sinTheta = Math.sin(theta);
        Vec3 prev = null;
        Vec3 mid = null;
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            Vec3 dir = a.scale(Math.sin((1.0D - t) * theta) / sinTheta)
                    .add(b.scale(Math.sin(t * theta) / sinTheta));
            Vec3 point = origin.add(dir.normalize().scale(ANGLE_RADIUS));
            if (prev != null) {
                Gizmos.line(prev, point, color, 1.5F);
            }
            if (i == segments / 2) {
                mid = point;
            }
            prev = point;
        }
        if (mid != null && label != null) {
            Gizmos.billboardText(label, mid, TextGizmo.Style.forColor(color));
        }
    }

    private static double clamp(double value) {
        return Math.max(-1.0D, Math.min(1.0D, value));
    }

    private static String degrees(double value) {
        return String.format(Locale.ROOT, "%.0f°", value);
    }

    /** X/Y/Z 坐标轴：正半轴带箭头、负半轴画一条较暗的线；末端标 X/Y/Z。 */
    private static void drawAxes(Vec3 origin) {
        Gizmos.arrow(origin, origin.add(AXIS_LENGTH, 0.0D, 0.0D), COLOR_X, AXIS_WIDTH);
        Gizmos.line(origin, origin.add(-AXIS_LENGTH, 0.0D, 0.0D), COLOR_AXIS_DIM, AXIS_WIDTH);
        Gizmos.arrow(origin, origin.add(0.0D, AXIS_LENGTH, 0.0D), COLOR_Y, AXIS_WIDTH);
        Gizmos.line(origin, origin.add(0.0D, -AXIS_LENGTH, 0.0D), COLOR_AXIS_DIM, AXIS_WIDTH);
        Gizmos.arrow(origin, origin.add(0.0D, 0.0D, AXIS_LENGTH), COLOR_Z, AXIS_WIDTH);
        Gizmos.line(origin, origin.add(0.0D, 0.0D, -AXIS_LENGTH), COLOR_AXIS_DIM, AXIS_WIDTH);
        Gizmos.billboardText("X", origin.add(AXIS_LENGTH + 0.25D, 0.0D, 0.0D),
                TextGizmo.Style.forColor(COLOR_X));
        Gizmos.billboardText("Y", origin.add(0.0D, AXIS_LENGTH + 0.25D, 0.0D),
                TextGizmo.Style.forColor(COLOR_Y));
        Gizmos.billboardText("Z", origin.add(0.0D, 0.0D, AXIS_LENGTH + 0.25D),
                TextGizmo.Style.forColor(COLOR_Z));
    }

    private static void drawAxisVectors(Snapshot state) {
        // 速度/动量分量标数字；加速度分量不标，避免文字拥挤。
        drawComponent(state.origin, state.velocityVec, state.velocityScale, new Vec3(1, 0, 0),
                COLOR_X, VECTOR_WIDTH, true);
        drawComponent(state.origin, state.velocityVec, state.velocityScale, new Vec3(0, 1, 0),
                COLOR_Y, VECTOR_WIDTH, true);
        drawComponent(state.origin, state.velocityVec, state.velocityScale, new Vec3(0, 0, 1),
                COLOR_Z, VECTOR_WIDTH, true);
        drawComponent(state.origin, state.accelVec, state.accelScale, new Vec3(1, 0, 0),
                COLOR_ACCEL, ACCEL_WIDTH, false);
        drawComponent(state.origin, state.accelVec, state.accelScale, new Vec3(0, 1, 0),
                COLOR_ACCEL, ACCEL_WIDTH, false);
        drawComponent(state.origin, state.accelVec, state.accelScale, new Vec3(0, 0, 1),
                COLOR_ACCEL, ACCEL_WIDTH, false);
    }

    /** 球极坐标：固定的参考球（三个正交大圆）+ 径向矢量 + 投影线，并标出 r、仰角、方位角。 */
    private static void drawSpherical(Snapshot state) {
        Vec3 origin = state.origin;
        Vec3 value = state.velocityVec;
        Vec3 displayed = value.scale(state.velocityScale);
        double length = displayed.length();
        Vec3 horizontal = new Vec3(displayed.x, 0.0D, displayed.z);
        double horizontalLength = horizontal.length();
        Vec3 horizontalDir = horizontalLength > 1.0E-6D
                ? horizontal.scale(1.0D / horizontalLength)
                : new Vec3(1.0D, 0.0D, 0.0D);
        double elevation = length > 1.0E-9D
                ? Math.toDegrees(Math.atan2(displayed.y, horizontalLength))
                : 0.0D;
        double azimuth = length > 1.0E-9D
                ? Math.toDegrees(Math.atan2(displayed.z, displayed.x))
                : 0.0D;

        // 参考球用固定半径 1，保证即使实体静止（速度为 0，例如悬停的珍珠）也能看到球极坐标轴。
        if (state.showAxes) {
            drawCircle(origin, 1.0D, new Vec3(1, 0, 0), new Vec3(0, 0, 1), COLOR_AXIS_DIM);
            drawCircle(origin, 1.0D, new Vec3(1, 0, 0), new Vec3(0, 1, 0), COLOR_AXIS_DIM);
            drawCircle(origin, 1.0D, new Vec3(0, 1, 0), new Vec3(0, 0, 1), COLOR_AXIS_DIM);
        }
        if (length > 1.0E-4D) {
            Vec3 tip = origin.add(displayed);
            Gizmos.arrow(origin, tip, COLOR_RESULTANT, VECTOR_WIDTH);
            // 水平投影与竖直投影线，直观表示仰角。
            Vec3 ground = origin.add(horizontalDir.scale(horizontalLength));
            Gizmos.line(origin, ground, COLOR_X, AXIS_WIDTH);
            Gizmos.line(ground, tip, COLOR_Y, AXIS_WIDTH);
            Gizmos.billboardText("r=" + format(value.length()), origin.add(displayed.scale(0.5D))
                    .add(0.0D, 0.25D, 0.0D), TextGizmo.Style.forColor(COLOR_RESULTANT));
            Gizmos.billboardText(String.format(Locale.ROOT, "仰角 %.0f°", elevation),
                    ground.add(0.0D, 0.25D, 0.0D), TextGizmo.Style.forColor(COLOR_Y));
            Gizmos.billboardText(String.format(Locale.ROOT, "方位 %.0f°", azimuth),
                    origin.add(0.25D, 0.15D, 0.0D), TextGizmo.Style.forColor(COLOR_X));
        }
    }

    /** 在世界里画一个圆：u、w 是圆所在平面的两个正交单位轴。 */
    private static void drawCircle(Vec3 origin, double radius, Vec3 u, Vec3 w, int color) {
        Vec3 prev = null;
        for (int i = 0; i <= CIRCLE_SEGMENTS; i++) {
            double angle = 2.0D * Math.PI * i / CIRCLE_SEGMENTS;
            Vec3 point = origin.add(u.scale(Math.cos(angle) * radius))
                    .add(w.scale(Math.sin(angle) * radius));
            if (prev != null) {
                Gizmos.line(prev, point, color, 1.5F);
            }
            prev = point;
        }
    }

    /** 画某一个轴上的分量：沿轴方向按分量大小伸出一支箭头，可选在末端标注数值。 */
    private static void drawComponent(Vec3 origin, Vec3 vector, double scale, Vec3 axis,
            int color, float width, boolean label) {
        double component = vector.dot(axis);
        if (Math.abs(component) < 1.0E-4D) {
            return;
        }
        Vec3 tip = origin.add(axis.scale(component * scale));
        Gizmos.arrow(origin, tip, color, width);
        if (label) {
            Gizmos.billboardText(format(component), tip.add(0.0D, 0.2D, 0.0D),
                    TextGizmo.Style.forColor(color));
        }
    }

    private static void drawVector(Vec3 origin, Vec3 vector, double scale, int color, float width,
            String label) {
        Vec3 scaled = vector.scale(scale);
        if (scaled.lengthSqr() < 1.0E-8D) {
            return;
        }
        Vec3 tip = origin.add(scaled);
        Gizmos.arrow(origin, tip, color, width);
        if (label != null) {
            Gizmos.billboardText(label + "=" + format(vector.length()), tip.add(0.0D, 0.25D, 0.0D),
                    TextGizmo.Style.forColor(color));
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** 只读渲染快照：tick 线程整体替换、渲染线程整体读取。 */
    private static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(null, null, null, null, null, null,
                1.0D, 1.0D, false, false, false, false, false, false, false,
                0.0D, 0.0D, 0.0F, 0.0F);

        private final Vec3 origin;
        private final Vec3 impulseVec;
        private final Vec3 velocityVec;
        private final Vec3 accelVec;
        private final Vec3 gravityVec;
        private final Vec3 dragVec;
        private final double velocityScale;
        private final double accelScale;
        private final boolean momentum;
        private final boolean spherical;
        private final boolean showAxes;
        private final boolean showAxisVectors;
        private final boolean showForces;
        private final boolean showExternal;
        private final boolean showResultant;
        private final double speed;
        private final double momentumMagnitude;
        private final float pitch;
        private final float yaw;

        private Snapshot(Vec3 origin, Vec3 impulseVec, Vec3 velocityVec, Vec3 accelVec,
                Vec3 gravityVec, Vec3 dragVec, double velocityScale, double accelScale,
                boolean momentum, boolean spherical, boolean showAxes, boolean showAxisVectors,
                boolean showForces, boolean showExternal, boolean showResultant,
                double speed, double momentumMagnitude, float pitch, float yaw) {
            this.origin = origin;
            this.impulseVec = impulseVec;
            this.velocityVec = velocityVec;
            this.accelVec = accelVec;
            this.gravityVec = gravityVec;
            this.dragVec = dragVec;
            this.velocityScale = velocityScale;
            this.accelScale = accelScale;
            this.momentum = momentum;
            this.spherical = spherical;
            this.showAxes = showAxes;
            this.showAxisVectors = showAxisVectors;
            this.showForces = showForces;
            this.showExternal = showExternal;
            this.showResultant = showResultant;
            this.speed = speed;
            this.momentumMagnitude = momentumMagnitude;
            this.pitch = pitch;
            this.yaw = yaw;
        }
    }
}
