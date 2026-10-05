package cn.blockforge.generated.sogcarpet.mixin.client;

import cn.blockforge.generated.sogcarpet.client.TransparentVertexConsumer;
import cn.blockforge.generated.sogcarpet.client.VisualizerState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.PistonHeadRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * “b36 目的地预览”的客户端实现（移植自 lucidity 的 {@code MovingBlockRenderMixin}），
 * 适配 1.21.4 及更早的即时渲染管线（{@code PistonHeadRenderer#render} 直接拿
 * {@link MultiBufferSource}，没有提交节点收集器，也没有相机位置参数）。
 *
 * <p>在 {@code render} 末尾（TAIL，此时姿态已复位）把移动方块按本身模型再画一份，
 * 顶点颜色 alpha 统一压成 {@value TransparentVertexConsumer#ALPHA}，于是重影落在移动
 * 方块实体所在的格子（也就是活塞的目的地），能提前看到方块会停在哪。</p>
 */
@Mixin(PistonHeadRenderer.class)
public abstract class PistonHeadRendererMixin {
    /** 本可视化在总控里的规则名。 */
    private static final String RULE = "b36TargetPreview";

    @Inject(method = "render(Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;"
            + "FLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "II)V",
            at = @At("TAIL"))
    private void sogcarpet$b36Preview(PistonMovingBlockEntity blockEntity, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay,
            CallbackInfo ci) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        BlockState blockState = blockEntity.getMovedState();
        if (blockState == null || blockState.isAir()) {
            return;
        }
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        Minecraft client = Minecraft.getInstance();
        Vec3 cameraPos = client.gameRenderer.getMainCamera().getPosition();
        poseStack.pushPose();
        try {
            poseStack.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y,
                    pos.getZ() - cameraPos.z);
            VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucentMovingBlock());
            VertexConsumer transparent = new TransparentVertexConsumer(consumer);
            client.getBlockRenderer().renderBatched(blockState, pos, level, poseStack, transparent,
                    false, RandomSource.create(blockState.getSeed(pos)));
        } finally {
            poseStack.popPose();
        }
    }
}
