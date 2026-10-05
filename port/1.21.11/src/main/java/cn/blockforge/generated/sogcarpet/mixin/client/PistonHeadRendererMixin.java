package cn.blockforge.generated.sogcarpet.mixin.client;

import cn.blockforge.generated.sogcarpet.client.DeferredGeometry;
import cn.blockforge.generated.sogcarpet.client.TransparentVertexConsumer;
import cn.blockforge.generated.sogcarpet.client.VisualizerState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.blockentity.PistonHeadRenderer;
import net.minecraft.client.renderer.blockentity.state.PistonHeadRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * “b36 目的地预览”的客户端实现（移植自 lucidity 的 {@code MovingBlockRenderMixin}）。
 *
 * <p>活塞推动方块时，原版会在 {@link PistonHeadRenderer#submit} 里把移动中的方块按
 * 本身模型画一遍。这里在同一个方法末尾（TAIL）再提交一份<b>自定义几何</b>：仍然用
 * 该方块的 {@link BlockStateModel} 走方块渲染器逐面细分，但把结果写进
 * {@link TransparentVertexConsumer}——它把所有顶点的 alpha 压成
 * {@value TransparentVertexConsumer#ALPHA}，于是这份“重影”是半透明的，能提前看到
 * 方块会停在哪一格。几何通过 {@link DeferredGeometry} 按
 * {@link RenderTypes#translucentMovingBlock()} 提交，和原版移动方块用同一套渲染类型。</p>
 *
 * <p><b>关键：这里不能再叠加位移。</b>原版 {@code submit} 把
 * {@code (xOffset, yOffset, zOffset)} 的位移 push/pop 得很干净，TAIL 时姿态已经回到
 * 移动方块实体所在的那一格，而移动方块实体恰恰被放在方块的<b>目的地</b>
 * （{@code PistonBaseBlock.moveBlocks} 里 {@code newMovingBlockEntity(pos.relative(pushDirection), ...)}）。
 * 所以直接提交几何，重影就落在“将要到达”的格子；本体仍由原版画在路上的插值位置，
 * 两者错开才看得见。</p>
 */
@Mixin(PistonHeadRenderer.class)
public abstract class PistonHeadRendererMixin {
    /** 本可视化在总控里的规则名。 */
    private static final String RULE = "b36TargetPreview";

    @Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/PistonHeadRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("TAIL"))
    private void sogcarpet$b36Preview(PistonHeadRenderState state, PoseStack poseStack,
            SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        MovingBlockRenderState moved = state.block;
        if (moved == null || moved.blockState == null || moved.blockState.isAir()) {
            return;
        }
        // 与 lucidity 一致：TAIL 时姿态就在移动方块实体的格子上（＝b36 的目的地），
        // 不能再叠加 state 的 x/y/zOffset，否则重影会贴在本体上而完全看不见。
        DeferredGeometry.submit(collector, poseStack, RenderTypes.translucentMovingBlock(),
                (local, consumer) -> drawPreview(local, consumer, moved));
    }

    /** 用移动方块自己的模型与群系着色把几何画进透明消费者。 */
    private static void drawPreview(PoseStack poseStack, VertexConsumer consumer,
            MovingBlockRenderState moved) {
        Minecraft client = Minecraft.getInstance();
        BlockState blockState = moved.blockState;
        BlockStateModel model = client.getModelManager().getBlockModelShaper().getBlockModel(blockState);
        long seed = blockState.getSeed(moved.randomSeedPos);
        List<BlockModelPart> parts = model.collectParts(RandomSource.create(seed));
        VertexConsumer transparent = new TransparentVertexConsumer(consumer);
        client.getBlockRenderer().renderBatched(blockState, moved.blockPos, moved, poseStack,
                transparent, false, parts);
    }
}
