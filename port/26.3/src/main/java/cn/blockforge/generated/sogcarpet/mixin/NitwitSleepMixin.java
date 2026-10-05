package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * “禁止傻子村民占用床”的最后一道闸：傻子永远不会上床。
 *
 * <p>村民躺下只有一条路——{@code SleepInBed} 行为调用
 * {@link LivingEntity#startSleeping(BlockPos)}，由它设置 {@code Pose.SLEEPING}、
 * 把实体挪到床中心，并把床方块的 {@code OCCUPIED} 设成 true。前两道处理
 * （{@link NitwitBrainMixin} 拿到 HOME 就清理、{@link NitwitVillagerMixin} 每 tick
 * 扫一次）已经让傻子基本拿不到 HOME；这里直接掐住入口，任何残余路径（存档里
 * 写死的坐标、其他 mod 写 HOME、规则中途开启等）都无法让傻子躺下，也就不会有
 * “上床之后立刻下床”的反复。</p>
 */
@Mixin(LivingEntity.class)
public abstract class NitwitSleepMixin {
    @Inject(method = "startSleeping", at = @At("HEAD"), cancellable = true)
    private void sog$denyNitwitSleep(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!SogSettings.nitwitBedDisabled) {
            return;
        }
        if ((Object) this instanceof Villager villager
                && villager.getVillagerData().profession().is(VillagerProfession.NITWIT)) {
            // 26.3 起 startSleeping 返回 boolean：拒绝入睡时返回 false，原版不会把它挪上床。
            cir.setReturnValue(false);
        }
    }
}
