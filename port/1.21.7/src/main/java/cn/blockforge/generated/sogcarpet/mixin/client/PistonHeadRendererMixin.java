package cn.blockforge.generated.sogcarpet.mixin.client;

import cn.blockforge.generated.sogcarpet.client.TransparentVertexConsumer;
import cn.blockforge.generated.sogcarpet.client.VisualizerState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.PistonHeadRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * “b36 目的地预览”的客户端实现（移植自 lucidity 的 {@code MovingBlockRenderMixin}）。
 *
 * <p>活塞推动方块时，原版会在 {@link PistonHeadRenderer#render} 里把移动中的方块按它本身的
 * 模型画一遍（位置随进度插值）。这里在同一个方法的末尾（TAIL）再画一份：仍然用该方块的模型，
 * 但把 {@link MultiBufferSource} 包成 {@link TransparentVertexConsumer}——它把所有顶点的
 * alpha 压成 {@value TransparentVertexConsumer#ALPHA}，于是这份“重影”是半透明的。</p>
 *
 * <p><b>1.21.5 ~ 1.21.8 的签名</b>：这几个版本给 {@code PistonHeadRenderer#render} 末尾加了
 * 一个相机位置的 {@code Vec3} 参数（{@code (PistonMovingBlockEntity, float, PoseStack,
 * MultiBufferSource, int, int, Vec3)}）。本 mixin 之前只写了 6 个参数的老签名，
 * 结果是**编译期 remap 报 "Cannot remap render(...)"、运行期 mixin apply 直接失败**——
 * 而 {@code PistonHeadRenderer} 在客户端启动阶段就会被 {@code BlockEntityRenderers} 加载，
 * 于是玩家看到的是“加了模组客户端黑屏/崩在启动”。这里按真实签名取到那个 {@code Vec3}，
 * 顺便省掉一次 {@code gameRenderer.getMainCamera()} 调用。</p>
 *
 * <p><b>为什么 {@code require = 0}</b>：这段只是“提前看到方块会停在哪一格”的锦上添花，
 * 不值得为它把整个客户端拖下水。以后原版再改这个方法的签名，最差的结果是这条预览消失
 * （日志里会有一条提示），而不是启动黑屏。</p>
 *
 * <p><b>坐标</b>：移动方块实体本身就在<b>目的地</b>（{@code PistonBaseBlock#moveBlocks}
 * 里新建实体时用的就是 {@code pos.relative(pushDirection)}），原版在 render 内部用
 * {@code progress} 把模型往起点方向推回去；TAIL 时那些位移已经 pop 干净，所以这里只要把姿态
 * 平移到实体所在方块（= 目的地）就能画在正确的格子上，不能再叠加 progress。</p>
 */
@Mixin(PistonHeadRenderer.class)
public class PistonHeadRendererMixin {
    /** 本可视化在总控里的规则名。 */
    private static final String RULE = "b36TargetPreview";

    @Inject(method = "render(Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;II"
            + "Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("TAIL"), require = 0)
    private void sogcarpet$b36Preview(PistonMovingBlockEntity moving, float tickDelta,
            PoseStack poseStack, MultiBufferSource buffers, int light, int overlay,
            Vec3 cameraPosition, CallbackInfo ci) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        BlockState moved = moving.getMovedState();
        if (moved == null || moved.isAir()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null || client.gameRenderer == null) {
            return;
        }
        BlockPos pos = moving.getBlockPos();
        Vec3 camera = cameraPosition;
        if (camera == null) {
            // 1.21.5 起原版会把相机位置直接传进来；拿不到时退回旧写法。
            camera = client.gameRenderer.getMainCamera().getPosition();
        }
        if (camera == null) {
            return;
        }
        // 半透明的那一份：所有顶点 alpha 固定成 100。
        MultiBufferSource ghost = type -> new TransparentVertexConsumer(buffers.getBuffer(type));
        poseStack.pushPose();
        try {
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            client.getBlockRenderer().renderSingleBlock(moved, poseStack, ghost, light, overlay);
        } finally {
            // 原版共享的 PoseStack：必须恢复。
            poseStack.popPose();
        }
    }
}
