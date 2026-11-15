package cn.blockforge.generated.sogcarpet.client;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

/**
 * 由 {@code BeaconScreenMixin} 注入到原版 {@code BeaconScreen} 上，让「效果选择界面」能把玩家
 * 挑好的效果写回信标界面。
 *
 * <p>原版 {@code BeaconScreen} 的待生效效果就是它自己的两个私有字段 {@code primary} /
 * {@code secondary}；点确认时原版会把这两个字段原样塞进 {@code ServerboundSetBeaconPacket}。
 * 因此只要把选择结果写进这两个字段，再回到信标界面点确认，就和“点了原版效果按钮再确认”
 * 走的是同一条服务端路径，不需要额外的网络包。</p>
 */
public interface SogBeaconScreenAccess {
    /** 界面当前待生效的主效果（未选时为 null）。 */
    Holder<MobEffect> sog$getPendingPrimary();

    /** 界面当前待生效的副效果（未选时为 null）。 */
    Holder<MobEffect> sog$getPendingSecondary();

    /** 写入待生效效果；{@code primarySlot} 为 true 时写主效果，否则写副效果。 */
    void sog$setPendingEffect(Holder<MobEffect> effect, boolean primarySlot);
}
