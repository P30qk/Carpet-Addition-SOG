package cn.blockforge.generated.sogcarpet;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/**
 * 「自定义信标效果」的规则感知效果表与校验。
 *
 * <p>原版信标能做主效果的只有速度/ haste /抗性/跳跃/力量，副效果只有再生，而且层级与效果绑定
 * （{@code BeaconBlockEntity.BEACON_EFFECTS} 的分段 + {@code getRequiredLevelsFor}）。本规则
 * 不扩展原版那套表，而是把“能不能用”整个换掉：</p>
 * <ul>
 *   <li>规则开启时，<b>任何已注册的效果</b>都可以被信标选中（主效果要求信标 ≥1 层，副效果仍按
 *       原版要求 4 层）；等级、持续时间、刷新节奏继续用原版的公式（{@code (9 + 层数*2) * 20}
 *       tick，4 层且主副同一效果时原版会给到 II 级）；</li>
 *   <li>规则关闭时一切照旧：{@link #validate} 返回 {@code null} 表示“交给原版逻辑”，
 *       {@code VALID_EFFECTS} 也只保留原版那几种，读档时自定义效果会被过滤掉。</li>
 * </ul>
 *
 * <p>{@link #validEffects()} 直接写回 {@code BeaconBlockEntity.VALID_EFFECTS}，它是
 * {@code filterEffect}（读档白名单）唯一的使用者：规则开启时对任何非空效果都返回 true，
 * 于是存进 NBT 的自定义效果不会在读档时被丢掉。</p>
 */
public final class SogBeaconHelper {
    /** 原版效果表（与 26.2 的 {@code BEACON_EFFECTS} 完全一致）。 */
    private static final List<List<Holder<MobEffect>>> VANILLA = List.of(
            List.of(MobEffects.SPEED, MobEffects.HASTE),
            List.of(MobEffects.RESISTANCE, MobEffects.JUMP_BOOST),
            List.of(MobEffects.STRENGTH),
            List.of(MobEffects.REGENERATION));

    /**
     * 原版允许的<b>主</b>效果：{@code BEACON_EFFECTS} 前三段。
     *
     * <p>注意不能把第四段的再生算进来：原版只有满级信标的<b>副</b>效果能选再生，
     * {@code getRequiredLevelsFor(再生)} 返回 4，所以再生作主效果在原版校验里会被拒绝。
     * 规则关闭时如果把它当“原版主效果”留下，就会得到一个原版根本开不出来的信标。</p>
     */
    private static final Set<Holder<MobEffect>> VANILLA_PRIMARY = VANILLA.subList(0, 3).stream()
            .flatMap(List::stream).collect(Collectors.toUnmodifiableSet());

    /** 原版允许的全部效果（用于判断“这个效果是不是原版就有的”）。 */
    private static final Set<Holder<MobEffect>> VANILLA_ALL =
            VANILLA.stream().flatMap(List::stream).collect(Collectors.toUnmodifiableSet());

    /** 规则开启时对任何非空效果都放行的只读集合；规则关闭时退回原版白名单。 */
    private static final Set<Holder<MobEffect>> RULE_AWARE_VALID = new AbstractSet<>() {
        @Override
        public boolean contains(Object object) {
            if (object == null) {
                return false;
            }
            return SogSettings.customBeaconEffects || VANILLA_ALL.contains(object);
        }

        @Override
        public Iterator<Holder<MobEffect>> iterator() {
            return VANILLA_ALL.iterator();
        }

        @Override
        public int size() {
            return VANILLA_ALL.size();
        }
    };

    private SogBeaconHelper() {
    }

    /** 写回 {@code BeaconBlockEntity.VALID_EFFECTS} 的读档白名单。 */
    public static Set<Holder<MobEffect>> validEffects() {
        return RULE_AWARE_VALID;
    }

    /** 这个效果能不能作原版的主效果（用于规则关闭时判断是否要清掉）。 */
    public static boolean isVanillaPrimary(Holder<MobEffect> effect) {
        return effect != null && VANILLA_PRIMARY.contains(effect);
    }

    /** 这个效果能不能作原版的副效果。
     *
     * <p>原版满级信标的副效果只有再生；速度、力量这些当副效果是只有本规则才做得出来的配置，
     * 所以规则关闭后也要清掉，否则会留下一个原版根本开不出来的信标。</p>
     */
    public static boolean isVanillaSecondary(Holder<MobEffect> effect) {
        return effect != null && VANILLA.get(3).contains(effect);
    }

    /** 这个效果是不是原版信标界面本来就能给的效果（主效果六选三 + 副效果再生）。 */
    public static boolean isVanillaBeaconEffect(Holder<MobEffect> effect) {
        return effect != null && VANILLA_ALL.contains(effect);
    }

    /** 原版信标可用的全部效果 id（给「切到白名单时先填一份可用名单」用）。 */
    public static java.util.List<String> vanillaBeaconEffectIds() {
        return VANILLA_ALL.stream()
                .map(SogBeaconFilter::idOf)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 规则开启时的信标效果校验；返回 {@code null} 表示“不接管，交给原版”。
     *
     * <p>语义与原版的 {@code validateEffects} 对等，只是把“效果必须出现在原版效果表的第几段”
     * 换成了“主效果任意、副效果需要 4 层”：</p>
     * <ul>
     *   <li>必须有主效果：原版 {@code applyEffects} 在主效果为空时直接返回，原版确认按钮也只在
     *       选了主效果时才可点，所以“只有副效果”的配置一律拒绝；</li>
     *   <li>信标至少要有 1 层，否则任何效果都不会被 {@code applyEffects} 施加；</li>
     *   <li>副效果存在时信标必须满 4 层；</li>
     *   <li>主/副效果都必须通过黑/白名单过滤（{@link SogBeaconFilter#mayPick}），Op 禁用的效果
     *       即使客户端界面被改过也开不出来；</li>
     *   <li>其余任意组合都允许。</li>
     * </ul>
     */
    public static Boolean validate(Holder<MobEffect> primary, Holder<MobEffect> secondary,
            int levels) {
        if (!SogSettings.customBeaconEffects) {
            return null;
        }
        if (primary == null) {
            return false;
        }
        if (levels < 1) {
            return false;
        }
        if (secondary != null && levels < 4) {
            return false;
        }
        if (!SogBeaconFilter.mayPick(primary)) {
            return false;
        }
        return secondary == null || SogBeaconFilter.mayPick(secondary);
    }
}
