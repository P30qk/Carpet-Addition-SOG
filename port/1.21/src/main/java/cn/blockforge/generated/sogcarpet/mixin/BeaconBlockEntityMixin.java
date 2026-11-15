package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogBeaconEffectHolder;
import cn.blockforge.generated.sogcarpet.SogBeaconFilter;
import cn.blockforge.generated.sogcarpet.SogBeaconHelper;
import cn.blockforge.generated.sogcarpet.SogSettings;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「自定义信标效果」的服务端接线（替换掉旧的“信标附加效果”）。
 *
 * <p>四件事：</p>
 * <ol>
 *   <li><b>读档白名单</b>：把 {@code VALID_EFFECTS} 换成规则感知的集合，规则开启时任何非空效果
 *       都能从 NBT 里读回来，不会被 {@code filterEffect} 丢掉；</li>
 *   <li><b>效果校验</b>：1.21.11 没有 26.2 的服务端 {@code validateEffects}，校验由
 *       客户端信标界面（本模组的 {@code BeaconScreenMixin}）负责；</li>
 *   <li><b>下界之星加一级</b>：用下界之星开出的信标会在 {@link #sog$readStarFlag} 里记下
 *       “这次生效要 +1 级”，随后 {@code applyEffects} 里两处 {@code MobEffectInstance} 的
 *       amplifier 参数统一 +1，持续时长仍按原版公式；</li>
 *   <li><b>规则关闭即失效</b>：关闭规则后，已经选了自定义效果（不在原版表里）的信标会被清空
 *       主/副效果并标脏，等于原版“没有效果的信号塔”。</li>
 * </ol>
 *
 * <p>下界之星标记用 NBT 持久化（{@link #TAG_NETHER_STAR}），因为它必须跨重登/重启保留。</p>
 */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityMixin implements SogBeaconEffectHolder {
    /** “本次效果由下界之星开启”的 NBT 键。 */
    @Unique
    private static final String TAG_NETHER_STAR = "SogNetherStarPaid";

    /**
     * 当前这次 {@code applyEffects} 要不要 +1 级。{@code applyEffects} 是静态方法，拿不到实例，
     * 所以在 HEAD 里按方块坐标查出信标、把标记放进这个字段，RETURN 时清掉。服务端 tick 是单线程，
     * 不存在交叉。
     */
    @Unique
    private static boolean sog$starBoostThisApply;

    @Mutable
    @Shadow
    @Final
    private static Set<Holder<MobEffect>> VALID_EFFECTS;

    @Shadow
    private Holder<MobEffect> primaryPower;

    @Shadow
    private Holder<MobEffect> secondaryPower;

    @Unique
    private boolean sog$netherStarPaid;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void sog$installValidEffects(CallbackInfo ci) {
        VALID_EFFECTS = SogBeaconHelper.validEffects();
    }

    /** 规则开启时接管校验；规则关闭时返回 null、原逻辑继续跑。 */
    /*
     * 1.21.11 还没有 26.2 引入的 BeaconBlockEntity#validateEffects：这个版本的效果校验仍在
     * 客户端信标界面（BeaconScreen）里做，服务端只按界面选中的效果写进 beaconData。
     * 因此这里不做服务端校验注入，自定义效果由「界面注入 + VALID_EFFECTS 白名单 + tick 清理」
     * 三者共同保证：界面允许选、读档不丢、规则关闭或名单变更时立刻失效。
     */

    /** 记下这次生效是否需要下界之星的 +1 级。 */
    @Inject(method = "applyEffects", at = @At("HEAD"))
    private static void sog$readStarFlag(Level level, BlockPos pos, int levels,
            Holder<MobEffect> primary, Holder<MobEffect> secondary, CallbackInfo ci) {
        sog$starBoostThisApply = SogSettings.customBeaconEffects
                && level.getBlockEntity(pos) instanceof SogBeaconEffectHolder holder
                && holder.sog$isNetherStarPaid();
    }

    @Inject(method = "applyEffects", at = @At("RETURN"))
    private static void sog$clearStarFlag(Level level, BlockPos pos, int levels,
            Holder<MobEffect> primary, Holder<MobEffect> secondary, CallbackInfo ci) {
        sog$starBoostThisApply = false;
    }

    /**
     * 下界之星等级 +1：把 {@code applyEffects} 里两处 {@code new MobEffectInstance(...)} 的结果
     * 换成“等级高一级”的新实例。两处都拦，于是主效果与副效果都会提升一级；持续时间、是否环境
     * 粒子、是否显示图标全部照抄原实例，只有 amplifier 变化。
     *
     * <p>用 MixinExtras 的 {@code @ModifyExpressionValue} 而不是 {@code @ModifyArg}：后者在
     * 26.2 的 Mixin 里不支持直接改 {@code NEW} 指令的构造参数。</p>
     */
    @ModifyExpressionValue(method = "applyEffects",
            at = @At(value = "NEW", target = "net/minecraft/world/effect/MobEffectInstance"))
    private static MobEffectInstance sog$boostAmplifierWithNetherStar(MobEffectInstance original) {
        if (!sog$starBoostThisApply || original == null) {
            return original;
        }
        return new MobEffectInstance(original.getEffect(), original.getDuration(),
                original.getAmplifier() + 1, original.isAmbient(), original.isVisible(),
                original.showIcon());
    }

    /**
     * 每次 tick 把信标里“现在不该生效”的效果清掉：
     *
     * <ul>
     *   <li><b>规则关闭</b>：清掉不在原版白名单里的效果（用了自定义效果或下界之星的信标失效），
     *       变回原版的空信标；</li>
     *   <li><b>规则开启</b>：黑白名单可能在信标开好之后才被 Op 改掉，于是被禁用的主/副效果
     *       在这里立刻失效（清空并标脏），不会继续给玩家上 buff。</li>
     * </ul>
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private static void sog$enforceBeaconEffects(Level level, BlockPos pos,
            BlockState state, BeaconBlockEntity beacon, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        SogBeaconEffectHolder holder = (SogBeaconEffectHolder) beacon;
        boolean changed = false;
        Holder<MobEffect> primary = holder.sog$getPrimaryEffect();
        Holder<MobEffect> secondary = holder.sog$getSecondaryEffect();
        if (SogSettings.customBeaconEffects) {
            // 名单变了：被禁用的效果立刻失效，避免已经开好的信标继续发放被 Op 禁掉的效果。
            if (primary != null && !SogBeaconFilter.mayPick(primary)) {
                holder.sog$setPrimaryEffect(null);
                changed = true;
                primary = null;
            }
            if (secondary != null && !SogBeaconFilter.mayPick(secondary)) {
                holder.sog$setSecondaryEffect(null);
                changed = true;
            }
            if (primary == null && holder.sog$getSecondaryEffect() != null) {
                // 没有主效果时副效果不会生效，留着只会让界面显示一个永远不起作用的图标。
                holder.sog$setSecondaryEffect(null);
                changed = true;
            }
        } else {
            boolean primaryValid = primary != null && SogBeaconHelper.isVanillaPrimary(primary);
            boolean secondaryValid = secondary != null && SogBeaconHelper.isVanillaSecondary(secondary);
            if (primary != null && !primaryValid) {
                holder.sog$setPrimaryEffect(null);
                changed = true;
            }
            // 副效果离不开有效主效果：主效果为空或非法时，副效果即使本身是原版再生也一起清掉，
            // 否则会留下一个原版界面做不出来、tick 也永远不会施加的“只有副效果”的信标。
            if (secondary != null && (!secondaryValid || !primaryValid)) {
                holder.sog$setSecondaryEffect(null);
                changed = true;
            }
            if (holder.sog$isNetherStarPaid()) {
                holder.sog$setNetherStarPaid(false);
                changed = true;
            }
        }
        if (changed) {
            holder.sog$markChanged();
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void sog$saveNetherStar(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (this.sog$netherStarPaid) {
            tag.putBoolean(TAG_NETHER_STAR, true);
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void sog$loadNetherStar(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        this.sog$netherStarPaid = tag.getBoolean(TAG_NETHER_STAR);
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

    @Override
    public boolean sog$isNetherStarPaid() {
        return this.sog$netherStarPaid;
    }

    @Override
    public void sog$setNetherStarPaid(boolean value) {
        this.sog$netherStarPaid = value;
    }
}
