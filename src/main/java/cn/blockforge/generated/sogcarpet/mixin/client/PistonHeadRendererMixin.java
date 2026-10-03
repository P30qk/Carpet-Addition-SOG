package cn.blockforge.generated.sogcarpet.mixin.client;

import cn.blockforge.generated.sogcarpet.client.DeferredGeometry;
import cn.blockforge.generated.sogcarpet.client.TransparentVertexConsumer;
import cn.blockforge.generated.sogcarpet.client.VisualizerState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.blockentity.PistonHeadRenderer;
import net.minecraft.client.renderer.blockentity.state.PistonHeadRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
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
 * 该方块的 {@link BlockStateModel} 走 {@link ModelBlockRenderer} 逐面细分，但把结果
 * 写进 {@link TransparentVertexConsumer}——它把所有顶点的 alpha 压成
 * {@value TransparentVertexConsumer#ALPHA}，于是这份“重影”是半透明的，能提前看到
 * 方块会停在哪一格。几何通过 {@link DeferredGeometry} 按
 * {@link RenderTypes#translucentMovingBlock()} 提交，和原版移动方块用同一套渲染类型。</p>
 *
 * <p><b>关键：这里不能再叠加位移。</b>原版 {@code submit} 把
 * {@code (xOffset, yOffset, zOffset)} 的位移 push/pop 得很干净，TAIL 时姿态已经回到
 * 移动方块实体所在的那一格，而移动方块实体恰恰被放在方块的<b>目的地</b>
 * （{@code PistonBaseBlock.moveBlocks} 里 {@code newMovingBlockEntity(pos.relative(pushDirection), ...)}）。
 * 所以直接提交几何，重影就落在“将要到达”的格子；本体仍由原版画在路上的插值位置，
 * 两者错开才看得见。若在这里再叠一次原版位移，重影会与本体完全重合，
 * 同材质、同光照、同 alpha 叠加等于什么都没画——这正是之前“B36 看不出来”的原因，
 * 也是与 lucidity 原实现唯一的差别。</p>
 *
 * <p>只预览 {@code state.block}（被推动的那一块），不额外处理 {@code state.base}；
 * 是否绘制完全由可视化规则 {@code b36TargetPreview}（连同 Ctrl+O 总开关、Ctrl+V 里的
 * 本地渲染开关）决定，其余写法与 lucidity 的 {@code MovingBlockRenderMixin} 保持一致。</p>
 */
@Mixin(PistonHeadRenderer.class)
public abstract class PistonHeadRendererMixin {
    /** 本可视化在总控里的规则名。 */
    private static final String RULE = "b36TargetPreview";

    @Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/PistonHeadRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
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

    /** 用移动方块自己的模型、光照与群系着色调出几何，逐 quad 交给透明消费者。 */
    private static void drawPreview(PoseStack poseStack, VertexConsumer consumer,
            MovingBlockRenderState moved) {
        Minecraft client = Minecraft.getInstance();
        BlockState blockState = moved.blockState;
        BlockStateModel model = client.getModelManager().getBlockStateModelSet().get(blockState);
        boolean ambientOcclusion = Boolean.TRUE.equals(client.options.ambientOcclusion().get());
        ModelBlockRenderer renderer = new ModelBlockRenderer(ambientOcclusion, false,
                client.getBlockColors());
        VertexConsumer transparent = new TransparentVertexConsumer(consumer);
        BlockQuadOutput output = (x, y, z, quad, instance) -> {
            poseStack.pushPose();
            try {
                poseStack.translate(x, y, z);
                transparent.putBakedQuad(poseStack.last(), quad, instance);
            } finally {
                poseStack.popPose();
            }
        };
        long seed = blockState.getSeed(moved.randomSeedPos);
        renderer.tesselateBlock(output, 0.0F, 0.0F, 0.0F, moved, moved.blockPos, blockState, model,
                seed);
    }
}
