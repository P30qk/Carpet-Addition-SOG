package cn.blockforge.generated.sogcarpet;

import java.util.AbstractList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/**
 * 信标附加效果的效果表。
 *
 * <p>原版 {@code BeaconBlockEntity.BEACON_EFFECTS} 是一个 4 段列表：前三段分别对应
 * 信标 1~3 层可做主效果（层数不足时按钮灰掉），第 4 段是 4 层才能选的辅助效果。
 * 本模组把饱和、幸运插进第 3 段（与力量同排），把抗火追加到第 4 段（与再生同排），
 * 位置正好对应参考图里的方框一、方框二、方框三。</p>
 *
 * <p>对外暴露的是 {@link #beaconEffects()}：一个“看规则取值”的只读视图。规则开启时
 * 返回带附加效果的完整表，关闭时逐值退回原版表。这样同一个字段同时驱动：</p>
 * <ul>
 *   <li>客户端信标界面——规则关闭时按钮不出现；</li>
 *   <li>服务端 {@code getRequiredLevelsFor}/{@code validateEffects}——规则关闭时
 *       附加效果找不到归属层级，选不中，等于原版。</li>
 * </ul>
 *
 * <p>{@link #allValidEffects()} 则始终包含全部效果，仅用于 {@code loadEffect} 的反序列化
 * 白名单，避免规则关闭期间把存档里已有的附加效果直接读丢（改由 tick 主动清空）。</p>
 */
public final class SogBeaconEffects {
    private static final List<Holder<MobEffect>> TIER_1 = List.of(MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED);
    private static final List<Holder<MobEffect>> TIER_2 =
            List.of(MobEffects.DAMAGE_RESISTANCE, MobEffects.JUMP);
    private static final List<Holder<MobEffect>> TIER_3 =
            List.of(MobEffects.SATURATION, MobEffects.DAMAGE_BOOST, MobEffects.LUCK);
    private static final List<Holder<MobEffect>> SECONDARY =
            List.of(MobEffects.REGENERATION, MobEffects.FIRE_RESISTANCE);

    /** 规则开启时的完整效果表。 */
    private static final List<List<Holder<MobEffect>>> FULL =
            List.of(TIER_1, TIER_2, TIER_3, SECONDARY);

    /** 规则关闭时的原版效果表（与 vanilla 的 BEACON_EFFECTS 完全一致）。 */
    private static final List<List<Holder<MobEffect>>> VANILLA = List.of(
            List.of(MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED),
            List.of(MobEffects.DAMAGE_RESISTANCE, MobEffects.JUMP),
            List.of(MobEffects.DAMAGE_BOOST),
            List.of(MobEffects.REGENERATION));

    /** 本模组追加进去的三个效果，用于判断某个信标是否用到了附加效果。 */
    private static final Set<Holder<MobEffect>> EXTRA =
            Set.of(MobEffects.SATURATION, MobEffects.LUCK, MobEffects.FIRE_RESISTANCE);

    private static final Set<Holder<MobEffect>> ALL_VALID =
            FULL.stream().flatMap(List::stream).collect(Collectors.toUnmodifiableSet());

    private static final List<List<Holder<MobEffect>>> RULE_AWARE = new AbstractList<>() {
        @Override
        public List<Holder<MobEffect>> get(int index) {
            return SogSettings.beaconExtraEffects ? FULL.get(index) : VANILLA.get(index);
        }

        @Override
        public int size() {
            return FULL.size();
        }
    };

    private SogBeaconEffects() {
    }

    /** 规则感知的信标效果表，直接写回 {@code BeaconBlockEntity.BEACON_EFFECTS}。 */
    public static List<List<Holder<MobEffect>>> beaconEffects() {
        return RULE_AWARE;
    }

    /** 反序列化白名单：始终包含附加效果，防止规则关闭期间读档丢效果。 */
    public static Set<Holder<MobEffect>> allValidEffects() {
        return ALL_VALID;
    }

    /** 是否是本模组追加的效果（饱和 / 幸运 / 抗火）。 */
    public static boolean isExtra(Holder<MobEffect> effect) {
        return effect != null && EXTRA.contains(effect);
    }
}
