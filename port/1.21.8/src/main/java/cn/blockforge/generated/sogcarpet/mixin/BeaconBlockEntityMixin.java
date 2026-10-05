package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogBeaconEffectHolder;
import cn.blockforge.generated.sogcarpet.SogBeaconEffects;
import cn.blockforge.generated.sogcarpet.SogSettings;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 信标附加效果的接线。
 *
 * <p>原版的效果表与白名单都是 {@code static final}，这里用 {@code @Mutable @Shadow}
 * 在 {@code <clinit>} 末尾把它们替换掉：</p>
 * <ul>
 *   <li>{@code BEACON_EFFECTS} 换成规则感知视图——界面与层级校验因此自动跟随规则，
 *       不需要再改信标界面；</li>
 *   <li>{@code VALID_EFFECTS} 换成包含附加效果的完整集合，保证读档时不丢效果。</li>
 * </ul>
 *
 * <p>规则关闭时，已经选过附加效果的信标要“变回无附加效果”。这一步在 tick 头部做：
 * 只清空附加效果本身，不动信标层数、也不动原版效果，因此不会影响原版玩法。</p>
 */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityMixin implements SogBeaconEffectHolder {
    @Mutable
    @Shadow
    @Final
    public static List<List<Holder<MobEffect>>> BEACON_EFFECTS;

    @Mutable
    @Shadow
    @Final
    private static Set<Holder<MobEffect>> VALID_EFFECTS;

    @Shadow
    private Holder<MobEffect> primaryPower;

    @Shadow
    private Holder<MobEffect> secondaryPower;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void sog$installBeaconEffects(CallbackInfo ci) {
        BEACON_EFFECTS = SogBeaconEffects.beaconEffects();
        VALID_EFFECTS = SogBeaconEffects.allValidEffects();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private static void sog$clearExtraEffects(Level level, BlockPos pos, BlockState state,
            BeaconBlockEntity beacon, CallbackInfo ci) {
        if (level.isClientSide() || SogSettings.beaconExtraEffects) {
            return;
        }
        SogBeaconEffectHolder holder = (SogBeaconEffectHolder) beacon;
        boolean changed = false;
        if (SogBeaconEffects.isExtra(holder.sog$getPrimaryEffect())) {
            holder.sog$setPrimaryEffect(null);
            changed = true;
        }
        if (SogBeaconEffects.isExtra(holder.sog$getSecondaryEffect())) {
            holder.sog$setSecondaryEffect(null);
            changed = true;
        }
        if (changed) {
            holder.sog$markChanged();
        }
    }

    @Override
    public Holder<MobEffect> sog$getPrimaryEffect() {
        return this.primaryPower;
    }

    @Override
    public Holder<MobEffect> sog$getSecondaryEffect() {
        return this.secondaryPower;
    }

    @Override
    public void sog$setPrimaryEffect(Holder<MobEffect> effect) {
        this.primaryPower = effect;
    }

    @Override
    public void sog$setSecondaryEffect(Holder<MobEffect> effect) {
        this.secondaryPower = effect;
    }

    @Override
    public void sog$markChanged() {
        ((BeaconBlockEntity) (Object) this).setChanged();
    }
}
