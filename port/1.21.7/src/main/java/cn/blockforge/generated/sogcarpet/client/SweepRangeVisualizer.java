package cn.blockforge.generated.sogcarpet.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import cn.blockforge.generated.sogcarpet.client.gizmo.GizmoStyle;
import cn.blockforge.generated.sogcarpet.client.gizmo.Gizmos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 横扫攻击范围可视化（移植自 sweeping-view）。
 *
 * <p>对每一个手持剑并瞄准了某个实体的玩家，画出原版 {@code Player#doSweepAttack} 真正使用的
 * 两个判定范围：</p>
 * <ul>
 *   <li>目标碰撞箱水平外扩 {@value #SWEEP_HORIZONTAL} 格、竖直外扩 {@value #SWEEP_VERTICAL} 格
 *       的方框（线框 + 半透明面）；</li>
 *   <li>原版第二重限制 {@code this.distanceToSqr(nearby) < 9.0} 的等价体——以攻击者
 *       <b>脚底</b>为球心、半径 {@value #SWEEP_RADIUS} 格的球体。</li>
 * </ul>
 *
 * <p>渲染方式与原模组完全一致：仍然走原版的 gizmo 管线，只是提交的是自定义的
 * {@link CuboidGizmo}／{@link SphereGizmo}（从内部/外部都可见）。完全在客户端逐帧计算与绘制，
 * 服务端零负担；由 {@code sweepRangeVisualizer} 规则与 Ctrl+O 总开关控制。</p>
 */
public final class SweepRangeVisualizer {
    /** 本规则在总控里的名字。 */
    private static final String RULE = "sweepRangeVisualizer";

    /** Horizontal / vertical inflation applied to the attacked entity's bounding box. */
    private static final double SWEEP_HORIZONTAL = 1.0;
    private static final double SWEEP_VERTICAL = 0.25;
    /** Radius (in blocks) of the reach sphere around the attacker. */
    private static final double SWEEP_RADIUS = 3.0;

    private static final float STROKE_WIDTH = 2.5F;

    /** Players further away than this (squared) from the camera are not considered. */
    private static final double MAX_RENDER_DISTANCE_SQ = 96.0 * 96.0;

    private SweepRangeVisualizer() {
    }

    /** 客户端入口调用一次：注册渲染回调。 */
    public static void init() {
        // 1.21.3 没有单独的“提取阶段”事件（26.x 的 LevelExtractionEvents），但本可视化只提交
        // gizmo 图元，和别的可视化一样放在调试渲染之前即可，画面与时机等价。
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(SweepRangeVisualizer::render);
    }

    public static void render(final WorldRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        ClientLevel level = context.world();
        // 全景截图时相机是分离状态（isDetached），此时不画。
        if (level == null || context.camera() == null || context.camera().isDetached()) {
            return;
        }
        try {
            renderSweepRanges(level, context);
        } catch (IllegalStateException e) {
            // 原版某些离屏渲染路径下收集器还没起：跳过这一帧。
        }
    }

    private static void renderSweepRanges(final ClientLevel level, final WorldRenderContext context) {
        float partialTicks = context.tickCounter().getGameTimeDeltaPartialTick(false);
        Vec3 cameraPos = context.camera().getPosition();

        boolean boxStrokeEnabled = VisualizerConfig.option(RULE, "boxStrokeEnabled", true);
        boolean boxFillEnabled = VisualizerConfig.option(RULE, "boxFillEnabled", true);
        boolean sphereEnabled = VisualizerConfig.option(RULE, "sphereEnabled", true);
        int boxStrokeColor = VisualizerConfig.color(RULE, "boxStroke", 0xFFFF5050);
        int boxFillColor = VisualizerConfig.color(RULE, "boxFill", 0x40FF5050);
        int sphereColor = VisualizerConfig.color(RULE, "sphere", 0x40FF5050);

        // 模式二“总是渲染”：不要求持剑/瞄准，本地玩家自身的 3 格触及球常显，
        // 准星指向实体时再画它的外扩方框。
        if (VisualizerConfig.option(RULE, "alwaysRender", false)) {
            Player self = net.minecraft.client.Minecraft.getInstance().player;
            if (self == null || !self.isAlive() || self.isSpectator()) {
                return;
            }
            Entity target = SweepTargeting.findPointedEntity(self, partialTicks);
            if (target == self) {
                target = null;
            }
            drawBox(target, partialTicks, boxStrokeEnabled, boxFillEnabled, boxStrokeColor, boxFillColor);
            if (sphereEnabled) {
                Gizmos.addGizmo(new SphereGizmo(self.getPosition(partialTicks), (float) SWEEP_RADIUS,
                        sphereColor));
            }
            return;
        }

        for (Player player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }

            if (player.position().distanceToSqr(cameraPos) > MAX_RENDER_DISTANCE_SQ) {
                continue;
            }

            // A sweep attack can only happen while a sword is held in the main hand.
            if (!player.getItemInHand(InteractionHand.MAIN_HAND).is(ItemTags.SWORDS)) {
                continue;
            }

            Entity target = SweepTargeting.findPointedEntity(player, partialTicks);

            if (target == null || target == player) {
                continue;
            }

            drawBox(target, partialTicks, boxStrokeEnabled, boxFillEnabled, boxStrokeColor, boxFillColor);

            // The 3 block reach limit is measured from the attacker's feet position, so the sphere is
            // half buried in the ground. Unlike vanilla's always-on-top gizmos it is left on the
            // regular depth test, so terrain occludes it the same way it occludes the sweep box.
            if (sphereEnabled) {
                Gizmos.addGizmo(new SphereGizmo(player.getPosition(partialTicks), (float) SWEEP_RADIUS, sphereColor));
            }
        }
    }

    /** 画目标实体碰撞箱外扩后的横扫方框（目标为 null 时不画）。 */
    private static void drawBox(Entity target, float partialTicks, boolean boxStrokeEnabled,
            boolean boxFillEnabled, int boxStrokeColor, int boxFillColor) {
        if (target == null || (!boxStrokeEnabled && !boxFillEnabled)) {
            return;
        }
        // Interpolate the target so the box follows it smoothly between ticks. Unlike vanilla's
        // cuboid gizmo this one keeps its faces when the camera moves inside the box.
        Vec3 targetOffset = target.getPosition(partialTicks).subtract(target.position());
        AABB sweepBox = target.getBoundingBox().move(targetOffset)
                .inflate(SWEEP_HORIZONTAL, SWEEP_VERTICAL, SWEEP_HORIZONTAL);
        // GizmoStyle reads a zero colour as "absent", so a half that is switched off just passes 0.
        GizmoStyle style = GizmoStyle.strokeAndFill(
                boxStrokeEnabled ? boxStrokeColor : 0,
                STROKE_WIDTH,
                boxFillEnabled ? boxFillColor : 0);
        Gizmos.addGizmo(new CuboidGizmo(sweepBox, style));
    }
}
