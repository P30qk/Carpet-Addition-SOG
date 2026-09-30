package cn.blockforge.generated.sogcarpet.mixin.client;

import cn.blockforge.generated.sogcarpet.client.FlexiblePlacementCorners;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * “Tweakeroo 灵活放置四角三角”规则的服务端无关部分：只改 Tweakeroo 算偏移位置的那一步。
 *
 * <p>Tweakeroo {@code PlacementTweaks.tryPlaceBlock} 里“偏移位置”分支只有一次
 * {@link BlockPos#relative}（整段字节码里 {@code relative(Direction)} 一共出现三次：
 * 前两次是“相邻位置”分支的连续两步，第三次就是偏移分支），以及紧跟其后的一次
 * {@code Vec3.add}（全方法只有两次 {@code Vec3.add(Vec3)}：第一次在“相邻位置”分支，
 * 第二次就是偏移分支平移命中点）。这两处分别重定向到
 * {@link FlexiblePlacementCorners#adjustOffset} 与
 * {@link FlexiblePlacementCorners#adjustHitVec}：瞄准四个角时它们返回双轴结果，其余情况
 * 原样返回 Tweakeroo 自己算的值。</p>
 *
 * <p>目标类用字符串写，编译期不依赖 Tweakeroo；配置 {@code sog_carpet.tweakeroo.mixins.json}
 * 是 required=false / defaultRequire=0，没装 Tweakeroo 或它改了实现时静默跳过，不影响本模组
 * 其余功能。</p>
 */
@Mixin(targets = "fi.dy.masa.tweakeroo.tweaks.PlacementTweaks")
public abstract class FlexiblePlacementOffsetMixin {
    @Redirect(
            method = "tryPlaceBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)"
                            + "Lnet/minecraft/core/BlockPos;",
                    ordinal = 2),
            require = 0)
    private static BlockPos sog$flexibleCornerOffset(BlockPos pos, Direction dir) {
        return FlexiblePlacementCorners.adjustOffset(pos, dir);
    }

    @Redirect(
            method = "tryPlaceBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;add(Lnet/minecraft/world/phys/Vec3;)"
                            + "Lnet/minecraft/world/phys/Vec3;",
                    ordinal = 1),
            require = 0)
    private static Vec3 sog$flexibleCornerHitVec(Vec3 hitVec, Vec3 offset) {
        return FlexiblePlacementCorners.adjustHitVec(hitVec, offset);
    }
}
