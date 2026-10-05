package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 禁鸡骑士的唯一收口点。
 *
 * <p>僵尸（含幼年僵尸，以及同样继承 {@link Zombie} 的溺尸 {@code Drowned}、
 * 尸壳 {@code Husk}、僵尸村民 {@code ZombieVillager}、僵尸猪灵 {@code ZombifiedPiglin}）
 * 的鸡骑士只在 {@code Zombie#finalizeSpawn} 里形成：后四个子类虽然各自重写了
 * {@code finalizeSpawn}，但都先 {@code super.finalizeSpawn(...)} 调回这里，
 * 所以只改这一个方法就能覆盖全部五种幼年变种。过了 {@code canSpawnJockey} 门后，
 * “骑现成的鸡”和“刷一只新鸡”两条分支各自掷一次 {@code nextFloat() < 0.05d}。
 * 26.2 字节码里整个 {@code Zombie#finalizeSpawn} 恰好只有这两处 {@code 0.05d} 常量
 * （另一处 {@code 0.05d} 在 {@code killedEntity} 的增援充能里，不在本方法，不会被误伤），
 * 规则开启时把它们压成 -1.0，两条分支同时失效，既不拆已有鸡骑士，
 * 也不碰 0.1f 破门、0.55f 捡装备等其它判定。</p>
 *
 * <p>不用 {@code @Redirect} 拦 {@code ZombieGroupData#canSpawnJockey} 的读取，
 * 是为绕开 mixinextras 0.5.4 对 26.2 工具链数组形态 {@code @Redirect.at} 的兼容崩溃
 * （详见 {@link ServerLevelMixin} 注释）。</p>
 */
@Mixin(Zombie.class)
public abstract class ZombieMixin {
    @ModifyConstant(method = "finalizeSpawn", constant = {
            @Constant(doubleValue = 0.05D),
            @Constant(doubleValue = 0.05D)
    })
    private double sogcarpet$disableChickenJockey(double jockeyRollCap) {
        if (SogSettings.chickenJockeyDisabled) {
            return -1.0D;
        }
        return jockeyRollCap;
    }
}
